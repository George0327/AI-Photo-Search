from pathlib import Path
from clip_model import ClipModel
from faiss_store import FaissStore


IMAGES_DIR = Path("data/images")
INDEX_DIR = Path("indexes")
INDEX_PATH = INDEX_DIR / "images.index"
ITEMS_PATH = INDEX_DIR / "images.json"

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def main():
    INDEX_DIR.mkdir(parents=True, exist_ok=True)

    model = ClipModel()
    store = FaissStore(dimension=768)  # ViT-L-14 text/image embedding size

    image_paths = [
        path for path in IMAGES_DIR.iterdir()
        if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
    ]

    if not image_paths:
        print("No images found in data/images")
        return

    print(f"Found {len(image_paths)} images.")

    for image_path in image_paths:
        try:
            vector = model.image_to_embedding(image_path)
            store.add(vector, image_path.name)
            print(f"Indexed: {image_path.name}")
        except Exception as exc:
            print(f"Failed to index {image_path.name}: {exc}")

    store.save(INDEX_PATH, ITEMS_PATH)
    print(f"Saved index to {INDEX_PATH}")
    print(f"Saved mapping to {ITEMS_PATH}")


if __name__ == "__main__":
    main()