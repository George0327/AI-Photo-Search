import asyncio

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

import app.state as state
from app.faiss_store import FaissStore
from app.schemas import SearchRequest, SearchResponse
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
    async with _get_lock(_album_locks, album_id):
        store = FaissStore(dimension=state._embedding_dim)
        state._album_stores[album_id] = store
        save_album_store(album_id, store)
    return {"success": True, "message": f"Album index reset successfully: {album_id}"}


@router.post("/index/albums/{album_id}/stream")
async def index_album_stream(
    album_id: str,
    file: UploadFile = File(...),
    image_id: str = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    image_bytes = await file.read()

    # 1. Quick existence check
    async with _get_lock(_album_locks, album_id):
        if get_cached_store(album_id).contains(image_id):
            return {"success": True, "message": f"Image {image_id} already indexed in album {album_id}"}

    # 2. Run CLIP inference outside the lock (concurrent across parallel requests)
    loop = asyncio.get_running_loop()
    try:
        vector = await loop.run_in_executor(ml_executor, state.model.image_to_embedding, image_bytes)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Failed to process image: {exc}")

    # 3. Write to FAISS (double-check in case another request raced us)
    async with _get_lock(_album_locks, album_id):
        store = get_cached_store(album_id)
        if not store.contains(image_id):
            store.add(vector, image_id)
            save_album_store(album_id, store)

    return {"success": True, "message": f"Successfully indexed image {image_id}"}


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

    query_vector = state.model.text_to_embedding(request.query)
    loop = asyncio.get_running_loop()
    merged_results = []

    for album_id in album_ids:
        # Get store reference under lock (fast), then search outside lock
        async with _get_lock(_album_locks, album_id):
            store = get_cached_store(album_id)
        if not store.items:
            continue
        album_results = await loop.run_in_executor(
            ml_executor, lambda s=store: s.search(query_vector, top_k=request.top_k)
        )
        for r in album_results:
            merged_results.append({"item_id": r["item_id"], "album_id": album_id, "score": r["score"]})

    merged_results.sort(key=lambda r: r["score"], reverse=True)
    return {"success": True, "message": "Search completed", "query": request.query,
            "top_k": request.top_k, "results": merged_results[:request.top_k]}
