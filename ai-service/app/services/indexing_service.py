from pathlib import Path
from app.clip_model import ClipModel
from app.faiss_store import FaissStore


SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
IMAGES_DIR = Path("data/images")
INDEX_DIR = Path("indexes")
INDEX_PATH = INDEX_DIR / "images.index"
ITEMS_PATH = INDEX_DIR / "images.json"


def index_local_images(model) -> dict:
    INDEX_DIR.mkdir(parents=True, exist_ok=True)

    store = FaissStore(dimension=768)

    image_paths = [
        path for path in IMAGES_DIR.iterdir()
        if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
    ]

    if not image_paths:
        return {
            "success": False,
            "message": "No images found in data/images",
            "indexed_count": 0,
        }

    indexed_count = 0
    failed = []

    for image_path in image_paths:
        try:
            vector = model.image_to_embedding(image_path)
            store.add(vector, image_path.name)
            indexed_count += 1
        except Exception as exc:
            failed.append({"file": image_path.name, "error": str(exc)})

    store.save(INDEX_PATH, ITEMS_PATH)

    return {
        "success": True,
        "message": "Indexing completed",
        "indexed_count": indexed_count,
        "failed": failed,
        "index_path": str(INDEX_PATH),
        "items_path": str(ITEMS_PATH),
    }