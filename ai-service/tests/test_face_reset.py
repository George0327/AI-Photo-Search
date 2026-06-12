import numpy as np
import pytest
from fastapi.testclient import TestClient

import app.state as state
from app.main import app

API_KEY_HEADER = {"X-Internal-API-Key": "dev-ai-service-key"}


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(state, "ALBUM_INDEX_ROOT", tmp_path / "albums")
    state._face_stores.clear()
    state._face_locks.clear()
    # No context manager: the lifespan (real model loading) must not run.
    return TestClient(app)


def test_face_reset_requires_api_key(client):
    assert client.post("/index/albums/album-1/faces/reset").status_code == 401


def test_face_reset_creates_empty_persisted_store(client, tmp_path):
    # Pre-populate an in-memory face store to prove reset replaces it.
    store = state.get_cached_face_store("album-1")
    vector = np.ones(state.FACE_INDEX_DIM, dtype="float32")
    store.add(vector, "img-1", [0, 0, 10, 10], 0.99)
    assert store.is_image_indexed("img-1")

    response = client.post("/index/albums/album-1/faces/reset", headers=API_KEY_HEADER)
    assert response.status_code == 200
    assert response.json()["success"] is True

    fresh = state.get_cached_face_store("album-1")
    assert not fresh.is_image_indexed("img-1")
    assert fresh.items == []

    index_dir = tmp_path / "albums" / "album-1"
    assert (index_dir / "faces.index").exists()
    assert (index_dir / "faces.json").exists()
