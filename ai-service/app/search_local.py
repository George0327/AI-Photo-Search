import sys
from pathlib import Path
from clip_model import ClipModel
from faiss_store import FaissStore


INDEX_PATH = Path("indexes/images.index")
ITEMS_PATH = Path("indexes/images.json")


def main():
    if len(sys.argv) < 2:
        print('Usage: python app/search_local.py "your prompt here"')
        return

    query = sys.argv[1]

    if not INDEX_PATH.exists() or not ITEMS_PATH.exists():
        print("Index files not found. Run index_local_images.py first.")
        return

    model = ClipModel()
    store = FaissStore.load(INDEX_PATH, ITEMS_PATH)

    query_vector = model.text_to_embedding(query)
    results = store.search(query_vector, top_k=10)

    print(f'\nQuery: "{query}"\n')
    for i, result in enumerate(results, start=1):
        print(f"{i}. {result['item_id']}  | score={result['score']:.4f}")


if __name__ == "__main__":
    main()