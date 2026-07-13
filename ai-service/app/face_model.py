import io
import os

import cv2
import numpy as np
from insightface.app import FaceAnalysis
from PIL import Image

from app import config


class FaceModel:
    """
    Wraps InsightFace buffalo_l for face detection and embedding.
    Produces 512-dim L2-normalized embeddings suitable for cosine similarity search.
    """

    def __init__(self) -> None:
        cuda = config.use_cuda()
        providers = ["CUDAExecutionProvider", "CPUExecutionProvider"] if cuda else ["CPUExecutionProvider"]

        #cap InsightFace GPU allocator to the VRAM left after CLIP
        cuda_opts = {
            "gpu_mem_limit": str(config.FACE_GPU_MEM_LIMIT_MB * 1024 * 1024),
            "arena_extend_strategy": "kSameAsRequested",
            "cudnn_conv_algo_search": "HEURISTIC",
            "cudnn_conv_use_max_workspace": "0",
        }
        provider_options = [cuda_opts, {}] if cuda else [{}]

        # insightface takes its model root as a constructor arg
        model_root = os.environ.get("INSIGHTFACE_HOME", "~/.insightface")
        self.app = FaceAnalysis(name=config.FACE_MODEL_NAME, root=model_root,
                                providers=providers, provider_options=provider_options)
        ctx_id = 0 if cuda else -1
        self.app.prepare(ctx_id=ctx_id, det_size=(config.FACE_DET_SIZE, config.FACE_DET_SIZE))

    def decode_image(self, image_bytes: bytes) -> np.ndarray | None:
        """decode image bytes to a BGR array"""
        return _decode_image_bgr(image_bytes)

    def detect_on_image(self, image: np.ndarray | None) -> list[dict]:
        """
        face detection + embedding on an already-decoded BGR image.
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
        return self.detect_on_image(self.decode_image(image_bytes))

    def embedding_from_bytes(self, image_bytes: bytes) -> np.ndarray | None:
        """
        returns the embedding of the most obvi face found, or None if no face detected.
        """
        faces = self.extract_faces(image_bytes)

        if not faces:
            return None
        
        best = max(faces, key=lambda f: f["confidence"])
        return best["embedding"]


def _decode_image_bgr(image_bytes: bytes) -> np.ndarray | None:
    try:
        image_pil = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        image_np = np.array(image_pil)
        return cv2.cvtColor(image_np, cv2.COLOR_RGB2BGR)
    except Exception:
        return None
