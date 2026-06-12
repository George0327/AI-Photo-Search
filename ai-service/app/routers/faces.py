import asyncio
import logging
from typing import List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

import app.state as state
from app.detection_faiss_store import DetectionFaissStore
from app.schemas import FaceBatchIndexResponse, FaceIndexResponse, FaceSearchResponse
from app.state import (
    ALBUM_INDEX_ROOT,
    FACE_INDEX_DIM,
    _face_locks,
    _get_lock,
    average_embeddings,
    face_index_path,
    face_items_path,
    get_cached_face_store,
    ml_executor,
    save_face_store,
    verify_internal_api_key,
)

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/index/albums/{album_id}/faces/reset")
async def reset_album_face_index(
    album_id: str,
    _: None = Depends(verify_internal_api_key),
):
    loop = asyncio.get_running_loop()
    async with _get_lock(_face_locks, album_id):
        store = DetectionFaissStore(dimension=FACE_INDEX_DIM)
        state._face_stores[album_id] = store
        await loop.run_in_executor(ml_executor, save_face_store, album_id, store)
    return {"success": True, "message": f"Album face index reset successfully: {album_id}"}


@router.post("/index/albums/{album_id}/faces/batch", response_model=FaceBatchIndexResponse)
async def index_album_faces_batch(
    album_id: str,
    files: List[UploadFile] = File(...),
    image_ids: List[str] = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    """Face-index a batch of images with one index save at the end."""
    if len(files) != len(image_ids):
        raise HTTPException(status_code=422, detail="files and image_ids must have the same length")

    contents = [await f.read() for f in files]
    loop = asyncio.get_running_loop()
    results: dict[str, dict] = {}

    # 1. Quick existence check (store load happens off the event loop)
    async with _get_lock(_face_locks, album_id):
        store = await loop.run_in_executor(ml_executor, get_cached_face_store, album_id)
    pending = []
    for image_id, image_bytes in zip(image_ids, contents):
        if store.is_image_indexed(image_id):
            results[image_id] = {"image_id": image_id, "success": True, "face_count": 0,
                                 "message": "Already face-indexed"}
        else:
            pending.append((image_id, image_bytes))

    # 2. Detect faces outside the lock — one executor task per image so the
    #    thread pool works the batch in parallel.
    async def detect(image_bytes: bytes):
        try:
            return await loop.run_in_executor(ml_executor, state.face_model.extract_faces, image_bytes)
        except Exception as exc:
            return exc

    detections = await asyncio.gather(*(detect(b) for _, b in pending))

    # 3. Write all detections, then save the index once
    if pending:
        async with _get_lock(_face_locks, album_id):
            store = await loop.run_in_executor(ml_executor, get_cached_face_store, album_id)
            added = False
            for (image_id, _), faces in zip(pending, detections):
                if isinstance(faces, Exception):
                    results[image_id] = {"image_id": image_id, "success": False, "face_count": 0,
                                         "message": f"Face detection failed: {faces}"}
                    continue
                if not store.is_image_indexed(image_id):
                    for face in faces:
                        store.add(face["embedding"], image_id, face["bbox"], face["confidence"])
                    store.mark_processed(image_id)
                    added = True
                results[image_id] = {"image_id": image_id, "success": True, "face_count": len(faces),
                                     "message": f"Indexed {len(faces)} face(s)"}
            if added:
                await loop.run_in_executor(ml_executor, save_face_store, album_id, store)

    ordered = [results[image_id] for image_id in image_ids]
    failed = sum(1 for r in ordered if not r["success"])
    return {"success": failed == 0,
            "message": f"Face-indexed batch for album {album_id}: {len(ordered) - failed} ok, {failed} failed",
            "results": ordered}


@router.post("/index/albums/{album_id}/faces", response_model=FaceIndexResponse)
async def index_album_faces(
    album_id: str,
    file: UploadFile = File(...),
    image_id: str = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    image_bytes = await file.read()
    loop = asyncio.get_running_loop()

    # 1. Quick existence check
    async with _get_lock(_face_locks, album_id):
        store = await loop.run_in_executor(ml_executor, get_cached_face_store, album_id)
    if store.is_image_indexed(image_id):
        return {"success": True, "face_count": 0, "message": f"Image {image_id} already face-indexed"}

    # 2. Run face detection outside the lock
    try:
        faces = await loop.run_in_executor(ml_executor, state.face_model.extract_faces, image_bytes)
    except Exception as exc:
        return {"success": False, "face_count": 0, "message": f"Face detection failed: {exc}"}

    # 3. Write to FAISS (double-check)
    async with _get_lock(_face_locks, album_id):
        store = await loop.run_in_executor(ml_executor, get_cached_face_store, album_id)
        if not store.is_image_indexed(image_id):
            for face in faces:
                store.add(face["embedding"], image_id, face["bbox"], face["confidence"])
            store.mark_processed(image_id)
            await loop.run_in_executor(ml_executor, save_face_store, album_id, store)

    return {"success": True, "face_count": len(faces),
            "message": f"Indexed {len(faces)} face(s) for image {image_id}"}


@router.post("/search/faces", response_model=FaceSearchResponse)
async def search_faces(
    files: List[UploadFile] = File(...),
    album_ids: List[str] = Form(default=[]),
    top_k: int = Form(default=10),
    threshold: float = Form(default=0.35),
    _: None = Depends(verify_internal_api_key),
):
    loop = asyncio.get_running_loop()

    # Extract face embeddings from all reference images (off the event loop)
    embeddings = []
    for f in files:
        image_bytes = await f.read()
        embedding = await loop.run_in_executor(ml_executor, state.face_model.embedding_from_bytes, image_bytes)
        if embedding is not None:
            embeddings.append(embedding)

    if not embeddings:
        raise HTTPException(
            status_code=422,
            detail="No face detected in any of the reference images. Please upload a clear photo of the person.",
        )

    query_vector = average_embeddings(embeddings)

    target_album_ids = (
        [a for a in album_ids if face_index_path(a).exists() and face_items_path(a).exists()]
        if album_ids
        else [
            path.name for path in ALBUM_INDEX_ROOT.iterdir()
            if path.is_dir() and face_index_path(path.name).exists()
        ] if ALBUM_INDEX_ROOT.exists() else []
    )

    if not target_album_ids:
        return {"success": False, "message": "No face indexes found. Run face indexing first.", "results": []}

    async def search_album(album_id: str) -> list[dict]:
        # Get store reference under lock (load happens off the event loop), search outside lock
        async with _get_lock(_face_locks, album_id):
            store = await loop.run_in_executor(ml_executor, get_cached_face_store, album_id)
        if not store.items:
            return []
        album_results = await loop.run_in_executor(
            ml_executor, lambda s=store: s.search(query_vector, top_k=top_k)
        )
        return [{**r, "album_id": album_id} for r in album_results]

    per_album_results = await asyncio.gather(*(search_album(a) for a in target_album_ids))

    merged: dict[str, dict] = {}
    for album_results in per_album_results:
        for r in album_results:
            if r["score"] < threshold:
                continue
            item_id = r["image_id"]
            if item_id not in merged or r["score"] > merged[item_id]["score"]:
                merged[item_id] = {
                    "item_id": item_id,
                    "album_id": r["album_id"],
                    "score": r["score"],
                    "matched_bbox": r["bbox"],
                }

    results = sorted(merged.values(), key=lambda x: x["score"], reverse=True)[:top_k]
    return {"success": True, "message": f"Found {len(results)} matching photo(s)", "results": results}
