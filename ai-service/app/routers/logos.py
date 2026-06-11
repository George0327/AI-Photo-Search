import asyncio
import logging
from typing import List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

import app.state as state
from app.logo_region_detector import LogoRegionDetector
from app.schemas import LogoIndexResponse, LogoSearchResponse
from app.state import (
    ALBUM_INDEX_ROOT,
    _logo_locks,
    _get_lock,
    average_embeddings,
    get_cached_logo_store,
    logo_index_path,
    logo_items_path,
    ml_executor,
    save_logo_store,
    verify_internal_api_key,
)

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/index/albums/{album_id}/logos", response_model=LogoIndexResponse)
async def index_album_logos(
    album_id: str,
    file: UploadFile = File(...),
    image_id: str = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    image_bytes = await file.read()

    # 1. Quick existence check
    async with _get_lock(_logo_locks, album_id):
        if get_cached_logo_store(album_id).is_image_indexed(image_id):
            return {"success": True, "logo_region_count": 0, "message": f"Image {image_id} already logo-indexed"}

    # 2. Detect regions + embed crops outside the lock
    def _detect_and_embed(img_bytes: bytes) -> list:
        regions = state.logo_detector.detect_logo_regions(img_bytes)
        results = []
        for region in regions:
            try:
                crop_bytes = LogoRegionDetector.crop_region(img_bytes, region["bbox"])
                embedding = state.model.image_to_embedding(crop_bytes)
                results.append((embedding, region["bbox"], region["confidence"]))
            except Exception as e:
                logger.warning("Logo region embed failed for image %s: %s", image_id, e)
                continue
        return results

    loop = asyncio.get_running_loop()
    try:
        region_embeddings = await loop.run_in_executor(ml_executor, _detect_and_embed, image_bytes)
    except Exception as exc:
        return {"success": False, "logo_region_count": 0, "message": f"Logo detection failed: {exc}"}

    # 3. Write to FAISS (double-check)
    async with _get_lock(_logo_locks, album_id):
        store = get_cached_logo_store(album_id)
        if not store.is_image_indexed(image_id):
            for embedding, bbox, confidence in region_embeddings:
                store.add(embedding, image_id, bbox, confidence)
            store.mark_processed(image_id)
            save_logo_store(album_id, store)

    return {
        "success": True,
        "logo_region_count": len(region_embeddings),
        "message": f"Indexed {len(region_embeddings)} logo region(s) for image {image_id}",
    }


@router.post("/search/logos", response_model=LogoSearchResponse)
async def search_logos(
    files: List[UploadFile] = File(...),
    album_ids: List[str] = Form(default=[]),
    top_k: int = Form(default=10),
    threshold: float = Form(default=0.2),
    _: None = Depends(verify_internal_api_key),
):
    loop = asyncio.get_running_loop()

    # Detect logo regions in reference images and embed the best crop,
    # mirroring what happens at indexing time (crop → CLIP embed).
    embeddings = []
    for f in files:
        image_bytes = await f.read()
        try:
            regions = state.logo_detector.detect_logo_regions(image_bytes) if state.logo_detector else []
            if regions:
                crop_bytes = LogoRegionDetector.crop_region(image_bytes, regions[0]["bbox"])
                embedding = await loop.run_in_executor(ml_executor, state.model.image_to_embedding, crop_bytes)
            else:
                embedding = await loop.run_in_executor(ml_executor, state.model.image_to_embedding, image_bytes)
            embeddings.append(embedding.flatten())
        except Exception as e:
            logger.warning("Logo reference image processing failed: %s", e)
            continue

    if not embeddings:
        raise HTTPException(status_code=422, detail="Could not process reference logo images.")

    query_vector = average_embeddings(embeddings)

    target_album_ids = (
        [a for a in album_ids if logo_index_path(a).exists() and logo_items_path(a).exists()]
        if album_ids
        else [
            path.name for path in ALBUM_INDEX_ROOT.iterdir()
            if path.is_dir() and logo_index_path(path.name).exists()
        ] if ALBUM_INDEX_ROOT.exists() else []
    )

    if not target_album_ids:
        return {"success": False, "message": "No logo indexes found. Run logo indexing first.", "results": []}

    merged: dict[str, dict] = {}

    for album_id in target_album_ids:
        # Get store reference under lock, search outside lock
        async with _get_lock(_logo_locks, album_id):
            store = get_cached_logo_store(album_id)
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
