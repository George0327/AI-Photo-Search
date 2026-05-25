from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from app.schemas import SearchRequest, SearchResponse, HealthResponse
from app.services.search_service import search_images
from app.services.indexing_service import index_local_images, index_single_image_stream
from app.clip_model import ClipModel
from app.faiss_store import FaissStore
from pathlib import Path
from starlette.middleware.sessions import SessionMiddleware
from app.auth_temp import router as auth_router

app = FastAPI(
    title="AI Photo Search Service",
    version="1.0.0"
)

# --- GLOBAL STATE ---
model = None
store = None

INDEX_PATH = Path("indexes/images.index")
ITEMS_PATH = Path("indexes/images.json")

app.add_middleware(SessionMiddleware, secret_key="temporary_secret")
# app.include_router(auth_router)

@app.on_event("startup")
def startup_event():
    global model, store

    print("Loading CLIP model...")
    model = ClipModel()

    if INDEX_PATH.exists() and ITEMS_PATH.exists():
        print("Loading FAISS index...")
        store = FaissStore.load(INDEX_PATH, ITEMS_PATH)
    else:
        print("No index found at startup.")


@app.get("/health", response_model=HealthResponse)
def health_check():
    return {"status": "ok"}


@app.post("/search", response_model=SearchResponse)
def search(request: SearchRequest):
    global model, store

    if store is None:
        raise HTTPException(status_code=400, detail="Index not loaded")

    result = search_images(
        query=request.query,
        top_k=request.top_k,
        model=model,
        store=store
    )

    return result


@app.post("/index/local")
def index_local():
    global model, store

    result = index_local_images(model=model)

    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])

    # reload index after indexing
    store = FaissStore.load(INDEX_PATH, ITEMS_PATH)

    return result


@app.post("/index/stream")
async def index_stream(
    file: UploadFile = File(...), 
    image_id: str = Form(...)
):
    """
    Receives an image via HTTP POST, indexes it in RAM, and discards it.
    """
    global model, store

    if store is None:
        # If the index is totally empty/missing at startup, initialize an empty one
        store = FaissStore(dimension=768)

    # Read the image file directly into memory
    image_bytes = await file.read()

    # Process it
    result = index_single_image_stream(
        image_bytes=image_bytes,
        image_id=image_id,
        model=model,
        store=store
    )

    if not result["success"]:
        raise HTTPException(status_code=400, detail=result["message"])

    # Save the updated index to disk to prevent data loss on server restart
    store.save(INDEX_PATH, ITEMS_PATH)

    return result