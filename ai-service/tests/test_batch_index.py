import numpy as np
import pytest
from fastapi.testclient import TestClient

import app.state as state
from app.main import app

API_KEY_HEADER = {"X-Internal-API-Key": "dev-ai-service-key"}
DIM = 8


def unit_vector(seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    v = rng.standard_normal(DIM).astype("float32")
    return (v / np.linalg.norm(v)).reshape(1, -1)


class StubClipModel:
    """Deterministic stand-in for the CLIP model: no weights, no GPU."""

    def _vector_for(self, payload: bytes) -> np.ndarray:
        return unit_vector(sum(payload) % 1000)

    def image_to_embedding(self, image_bytes: bytes) -> np.ndarray:
        if image_bytes == b"corrupt":
            raise ValueError("not an image")
        return self._vector_for(image_bytes)

    def images_to_embeddings(self, image_inputs: list):
        embeddings, errors = [], []
        for blob in image_inputs:
            if blob == b"corrupt":
                embeddings.append(None)
                errors.append("Failed to decode image: not an image")
            else:
                embeddings.append(self._vector_for(blob))
                errors.append(None)
        return embeddings, errors

    def text_to_embedding(self, text: str) -> np.ndarray:
        return unit_vector(len(text))


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(state, "model", StubClipModel())
    monkeypatch.setattr(state, "_embedding_dim", DIM)
    monkeypatch.setattr(state, "ALBUM_INDEX_ROOT", tmp_path / "albums")
    # In-memory caches are module-level dicts shared with the routers — clear
    # them in place so each test starts fresh.
    state._album_stores.clear()
    state._album_locks.clear()
    # TestClient used without a context manager on purpose: the lifespan (which
    # loads the real models) must not run.
    return TestClient(app)


def batch_files(payloads: dict[str, bytes]):
    files = [("files", (image_id, blob, "image/jpeg")) for image_id, blob in payloads.items()]
    # httpx needs repeated form fields as a dict with a list value.
    data = {"image_ids": list(payloads)}
    return files, data


def test_batch_requires_api_key(client):
    files, data = batch_files({"img-1": b"first"})
    response = client.post("/index/albums/album-1/batch", files=files, data=data)
    assert response.status_code == 401


def test_batch_indexes_images_and_persists_once(client, tmp_path):
    files, data = batch_files({"img-1": b"first", "img-2": b"second"})
    response = client.post("/index/albums/album-1/batch", files=files, data=data,
                           headers=API_KEY_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["success"] is True
    assert [r["image_id"] for r in body["results"]] == ["img-1", "img-2"]
    assert all(r["success"] for r in body["results"])

    index_dir = tmp_path / "albums" / "album-1"
    assert (index_dir / "images.index").exists()
    assert (index_dir / "images.json").exists()


def test_batch_reports_per_image_failures(client):
    files, data = batch_files({"img-ok": b"fine", "img-bad": b"corrupt"})
    response = client.post("/index/albums/album-1/batch", files=files, data=data,
                           headers=API_KEY_HEADER)
    body = response.json()
    assert body["success"] is False
    by_id = {r["image_id"]: r for r in body["results"]}
    assert by_id["img-ok"]["success"] is True
    assert by_id["img-bad"]["success"] is False
    assert "decode" in by_id["img-bad"]["message"]


def test_batch_is_idempotent(client):
    files, data = batch_files({"img-1": b"first"})
    client.post("/index/albums/album-1/batch", files=files, data=data, headers=API_KEY_HEADER)

    files, data = batch_files({"img-1": b"first"})
    response = client.post("/index/albums/album-1/batch", files=files, data=data,
                           headers=API_KEY_HEADER)
    assert response.json()["results"][0]["message"] == "Already indexed"


def test_batch_rejects_mismatched_lengths(client):
    files = [("files", ("img-1", b"first", "image/jpeg"))]
    data = {"image_ids": ["img-1", "img-2"]}
    response = client.post("/index/albums/album-1/batch", files=files, data=data,
                           headers=API_KEY_HEADER)
    assert response.status_code == 422


def test_search_returns_indexed_images_ranked(client):
    files, data = batch_files({"img-1": b"first", "img-2": b"second"})
    client.post("/index/albums/album-1/batch", files=files, data=data, headers=API_KEY_HEADER)

    response = client.post("/search", json={"query": "anything", "top_k": 2},
                           headers=API_KEY_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["success"] is True
    assert len(body["results"]) == 2
    scores = [r["score"] for r in body["results"]]
    assert scores == sorted(scores, reverse=True)
