FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
    g++ \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ app/
ENV HF_HOME=/model-cache/huggingface
ENV INSIGHTFACE_HOME=/model-cache/insightface
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
