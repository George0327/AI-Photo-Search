import asyncio
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile

from app.clip_model import ClipModel
from app.faiss_store import FaissStore
from app.schemas import HealthResponse, SearchRequest, SearchResponse
from app.services.indexing_service import index_single_image_stream

INDEX_ROOT = Path("indexes")
ALBUM_INDEX_ROOT = INDEX_ROOT / "albums"
INTERNAL_API_KEY = os.getenv("AI_SERVICE_INTERNAL_API_KEY", "dev-ai-service-key")

# Populated once at startup
model: ClipModel | None = None
_embedding_dim: int | None = None

# Per-album in-memory store cache and async locks (asyncio is single-threaded,
# so plain dict access is safe here — no threading.Lock needed)
_album_stores: dict[str, FaissStore] = {}
_album_locks: dict[str, asyncio.Lock] = {}


def _get_lock(album_id: str) -> asyncio.Lock:
    if album_id not in _album_locks:
        _album_locks[album_id] = asyncio.Lock()
    return _album_locks[album_id]


def _get_cached_store(album_id: str) -> FaissStore:
    """Return the cached FaissStore or load from disk. Call only inside the album lock."""
    if album_id not in _album_stores:
        _album_stores[album_id] = _load_album_store(album_id)
    return _album_stores[album_id]


def verify_internal_api_key(x_internal_api_key: str | None = Header(default=None)):
    if x_internal_api_key != INTERNAL_API_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized")


def album_dir(album_id: str) -> Path:
    return ALBUM_INDEX_ROOT / album_id


def album_index_path(album_id: str) -> Path:
    return album_dir(album_id) / "images.index"


def album_items_path(album_id: str) -> Path:
    return album_dir(album_id) / "images.json"


def _load_album_store(album_id: str) -> FaissStore:
    index_path = album_index_path(album_id)
    items_path = album_items_path(album_id)
    if index_path.exists() and items_path.exists():
        return FaissStore.load(index_path, items_path)
    return FaissStore(dimension=_embedding_dim)


def _save_album_store(album_id: str, store: FaissStore) -> None:
    directory = album_dir(album_id)
    directory.mkdir(parents=True, exist_ok=True)
    store.save(album_index_path(album_id), album_items_path(album_id))


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


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, _embedding_dim
    print("Loading CLIP model...")
    model = ClipModel()
    _embedding_dim = int(model.text_to_embedding("test").shape[1])
    ALBUM_INDEX_ROOT.mkdir(parents=True, exist_ok=True)
    print(f"AI service ready. Embedding dimension: {_embedding_dim}")
    yield


app = FastAPI(
    title="AI Photo Search Service",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health", response_model=HealthResponse)
def health_check():
    return {"status": "ok"}


@app.post("/index/albums/{album_id}/reset")
async def reset_album_index(
    album_id: str,
    _: None = Depends(verify_internal_api_key),
):
    async with _get_lock(album_id):
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
    # Read file bytes before acquiring the lock so I/O doesn't block other albums
    image_bytes = await file.read()

    async with _get_lock(album_id):
        store = _get_cached_store(album_id)

        if store.contains(image_id):
            return {
                "success": True,
                "message": f"Image {image_id} already indexed in album {album_id}",
            }

        result = index_single_image_stream(
            image_bytes=image_bytes,
            image_id=image_id,
            model=model,
            store=store,
        )

        if not result["success"]:
            raise HTTPException(status_code=400, detail=result["message"])

        _save_album_store(album_id, store)

    return result


@app.post("/search", response_model=SearchResponse)
async def search(
    request: SearchRequest,
    _: None = Depends(verify_internal_api_key),
):
    if request.album_ids:
        album_ids = [
            a for a in request.album_ids
            if album_index_path(a).exists() and album_items_path(a).exists()
        ]
    else:
        album_ids = _list_indexed_album_ids()

    if not album_ids:
        return {
            "success": False,
            "message": "No matching album indexes found. Run indexing first.",
            "query": request.query,
            "top_k": request.top_k,
            "results": [],
        }

    query_vector = model.text_to_embedding(request.query)
    merged_results = []

    for album_id in album_ids:
        async with _get_lock(album_id):
            store = _get_cached_store(album_id)
            if len(store.items) == 0:
                continue
            album_results = store.search(query_vector, top_k=request.top_k)

        for result in album_results:
            merged_results.append({
                "item_id": result["item_id"],
                "album_id": album_id,
                "score": result["score"],
            })

    merged_results.sort(key=lambda r: r["score"], reverse=True)

    return {
        "success": True,
        "message": "Search completed",
        "query": request.query,
        "top_k": request.top_k,
        "results": merged_results[: request.top_k],
    }
