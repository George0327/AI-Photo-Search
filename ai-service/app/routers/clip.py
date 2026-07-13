import asyncio
from typing import List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

import app.state as state
from app.faiss_store import FaissStore
from app.schemas import BatchIndexResponse, SearchRequest, SearchResponse
from app.state import (
    _album_locks,
    _get_lock,
    get_cached_store,
    save_album_store,
    list_indexed_album_ids,
    album_index_path,
    album_items_path,
    ml_executor,
    verify_internal_api_key,
)

router = APIRouter()


@router.post("/index/albums/{album_id}/reset")
async def reset_album_index(
    album_id: str,
    _: None = Depends(verify_internal_api_key),
):
    loop = asyncio.get_running_loop()

    async with _get_lock(_album_locks, album_id):
        store = FaissStore(dimension=state._embedding_dim)
        state._album_stores[album_id] = store
        await loop.run_in_executor(ml_executor, save_album_store, album_id, store)

    return {"success": True, "message": f"Album index reset successfully: {album_id}"}


@router.post("/index/albums/{album_id}/batch", response_model=BatchIndexResponse)
async def index_album_batch(
    album_id: str,
    files: List[UploadFile] = File(...),
    image_ids: List[str] = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    """index a batch of images with one CLIP forward pass and one index save"""
    if len(files) != len(image_ids):
        raise HTTPException(status_code=422, detail="files and image_ids must have the same length")

    contents = [await f.read() for f in files]
    loop = asyncio.get_running_loop()
    results: dict[str, dict] = {}

    async with _get_lock(_album_locks, album_id):
        store = await loop.run_in_executor(ml_executor, get_cached_store, album_id)

    pending = []

    for image_id, image_bytes in zip(image_ids, contents):
        if store.contains(image_id):
            results[image_id] = {"image_id": image_id, "success": True,
                                 "message": "Already indexed"}
        else:
            pending.append((image_id, image_bytes))

    if pending:
        embeddings, errors = await loop.run_in_executor(
            ml_executor, state.model.images_to_embeddings, [b for _, b in pending]
        )

        async with _get_lock(_album_locks, album_id):
            store = await loop.run_in_executor(ml_executor, get_cached_store, album_id)
            added = False

            for (image_id, _), vector, error in zip(pending, embeddings, errors):
                if vector is None:
                    results[image_id] = {"image_id": image_id, "success": False, "message": error}
                elif store.contains(image_id):
                    results[image_id] = {"image_id": image_id, "success": True,
                                         "message": "Already indexed"}
                else:
                    store.add(vector, image_id)
                    added = True
                    results[image_id] = {"image_id": image_id, "success": True,
                                         "message": "Indexed"}

            if added:
                await loop.run_in_executor(ml_executor, save_album_store, album_id, store)

    ordered = [results[image_id] for image_id in image_ids]
    failed = sum(1 for r in ordered if not r["success"])

    return {"success": failed == 0,
            "message": f"Indexed batch for album {album_id}: {len(ordered) - failed} ok, {failed} failed",
            "results": ordered}


@router.post("/search", response_model=SearchResponse)
async def search(
    request: SearchRequest,
    _: None = Depends(verify_internal_api_key),
):
    album_ids = (
        [a for a in request.album_ids if album_index_path(a).exists() and album_items_path(a).exists()]
        if request.album_ids
        else list_indexed_album_ids()
    )

    if not album_ids:
        return {"success": False, "message": "No matching album indexes found. Run indexing first.",
                "query": request.query, "top_k": request.top_k, "results": []}

    loop = asyncio.get_running_loop()
    query_vector = await loop.run_in_executor(
        ml_executor, state.model.text_to_embedding, request.query
    )

    # "search within results": restrict matches to a prior result set
    restrict = set(request.restrict_ids) if request.restrict_ids else None

    async def search_album(album_id: str) -> list[dict]:
        async with _get_lock(_album_locks, album_id):
            store = await loop.run_in_executor(ml_executor, get_cached_store, album_id)

        if not store.items:
            return []

        # when restricting, rank the whole album then filter
        k = len(store.items) if restrict is not None else request.top_k

        album_results = await loop.run_in_executor(
            ml_executor, lambda s=store: s.search(query_vector, top_k=k)
        )

        return [{"item_id": r["item_id"], "album_id": album_id, "score": r["score"]}
                for r in album_results
                if restrict is None or r["item_id"] in restrict]

    per_album_results = await asyncio.gather(*(search_album(a) for a in album_ids))
    merged_results = [r for album_results in per_album_results for r in album_results]
    merged_results.sort(key=lambda r: r["score"], reverse=True)

    return {"success": True, "message": "Search completed", "query": request.query,
            "top_k": request.top_k, "results": merged_results[:request.top_k]}
