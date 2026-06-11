import json
import os
from pathlib import Path

import faiss
import numpy as np


class FaissStore:
    def __init__(self, dimension: int):
        self.dimension = dimension
        self.index = faiss.IndexFlatIP(dimension)
        self.items: list[str] = []
        self._item_set: set[str] = set()

    def add(self, vector: np.ndarray, item_id: str) -> None:
        if vector.ndim == 1:
            vector = vector.reshape(1, -1)
        self.index.add(vector)
        self.items.append(item_id)
        self._item_set.add(item_id)

    def contains(self, item_id: str) -> bool:
        return item_id in self._item_set

    def search(self, query_vector: np.ndarray, top_k: int = 5) -> list[dict]:
        if query_vector.ndim == 1:
            query_vector = query_vector.reshape(1, -1)

        actual_k = min(top_k, len(self.items))
        if actual_k == 0:
            return []

        scores, indices = self.index.search(query_vector, actual_k)

        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx == -1:
                continue
            results.append({"item_id": self.items[idx], "score": float(score)})
        return results

    def save(self, index_path: str | Path, items_path: str | Path) -> None:
        index_path = Path(index_path)
        items_path = Path(items_path)
        tmp_index = index_path.with_name(index_path.name + ".tmp")
        tmp_items = items_path.with_name(items_path.name + ".tmp")
        try:
            faiss.write_index(self.index, str(tmp_index))
            tmp_items.write_text(json.dumps(self.items, indent=2), encoding="utf-8")
            os.replace(tmp_index, index_path)
            os.replace(tmp_items, items_path)
        except Exception:
            tmp_index.unlink(missing_ok=True)
            tmp_items.unlink(missing_ok=True)
            raise

    @classmethod
    def load(cls, index_path: str | Path, items_path: str | Path) -> "FaissStore":
        index = faiss.read_index(str(index_path))
        items: list[str] = json.loads(Path(items_path).read_text(encoding="utf-8"))

        store = cls(index.d)
        store.index = index
        store.items = items
        store._item_set = set(items)
        return store
