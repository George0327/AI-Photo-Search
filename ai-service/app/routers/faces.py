import asyncio
import logging
from typing import List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

import app.state as state
from app.schemas import FaceIndexResponse, FaceSearchResponse
from app.state import (
    ALBUM_INDEX_ROOT,
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


@router.post("/index/albums/{album_id}/faces", response_model=FaceIndexResponse)
async def index_album_faces(
    album_id: str,
    file: UploadFile = File(...),
    image_id: str = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    image_bytes = await file.read()

    # 1. Quick existence check
    async with _get_lock(_face_locks, album_id):
        if get_cached_face_store(album_id).is_image_indexed(image_id):
            return {"success": True, "face_count": 0, "message": f"Image {image_id} already face-indexed"}

    # 2. Run face detection outside the lock
    loop = asyncio.get_running_loop()
    try:
        faces = await loop.run_in_executor(ml_executor, state.face_model.extract_faces, image_bytes)
    except Exception as exc:
        return {"success": False, "face_count": 0, "message": f"Face detection failed: {exc}"}

    # 3. Write to FAISS (double-check)
    async with _get_lock(_face_locks, album_id):
        store = get_cached_face_store(album_id)
        if not store.is_image_indexed(image_id):
            for face in faces:
                store.add(face["embedding"], image_id, face["bbox"], face["confidence"])
            store.mark_processed(image_id)
            save_face_store(album_id, store)

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

    merged: dict[str, dict] = {}

    for album_id in target_album_ids:
        # Get store reference under lock, search outside lock
        async with _get_lock(_face_locks, album_id):
            store = get_cached_face_store(album_id)
        if not store.items:
            continue
        album_results = await loop.run_in_executor(
            ml_executor, lambda s=store: s.search(query_vector, top_k=top_k)
        )
        for r in album_results:
            if r["score"] < threshold:
                continue
            item_id = r["image_id"]
            if item_id not in merged or r["score"] > merged[item_id]["score"]:
                merged[item_id] = {
                    "item_id": item_id,
                    "album_id": album_id,
                    "score": r["score"],
                    "matched_bbox": r["bbox"],
                }

    results = sorted(merged.values(), key=lambda x: x["score"], reverse=True)[:top_k]
    return {"success": True, "message": f"Found {len(results)} matching photo(s)", "results": results}
