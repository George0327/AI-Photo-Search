# syntax=docker/dockerfile:1
FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    g++ \
    libheif1 \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt
COPY app/ app/
ENV HF_HOME=/model-cache/huggingface
ENV INSIGHTFACE_HOME=/model-cache/insightface
# Exactly one worker: FAISS stores and per-album locks live in process memory,
# so multiple workers would race each other writing the same index files.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
