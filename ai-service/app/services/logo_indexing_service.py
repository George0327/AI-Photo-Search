from app.clip_model import ClipModel
from app.detection_faiss_store import DetectionFaissStore
from app.logo_region_detector import LogoRegionDetector


def index_logos(
    image_bytes: bytes,
    image_id: str,
    logo_detector: LogoRegionDetector,
    clip_model: ClipModel,
    store: DetectionFaissStore,
) -> dict:
    """
    Detects logo regions in the image, embeds each crop with CLIP, and adds to the store.
    Images that have already been processed are skipped (idempotent).
    """
    if store.is_image_indexed(image_id):
        return {"success": True, "logo_region_count": 0, "message": f"Image {image_id} already logo-indexed"}

    try:
        regions = logo_detector.detect_logo_regions(image_bytes)
    except Exception as exc:
        return {"success": False, "logo_region_count": 0, "message": f"Logo detection failed: {exc}"}

    indexed_count = 0
    for region in regions:
        try:
            crop_bytes = LogoRegionDetector.crop_region(image_bytes, region["bbox"])
            embedding = clip_model.image_to_embedding(crop_bytes)
            store.add(embedding, image_id, region["bbox"], region["confidence"])
            indexed_count += 1
        except Exception:
            continue

    store.mark_processed(image_id)

    return {
        "success": True,
        "logo_region_count": indexed_count,
        "message": f"Indexed {indexed_count} logo region(s) for image {image_id}",
    }
