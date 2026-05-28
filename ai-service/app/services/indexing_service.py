from app.clip_model import ClipModel
from app.faiss_store import FaissStore


def index_single_image_stream(
    image_bytes: bytes,
    image_id: str,
    model: ClipModel,
    store: FaissStore,
) -> dict:
    try:
        vector = model.image_to_embedding(image_bytes)
        store.add(vector, image_id)
        return {"success": True, "message": f"Successfully indexed image {image_id}"}
    except Exception as exc:
        return {"success": False, "message": f"Failed to process image: {exc}"}
