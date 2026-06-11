from contextlib import asynccontextmanager

from fastapi import FastAPI

import app.state as state
from app.clip_model import ClipModel
from app.face_model import FaceModel
from app.routers import clip, faces
from app.schemas import HealthResponse


@asynccontextmanager
async def lifespan(app: FastAPI):
    state.model = ClipModel()
    state._embedding_dim = int(state.model.text_to_embedding("test").shape[1])
    print(f"CLIP ready — {state.model.model_name}/{state.model.pretrained} (dim={state._embedding_dim})")

    state.face_model = FaceModel()
    print("Face model ready — InsightFace buffalo_l")

    state.ALBUM_INDEX_ROOT.mkdir(parents=True, exist_ok=True)
    print("AI service ready.")
    yield


app = FastAPI(title="AI Photo Search Service", version="2.0.0", lifespan=lifespan)

app.include_router(clip.router)
app.include_router(faces.router)


@app.get("/health", response_model=HealthResponse)
def health_check():
    return {"status": "ok"}
