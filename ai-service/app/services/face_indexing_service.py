from app.detection_faiss_store import DetectionFaissStore
from app.face_model import FaceModel


def index_faces(
    image_bytes: bytes,
    image_id: str,
    face_model: FaceModel,
    store: DetectionFaissStore,
) -> dict:
    """
    Detects all faces in the image and adds their embeddings to the store.
    Images that have already been processed are skipped (idempotent).
    """
    if store.is_image_indexed(image_id):
        return {"success": True, "face_count": 0, "message": f"Image {image_id} already face-indexed"}

    try:
        faces = face_model.extract_faces(image_bytes)
    except Exception as exc:
        return {"success": False, "face_count": 0, "message": f"Face detection failed: {exc}"}

    for face in faces:
        store.add(face["embedding"], image_id, face["bbox"], face["confidence"])

    # Mark as processed even if no faces were found, so we don't retry every time
    store.mark_processed(image_id)

    return {
        "success": True,
        "face_count": len(faces),
        "message": f"Indexed {len(faces)} face(s) for image {image_id}",
    }
