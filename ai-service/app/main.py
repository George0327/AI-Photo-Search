import asyncio
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import List

import numpy as np
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile

from app.clip_model import ClipModel
from app.detection_faiss_store import DetectionFaissStore
from app.face_model import FaceModel
from app.faiss_store import FaissStore
from app.logo_region_detector import LogoRegionDetector
from app.schemas import (
    FaceIndexResponse,
    FaceSearchResponse,
    HealthResponse,
    LogoIndexResponse,
    LogoSearchResponse,
    SearchRequest,
    SearchResponse,
)
from app.services.face_indexing_service import index_faces
from app.services.indexing_service import index_single_image_stream
from app.services.logo_indexing_service import index_logos

INDEX_ROOT = Path("indexes")
ALBUM_INDEX_ROOT = INDEX_ROOT / "albums"
INTERNAL_API_KEY = os.getenv("AI_SERVICE_INTERNAL_API_KEY", "dev-ai-service-key")

FACE_INDEX_DIM = 512  # ArcFace always produces 512-dim embeddings

# ── Module-level models (populated at startup) ───────────────────────────────

model: ClipModel | None = None
face_model: FaceModel | None = None
logo_detector: LogoRegionDetector | None = None
_embedding_dim: int | None = None

# ── Per-album in-memory caches and async locks ───────────────────────────────
# asyncio is single-threaded so plain dict access is safe

_album_stores: dict[str, FaissStore] = {}
_face_stores: dict[str, DetectionFaissStore] = {}
_logo_stores: dict[str, DetectionFaissStore] = {}

_album_locks: dict[str, asyncio.Lock] = {}
_face_locks: dict[str, asyncio.Lock] = {}
_logo_locks: dict[str, asyncio.Lock] = {}


def _get_lock(d: dict, album_id: str) -> asyncio.Lock:
    if album_id not in d:
        d[album_id] = asyncio.Lock()
    return d[album_id]


# ── Path helpers ─────────────────────────────────────────────────────────────

def album_dir(album_id: str) -> Path:
    return ALBUM_INDEX_ROOT / album_id

def album_index_path(album_id: str) -> Path:
    return album_dir(album_id) / "images.index"

def album_items_path(album_id: str) -> Path:
    return album_dir(album_id) / "images.json"

def face_index_path(album_id: str) -> Path:
    return album_dir(album_id) / "faces.index"

def face_items_path(album_id: str) -> Path:
    return album_dir(album_id) / "faces.json"

def logo_index_path(album_id: str) -> Path:
    return album_dir(album_id) / "logos.index"

def logo_items_path(album_id: str) -> Path:
    return album_dir(album_id) / "logos.json"


# ── Store loaders ─────────────────────────────────────────────────────────────

def _load_album_store(album_id: str) -> FaissStore:
    if album_index_path(album_id).exists() and album_items_path(album_id).exists():
        return FaissStore.load(album_index_path(album_id), album_items_path(album_id))
    return FaissStore(dimension=_embedding_dim)

def _load_face_store(album_id: str) -> DetectionFaissStore:
    if face_index_path(album_id).exists() and face_items_path(album_id).exists():
        return DetectionFaissStore.load(face_index_path(album_id), face_items_path(album_id))
    return DetectionFaissStore(dimension=FACE_INDEX_DIM)

def _load_logo_store(album_id: str) -> DetectionFaissStore:
    if logo_index_path(album_id).exists() and logo_items_path(album_id).exists():
        return DetectionFaissStore.load(logo_index_path(album_id), logo_items_path(album_id))
    return DetectionFaissStore(dimension=_embedding_dim)

def _get_cached_store(album_id: str) -> FaissStore:
    if album_id not in _album_stores:
        _album_stores[album_id] = _load_album_store(album_id)
    return _album_stores[album_id]

def _get_cached_face_store(album_id: str) -> DetectionFaissStore:
    if album_id not in _face_stores:
        _face_stores[album_id] = _load_face_store(album_id)
    return _face_stores[album_id]

def _get_cached_logo_store(album_id: str) -> DetectionFaissStore:
    if album_id not in _logo_stores:
        _logo_stores[album_id] = _load_logo_store(album_id)
    return _logo_stores[album_id]


# ── Store savers ─────────────────────────────────────────────────────────────

def _save_album_store(album_id: str, store: FaissStore) -> None:
    album_dir(album_id).mkdir(parents=True, exist_ok=True)
    store.save(album_index_path(album_id), album_items_path(album_id))

def _save_face_store(album_id: str, store: DetectionFaissStore) -> None:
    album_dir(album_id).mkdir(parents=True, exist_ok=True)
    store.save(face_index_path(album_id), face_items_path(album_id))

def _save_logo_store(album_id: str, store: DetectionFaissStore) -> None:
    album_dir(album_id).mkdir(parents=True, exist_ok=True)
    store.save(logo_index_path(album_id), logo_items_path(album_id))


# ── Other helpers ─────────────────────────────────────────────────────────────

def _list_indexed_album_ids() -> list[str]:
    if not ALBUM_INDEX_ROOT.exists():
        return []
    return [
        path.name
        for path in ALBUM_INDEX_ROOT.iterdir()
        if path.is_dir()
        and album_index_path(path.name).exists()
        and album_items_path(path.name).exists()
    ]

def _average_embeddings(embeddings: list[np.ndarray]) -> np.ndarray:
    """Average a list of embeddings and L2-normalize the result."""
    avg = np.mean(np.stack(embeddings, axis=0), axis=0)
    norm = np.linalg.norm(avg)
    if norm > 0:
        avg = avg / norm
    return avg.astype("float32")


# ── Auth dependency ───────────────────────────────────────────────────────────

def verify_internal_api_key(x_internal_api_key: str | None = Header(default=None)):
    if x_internal_api_key != INTERNAL_API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized")


# ── Startup ───────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, face_model, logo_detector, _embedding_dim

    print("Loading CLIP model...")
    model = ClipModel()
    _embedding_dim = int(model.text_to_embedding("test").shape[1])

    print("Loading face recognition model (InsightFace buffalo_l)...")
    face_model = FaceModel()

    print("Loading logo region detector (OWL-ViT v2)...")
    logo_detector = LogoRegionDetector()

    ALBUM_INDEX_ROOT.mkdir(parents=True, exist_ok=True)
    print(
        f"AI service ready — CLIP: {model.model_name}/{model.pretrained} "
        f"(dim={_embedding_dim}), face: InsightFace buffalo_l, logo: OWL-ViT v2"
    )
    yield


app = FastAPI(
    title="AI Photo Search Service",
    version="2.0.0",
    lifespan=lifespan,
)


# ── Health ────────────────────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse)
def health_check():
    return {"status": "ok"}


# ── CLIP indexing ─────────────────────────────────────────────────────────────

@app.post("/index/albums/{album_id}/reset")
async def reset_album_index(
    album_id: str,
    _: None = Depends(verify_internal_api_key),
):
    async with _get_lock(_album_locks, album_id):
        store = FaissStore(dimension=_embedding_dim)
        _album_stores[album_id] = store
        _save_album_store(album_id, store)
    return {"success": True, "message": f"Album index reset successfully: {album_id}"}


@app.post("/index/albums/{album_id}/stream")
async def index_album_stream(
    album_id: str,
    file: UploadFile = File(...),
    image_id: str = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    image_bytes = await file.read()

    async with _get_lock(_album_locks, album_id):
        store = _get_cached_store(album_id)
        if store.contains(image_id):
            return {"success": True, "message": f"Image {image_id} already indexed in album {album_id}"}

        result = index_single_image_stream(image_bytes=image_bytes, image_id=image_id, model=model, store=store)
        if not result["success"]:
            raise HTTPException(status_code=400, detail=result["message"])
        _save_album_store(album_id, store)

    return result


# ── Face indexing ─────────────────────────────────────────────────────────────

@app.post("/index/albums/{album_id}/faces", response_model=FaceIndexResponse)
async def index_album_faces(
    album_id: str,
    file: UploadFile = File(...),
    image_id: str = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    image_bytes = await file.read()

    async with _get_lock(_face_locks, album_id):
        store = _get_cached_face_store(album_id)
        result = index_faces(image_bytes=image_bytes, image_id=image_id, face_model=face_model, store=store)
        _save_face_store(album_id, store)

    return result


# ── Logo indexing ─────────────────────────────────────────────────────────────

@app.post("/index/albums/{album_id}/logos", response_model=LogoIndexResponse)
async def index_album_logos(
    album_id: str,
    file: UploadFile = File(...),
    image_id: str = Form(...),
    _: None = Depends(verify_internal_api_key),
):
    image_bytes = await file.read()

    async with _get_lock(_logo_locks, album_id):
        store = _get_cached_logo_store(album_id)
        result = index_logos(
            image_bytes=image_bytes,
            image_id=image_id,
            logo_detector=logo_detector,
            clip_model=model,
            store=store,
        )
        _save_logo_store(album_id, store)

    return result


# ── CLIP semantic search ──────────────────────────────────────────────────────

@app.post("/search", response_model=SearchResponse)
async def search(
    request: SearchRequest,
    _: None = Depends(verify_internal_api_key),
):
    album_ids = (
        [a for a in request.album_ids if album_index_path(a).exists() and album_items_path(a).exists()]
        if request.album_ids
        else _list_indexed_album_ids()
    )

    if not album_ids:
        return {"success": False, "message": "No matching album indexes found. Run indexing first.",
                "query": request.query, "top_k": request.top_k, "results": []}

    query_vector = model.text_to_embedding(request.query)
    merged_results = []

    for album_id in album_ids:
        async with _get_lock(_album_locks, album_id):
            store = _get_cached_store(album_id)
            if not store.items:
                continue
            album_results = store.search(query_vector, top_k=request.top_k)

        for r in album_results:
            merged_results.append({"item_id": r["item_id"], "album_id": album_id, "score": r["score"]})

    merged_results.sort(key=lambda r: r["score"], reverse=True)
    return {"success": True, "message": "Search completed", "query": request.query,
            "top_k": request.top_k, "results": merged_results[:request.top_k]}


# ── Face search ───────────────────────────────────────────────────────────────

@app.post("/search/faces", response_model=FaceSearchResponse)
async def search_faces(
    files: List[UploadFile] = File(...),
    album_ids: List[str] = Form(default=[]),
    top_k: int = Form(default=10),
    threshold: float = Form(default=0.35),
    _: None = Depends(verify_internal_api_key),
):
    # Extract face embeddings from all reference images
    embeddings = []
    for f in files:
        image_bytes = await f.read()
        embedding = face_model.embedding_from_bytes(image_bytes)
        if embedding is not None:
            embeddings.append(embedding)

    if not embeddings:
        raise HTTPException(
            status_code=422,
            detail="No face detected in any of the reference images. Please upload a clear photo of the person.",
        )

    query_vector = _average_embeddings(embeddings)

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
        async with _get_lock(_face_locks, album_id):
            store = _get_cached_face_store(album_id)
            if not store.items:
                continue
            album_results = store.search(query_vector, top_k=top_k)

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


# ── Logo search ───────────────────────────────────────────────────────────────

@app.post("/search/logos", response_model=LogoSearchResponse)
async def search_logos(
    files: List[UploadFile] = File(...),
    album_ids: List[str] = Form(default=[]),
    top_k: int = Form(default=10),
    threshold: float = Form(default=0.2),
    _: None = Depends(verify_internal_api_key),
):
    # Embed each reference logo image with CLIP (whole image, no region detection needed)
    embeddings = []
    for f in files:
        image_bytes = await f.read()
        try:
            embedding = model.image_to_embedding(image_bytes)
            embeddings.append(embedding.flatten())
        except Exception:
            continue

    if not embeddings:
        raise HTTPException(status_code=422, detail="Could not process reference logo images.")

    query_vector = _average_embeddings(embeddings)

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
        async with _get_lock(_logo_locks, album_id):
            store = _get_cached_logo_store(album_id)
            if not store.items:
                continue
            album_results = store.search(query_vector, top_k=top_k)

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
