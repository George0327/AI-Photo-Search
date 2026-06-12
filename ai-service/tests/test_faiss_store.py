import numpy as np

from app.faiss_store import FaissStore


def unit_vector(dim: int, seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    v = rng.standard_normal(dim).astype("float32")
    return v / np.linalg.norm(v)


def test_add_contains_and_search_order():
    store = FaissStore(dimension=8)
    a, b = unit_vector(8, 1), unit_vector(8, 2)
    store.add(a, "img-a")
    store.add(b, "img-b")

    assert store.contains("img-a")
    assert store.contains("img-b")
    assert not store.contains("img-c")

    results = store.search(a, top_k=2)
    assert [r["item_id"] for r in results][0] == "img-a"
    assert results[0]["score"] > results[1]["score"]


def test_search_empty_store_returns_nothing():
    store = FaissStore(dimension=8)
    assert store.search(unit_vector(8, 1), top_k=5) == []


def test_save_and_load_roundtrip(tmp_path):
    store = FaissStore(dimension=8)
    a, b = unit_vector(8, 1), unit_vector(8, 2)
    store.add(a, "img-a")
    store.add(b, "img-b")

    index_path = tmp_path / "images.index"
    items_path = tmp_path / "images.json"
    store.save(index_path, items_path)

    loaded = FaissStore.load(index_path, items_path)
    assert loaded.items == ["img-a", "img-b"]
    assert loaded.contains("img-a")
    results = loaded.search(b, top_k=1)
    assert results[0]["item_id"] == "img-b"
