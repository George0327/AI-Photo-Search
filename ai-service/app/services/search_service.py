from pathlib import Path
from app.clip_model import ClipModel
from app.faiss_store import FaissStore


INDEX_PATH = Path("indexes/images.index")
ITEMS_PATH = Path("indexes/images.json")


def search_images(query: str, top_k: int, model, store) -> dict:
    if not INDEX_PATH.exists() or not ITEMS_PATH.exists():
        return {
            "success": False,
            "message": "Index files not found. Run indexing first.",
            "results": [],
        }

    query_vector = model.text_to_embedding(query)
    results = store.search(query_vector, top_k=top_k)

    return {
        "success": True,
        "message": "Search completed",
        "query": query,
        "top_k": top_k,
        "results": results,
    }