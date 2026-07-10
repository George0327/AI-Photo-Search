"""Central configuration for models, device, and parallelism.

This is the single place to retune the AI service for different hardware. Every
value has a sensible default here and can be overridden with an environment
variable (set in .env / docker-compose) — so moving the app to another machine
means changing config here or env vars, never hunting through the code.
"""
import os


def _int(name: str, default: int) -> int:
    raw = os.getenv(name)
    return int(raw) if raw not in (None, "") else default


# ── Device ───────────────────────────────────────────────────────────────────
# "auto" = use the GPU when available, else CPU. Force with "cuda" or "cpu".
DEVICE = os.getenv("AI_DEVICE", "auto").lower()


def use_cuda() -> bool:
    """Resolve whether to use the GPU, honoring DEVICE. Imports torch lazily."""
    if DEVICE == "cpu":
        return False
    import torch
    if DEVICE == "cuda":
        return True
    return torch.cuda.is_available()


# ── CLIP (semantic photo search) ─────────────────────────────────────────────
CLIP_MODEL_NAME = os.getenv("CLIP_MODEL_NAME", "ViT-L-14")
CLIP_PRETRAINED = os.getenv("CLIP_PRETRAINED", "openai")
# "fp16" halves CLIP's VRAM (≈2.3 GB → ≈1.2 GB) to share a small GPU with the face model; CUDA only, CPU runs fp32. Embeddings stay normalized, so quality is unaffected.
CLIP_PRECISION = os.getenv("CLIP_PRECISION", "fp16").lower()
# Max images per CLIP forward pass (bounds peak memory per batch request).
CLIP_ENCODE_CHUNK_SIZE = _int("CLIP_ENCODE_CHUNK_SIZE", 16)

# ── Face model (InsightFace) ─────────────────────────────────────────────────
FACE_MODEL_NAME = os.getenv("FACE_MODEL_NAME", "buffalo_l")
FACE_DET_SIZE = _int("FACE_DET_SIZE", 640)  # square detection input (px)
# Hard cap (MB) on InsightFace's own GPU allocator; must fit the VRAM left after CLIP loads. CUDA only.
FACE_GPU_MEM_LIMIT_MB = _int("FACE_GPU_MEM_LIMIT_MB", 768)

# ── Parallelism ──────────────────────────────────────────────────────────────
# General ML/CPU thread pool (image decode, FAISS I/O, CLIP). 0 = os.cpu_count().
ML_EXECUTOR_WORKERS = _int("ML_EXECUTOR_WORKERS", 0)
# Concurrent InsightFace inferences; 1 is safest on a single GPU (it runs them serially anyway). Raise only on hardware with spare capacity.
FACE_INFERENCE_CONCURRENCY = _int("FACE_INFERENCE_CONCURRENCY", 1)
