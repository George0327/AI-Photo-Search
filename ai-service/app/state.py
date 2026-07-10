import asyncio
import logging
import os
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
from fastapi import Header, HTTPException

from app import config
from app.clip_model import ClipModel
from app.detection_faiss_store import DetectionFaissStore
from app.face_model import FaceModel
from app.faiss_store import FaissStore
logger = logging.getLogger(__name__)

# ── Constants ─────────────────────────────────────────────────────────────────

INDEX_ROOT = Path("indexes")
ALBUM_INDEX_ROOT = INDEX_ROOT / "albums"
INTERNAL_API_KEY = os.getenv("AI_SERVICE_INTERNAL_API_KEY", "dev-ai-service-key")
FACE_INDEX_DIM = 512  # ArcFace always produces 512-dim embeddings

# ── Module-level models (populated at startup) ───────────────────────────────

model: ClipModel | None = None
face_model: FaceModel | None = None
_embedding_dim: int | None = None

# ── Per-album in-memory caches and async locks ───────────────────────────────
# Plain dicts are safe: each album's entry is serialized by its asyncio lock and CPython dict assignment is atomic.

_album_stores: dict[str, FaissStore] = {}
_face_stores: dict[str, DetectionFaissStore] = {}

_album_locks: dict[str, asyncio.Lock] = {}
_face_locks: dict[str, asyncio.Lock] = {}

# CPU-bound pool (image decode, FAISS I/O, CLIP), outside the per-album lock. PyTorch releases the GIL during C++ ops, so workers run in parallel. 0 = os.cpu_count().
ml_executor = ThreadPoolExecutor(max_workers=config.ML_EXECUTOR_WORKERS or (os.cpu_count() or 4))

# InsightFace GPU pool, one worker by default: the GPU runs inferences serially and the onnxruntime session isn't concurrency-safe. Tune via config.FACE_INFERENCE_CONCURRENCY.
face_executor = ThreadPoolExecutor(max_workers=max(1, config.FACE_INFERENCE_CONCURRENCY))


def _get_lock(d: dict, album_id: str) -> asyncio.Lock:
    if album_id not in d:
        d[album_id] = asyncio.Lock()
    return d[album_id]


# ── Path helpers ──────────────────────────────────────────────────────────────

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


# ── Store loaders / getters / savers ─────────────────────────────────────────

def _load_album_store(album_id: str) -> FaissStore:
    if album_index_path(album_id).exists() and album_items_path(album_id).exists():
        return FaissStore.load(album_index_path(album_id), album_items_path(album_id))
    return FaissStore(dimension=_embedding_dim)

def _load_face_store(album_id: str) -> DetectionFaissStore:
    if face_index_path(album_id).exists() and face_items_path(album_id).exists():
        return DetectionFaissStore.load(face_index_path(album_id), face_items_path(album_id))
    return DetectionFaissStore(dimension=FACE_INDEX_DIM)

def get_cached_store(album_id: str) -> FaissStore:
    if album_id not in _album_stores:
        _album_stores[album_id] = _load_album_store(album_id)
    return _album_stores[album_id]

def get_cached_face_store(album_id: str) -> DetectionFaissStore:
    if album_id not in _face_stores:
        _face_stores[album_id] = _load_face_store(album_id)
    return _face_stores[album_id]

def save_album_store(album_id: str, store: FaissStore) -> None:
    album_dir(album_id).mkdir(parents=True, exist_ok=True)
    store.save(album_index_path(album_id), album_items_path(album_id))

def save_face_store(album_id: str, store: DetectionFaissStore) -> None:
    album_dir(album_id).mkdir(parents=True, exist_ok=True)
    store.save(face_index_path(album_id), face_items_path(album_id))


# ── Misc helpers ──────────────────────────────────────────────────────────────

def list_indexed_album_ids() -> list[str]:
    if not ALBUM_INDEX_ROOT.exists():
        return []
    return [
        path.name
        for path in ALBUM_INDEX_ROOT.iterdir()
        if path.is_dir()
        and album_index_path(path.name).exists()
        and album_items_path(path.name).exists()
    ]

def average_embeddings(embeddings: list[np.ndarray]) -> np.ndarray:
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
