import io

import torch
from PIL import Image
from transformers import Owlv2ForObjectDetection, Owlv2Processor


class LogoRegionDetector:
    """
    Uses OWL-ViT v2 (google/owlv2-base-patch16) for open-vocabulary logo region detection.
    Finds logo-like regions using generic text prompts — works for any logo, known or unknown.
    """

    # Generic prompts that describe logo-like regions
    TEXT_QUERIES = [["logo", "company logo", "brand emblem", "badge", "sticker", "patch"]]

    def __init__(self) -> None:
        self.processor = Owlv2Processor.from_pretrained("google/owlv2-base-patch16")
        self.model = Owlv2ForObjectDetection.from_pretrained("google/owlv2-base-patch16")
        self.model.eval()

    def detect_logo_regions(
        self,
        image_bytes: bytes,
        threshold: float = 0.07,
        max_regions: int = 10,
    ) -> list[dict]:
        """
        Detects logo-like regions in the image.

        Returns a list of:
            {
                "bbox": [x1, y1, x2, y2] in pixel coordinates,
                "confidence": float,
            }
        """
        try:
            image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        except Exception:
            return []

        w, h = image.size

        inputs = self.processor(
            text=self.TEXT_QUERIES,
            images=image,
            return_tensors="pt",
        )

        with torch.no_grad():
            outputs = self.model(**inputs)

        target_sizes = torch.tensor([(h, w)])
        results = self.processor.post_process_object_detection(
            outputs=outputs,
            threshold=threshold,
            target_sizes=target_sizes,
        )[0]

        regions = []
        scores = results["scores"].tolist()
        boxes = results["boxes"].tolist()

        # Sort by confidence descending and take at most max_regions
        candidates = sorted(zip(scores, boxes), key=lambda x: x[0], reverse=True)

        for score, box in candidates[:max_regions]:
            x1, y1, x2, y2 = [int(v) for v in box]
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(w, x2), min(h, y2)

            # Skip degenerate boxes (too small to be meaningful logos)
            if x2 <= x1 or y2 <= y1 or (x2 - x1) * (y2 - y1) < 400:
                continue

            regions.append({"bbox": [x1, y1, x2, y2], "confidence": float(score)})

        return regions

    @staticmethod
    def crop_region(image_bytes: bytes, bbox: list[int]) -> bytes:
        """Crop the image to the given bounding box and return as JPEG bytes."""
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        x1, y1, x2, y2 = bbox
        crop = image.crop((x1, y1, x2, y2))
        buf = io.BytesIO()
        crop.save(buf, format="JPEG")
        return buf.getvalue()
