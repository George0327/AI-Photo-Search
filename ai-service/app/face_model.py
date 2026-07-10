import io
import os

import cv2
import numpy as np
from insightface.app import FaceAnalysis
from PIL import Image

from app import config


class FaceModel:
    """
    Wraps InsightFace buffalo_l (SCRFD detector + ArcFace R50 recogniser, w600k_r50) for face detection and embedding.
    Produces 512-dim L2-normalized embeddings suitable for cosine similarity search.
    """

    def __init__(self) -> None:
        cuda = config.use_cuda()
        providers = ["CUDAExecutionProvider", "CPUExecutionProvider"] if cuda else ["CPUExecutionProvider"]

        # Cap InsightFace's GPU allocator (HEURISTIC conv search + hard gpu_mem_limit) to the VRAM left after CLIP; its onnxruntime defaults can otherwise CUDA-OOM the whole process.
        cuda_opts = {
            "gpu_mem_limit": str(config.FACE_GPU_MEM_LIMIT_MB * 1024 * 1024),
            "arena_extend_strategy": "kSameAsRequested",
            "cudnn_conv_algo_search": "HEURISTIC",
            "cudnn_conv_use_max_workspace": "0",
        }
        provider_options = [cuda_opts, {}] if cuda else [{}]

        # insightface takes its model root as a constructor arg (ignores INSIGHTFACE_HOME), so pass the cache dir or buffalo_l re-downloads on every container recreation.
        model_root = os.environ.get("INSIGHTFACE_HOME", "~/.insightface")
        self.app = FaceAnalysis(name=config.FACE_MODEL_NAME, root=model_root,
                                providers=providers, provider_options=provider_options)
        ctx_id = 0 if cuda else -1
        self.app.prepare(ctx_id=ctx_id, det_size=(config.FACE_DET_SIZE, config.FACE_DET_SIZE))

    def decode_image(self, image_bytes: bytes) -> np.ndarray | None:
        """Decode image bytes to a BGR array. CPU-bound and stateless, so it is
        safe to run on many worker threads in parallel (unlike detection)."""
        return _decode_image_bgr(image_bytes)

    def detect_on_image(self, image: np.ndarray | None) -> list[dict]:
        """
        Run face detection + embedding on an already-decoded BGR image.

        GPU-bound: the InsightFace/onnxruntime session is not concurrency-safe
        and the GPU runs inferences serially, so callers must serialize this
        (see face_executor). Returns a list of:
            {
                "embedding": np.ndarray shape (512,) float32, L2-normalized,
                "bbox": [x1, y1, x2, y2] in pixel coordinates,
                "confidence": float detection score,
            }
        """
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

    def extract_faces(self, image_bytes: bytes) -> list[dict]:
        """Decode + detect in one call (decode is cheap relative to detection;
        used by low-volume paths). The batch path splits the two stages so the
        decode can run in parallel — see routers/faces.py."""
        return self.detect_on_image(self.decode_image(image_bytes))

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
