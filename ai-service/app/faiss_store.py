from pathlib import Path
import json
import faiss
import numpy as np


class FaissStore:
    def __init__(self, dimension: int):
        self.dimension = dimension
        self.index = faiss.IndexFlatIP(dimension)
        self.items: list[str] = []

    def add(self, vector: np.ndarray, item_id: str) -> None:
        if vector.ndim == 1:
            vector = vector.reshape(1, -1)

        self.index.add(vector)
        self.items.append(item_id)

    def search(self, query_vector: np.ndarray, top_k: int = 5):
        if query_vector.ndim == 1:
            query_vector = query_vector.reshape(1, -1)

        scores, indices = self.index.search(query_vector, top_k)

        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx == -1:
                continue
            results.append({
                "item_id": self.items[idx],
                "score": float(score),
            })
        return results

    def save(self, index_path: str | Path, items_path: str | Path) -> None:
        faiss.write_index(self.index, str(index_path))
        Path(items_path).write_text(json.dumps(self.items, indent=2), encoding="utf-8")

    @classmethod
    def load(cls, index_path: str | Path, items_path: str | Path):
        index = faiss.read_index(str(index_path))
        items = json.loads(Path(items_path).read_text(encoding="utf-8"))

        store = cls(index.d)
        store.index = index
        store.items = items
        return store