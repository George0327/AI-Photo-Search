"""Models, device and parallelism settings. Every value can be overridden by an env var."""
import os


def _int(name: str, default: int) -> int:
    raw = os.getenv(name)
    return int(raw) if raw not in (None, "") else default


# Device

DEVICE = os.getenv("AI_DEVICE", "auto").lower()


def use_cuda() -> bool:
    if DEVICE == "cpu":
        return False

    import torch

    if DEVICE == "cuda":
        return True

    return torch.cuda.is_available()


# CLIP

CLIP_MODEL_NAME = os.getenv("CLIP_MODEL_NAME", "ViT-L-14")
CLIP_PRETRAINED = os.getenv("CLIP_PRETRAINED", "openai")
CLIP_PRECISION = os.getenv("CLIP_PRECISION", "fp16").lower()  # fp16 halves VRAM, CUDA only
CLIP_ENCODE_CHUNK_SIZE = _int("CLIP_ENCODE_CHUNK_SIZE", 16)

# Face model (InsightFace)

FACE_MODEL_NAME = os.getenv("FACE_MODEL_NAME", "buffalo_l")
FACE_DET_SIZE = _int("FACE_DET_SIZE", 640)
FACE_GPU_MEM_LIMIT_MB = _int("FACE_GPU_MEM_LIMIT_MB", 768)  # must fit the VRAM left after CLIP

# Parallelism

ML_EXECUTOR_WORKERS = _int("ML_EXECUTOR_WORKERS", 0)  # 0 = os.cpu_count()
FACE_INFERENCE_CONCURRENCY = _int("FACE_INFERENCE_CONCURRENCY", 1)
