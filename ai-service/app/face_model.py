import io

import cv2
import numpy as np
from insightface.app import FaceAnalysis
from PIL import Image


class FaceModel:
    """
    Wraps InsightFace buffalo_l (ArcFace R100) for face detection and embedding.
    Produces 512-dim L2-normalized embeddings suitable for cosine similarity search.
    """

    def __init__(self) -> None:
        self.app = FaceAnalysis(
            name="buffalo_l",
            providers=["CPUExecutionProvider"],
        )
        # det_size controls the detection resolution; 640x640 is a good balance
        self.app.prepare(ctx_id=0, det_size=(640, 640))

    def extract_faces(self, image_bytes: bytes) -> list[dict]:
        """
        Detects all faces in the image and returns their embeddings.

        Returns a list of:
            {
                "embedding": np.ndarray shape (512,) float32, L2-normalized,
                "bbox": [x1, y1, x2, y2] in pixel coordinates,
                "confidence": float detection score,
            }
        """
        image = _decode_image_bgr(image_bytes)
        if image is None:
            return []

        faces = self.app.get(image)

        results = []
        for face in faces:
            embedding = face.normed_embedding.astype("float32")
            bbox = [int(v) for v in face.bbox.tolist()]
            confidence = float(face.det_score)
            results.append({"embedding": embedding, "bbox": bbox, "confidence": confidence})

        return results

    def embedding_from_bytes(self, image_bytes: bytes) -> np.ndarray | None:
        """
        Convenience method for reference images: returns the embedding of the
        most prominent face found (highest confidence), or None if no face detected.
        """
        faces = self.extract_faces(image_bytes)
        if not faces:
            return None
        best = max(faces, key=lambda f: f["confidence"])
        return best["embedding"]


def _decode_image_bgr(image_bytes: bytes) -> np.ndarray | None:
    """Decode image bytes to a BGR numpy array for OpenCV/InsightFace."""
    try:
        image_pil = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        image_np = np.array(image_pil)
        return cv2.cvtColor(image_np, cv2.COLOR_RGB2BGR)
    except Exception:
        return None
