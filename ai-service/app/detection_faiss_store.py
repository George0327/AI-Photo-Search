import json
import os
from pathlib import Path

import faiss
import numpy as np


class DetectionFaissStore:
    """
    FAISS store for face/logo region embeddings.
    Each entry maps to a detected region: {image_id, bbox, confidence}.
    One photo can have multiple entries (one per detected face or logo region).
    Search results are deduplicated by image_id, keeping the best score per photo.
    """

    def __init__(self, dimension: int):
        self.dimension = dimension
        self.index = faiss.IndexFlatIP(dimension)
        self.items: list[dict] = []
        self._processed_image_ids: set[str] = set()

    def add(
        self,
        vector: np.ndarray,
        image_id: str,
        bbox: list[int],
        confidence: float,
    ) -> None:
        if vector.ndim == 1:
            vector = vector.reshape(1, -1)
        self.index.add(vector)
        self.items.append({"image_id": image_id, "bbox": bbox, "confidence": confidence})
        self._processed_image_ids.add(image_id)

    def is_image_indexed(self, image_id: str) -> bool:
        """True if this image has already been processed (even if no regions were found)."""
        return image_id in self._processed_image_ids

    def mark_processed(self, image_id: str) -> None:
        """Mark an image as processed even when no detectable regions were found."""
        self._processed_image_ids.add(image_id)

    def search(self, query_vector: np.ndarray, top_k: int) -> list[dict]:
        """
        Returns up to top_k results, deduplicated by image_id.
        Each result: {image_id, bbox, confidence, score}
        """
        if not self.items:
            return []

        if query_vector.ndim == 1:
            query_vector = query_vector.reshape(1, -1)

        # Fetch extra candidates to survive deduplication
        k = min(top_k * 10, len(self.items))
        scores, indices = self.index.search(query_vector, k)

        best_per_image: dict[str, dict] = {}
        for score, idx in zip(scores[0], indices[0]):
            if idx == -1:
                continue
            item = self.items[idx]
            image_id = item["image_id"]
            score_val = float(score)
            if image_id not in best_per_image or score_val > best_per_image[image_id]["score"]:
                best_per_image[image_id] = {**item, "score": score_val}

        results = sorted(best_per_image.values(), key=lambda x: x["score"], reverse=True)
        return results[:top_k]

    def save(self, index_path: str | Path, items_path: str | Path) -> None:
        index_path = Path(index_path)
        items_path = Path(items_path)
        data = {
            "items": self.items,
            "processed_image_ids": list(self._processed_image_ids),
        }
        tmp_index = index_path.with_name(index_path.name + ".tmp")
        tmp_items = items_path.with_name(items_path.name + ".tmp")
        try:
            faiss.write_index(self.index, str(tmp_index))
            tmp_items.write_text(json.dumps(data, indent=2), encoding="utf-8")
            os.replace(tmp_index, index_path)
            os.replace(tmp_items, items_path)
        except Exception:
            tmp_index.unlink(missing_ok=True)
            tmp_items.unlink(missing_ok=True)
            raise

    @classmethod
    def load(cls, index_path: str | Path, items_path: str | Path) -> "DetectionFaissStore":
        index = faiss.read_index(str(index_path))
        data = json.loads(Path(items_path).read_text(encoding="utf-8"))

        store = cls(index.d)
        store.index = index
        store.items = data["items"]
        store._processed_image_ids = set(data.get("processed_image_ids", [
            item["image_id"] for item in data["items"]
        ]))
        return store
