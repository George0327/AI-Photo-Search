from pathlib import Path
import torch
from PIL import Image
import open_clip
import io
from pillow_heif import register_heif_opener

from app import config

register_heif_opener()


class ClipModel:
    def __init__(self, model_name: str | None = None, pretrained: str | None = None) -> None:
        self.device = "cuda" if config.use_cuda() else "cpu"
        self.model_name = model_name or config.CLIP_MODEL_NAME
        self.pretrained = pretrained or config.CLIP_PRETRAINED
        # fp16 only on GPU — halves resident VRAM so CLIP can share a small card
        # with the face model. CPU stays fp32 (fp16 is slow/unsupported on CPU).
        self.fp16 = self.device == "cuda" and config.CLIP_PRECISION == "fp16"
        self.dtype = torch.float16 if self.fp16 else torch.float32
        # Upper bound on images per forward pass; keeps memory bounded regardless
        # of how many files a single batch request carries.
        self.chunk_size = config.CLIP_ENCODE_CHUNK_SIZE
        self.model, _, self.preprocess = open_clip.create_model_and_transforms(
            model_name=self.model_name,
            pretrained=self.pretrained,
        )
        self.tokenizer = open_clip.get_tokenizer(self.model_name)
        self.model.eval()
        self.model.to(self.device)
        if self.fp16:
            self.model.half()

    @staticmethod
    def _open_image(image_input) -> Image.Image:
        if isinstance(image_input, bytes):
            return Image.open(io.BytesIO(image_input)).convert("RGB")
        if isinstance(image_input, (str, Path)):
            return Image.open(image_input).convert("RGB")
        raise ValueError("Unsupported image input type")

    def image_to_embedding(self, image_input):
        """Accepts a file path or raw image bytes."""

        image = self._open_image(image_input)
        image_tensor = self.preprocess(image).unsqueeze(0).to(self.device, dtype=self.dtype)

        with torch.no_grad():
            features = self.model.encode_image(image_tensor)
            features = features / features.norm(dim=-1, keepdim=True)

        return features.cpu().numpy().astype("float32")

    def images_to_embeddings(self, image_inputs: list):
        """Encode many images using batched forward passes.

        Returns (embeddings, errors), both aligned with the input list:
        embeddings[i] is a (1, dim) float32 array or None if decoding failed,
        in which case errors[i] holds the error message.
        """
        embeddings: list = [None] * len(image_inputs)
        errors: list = [None] * len(image_inputs)

        tensors, ok_indices = [], []
        for i, image_input in enumerate(image_inputs):
            try:
                tensors.append(self.preprocess(self._open_image(image_input)))
                ok_indices.append(i)
            except Exception as exc:
                errors[i] = f"Failed to decode image: {exc}"

        for start in range(0, len(tensors), self.chunk_size):
            chunk = tensors[start : start + self.chunk_size]
            batch = torch.stack(chunk).to(self.device, dtype=self.dtype)
            with torch.no_grad():
                features = self.model.encode_image(batch)
                features = features / features.norm(dim=-1, keepdim=True)
            features = features.cpu().numpy().astype("float32")
            for row, input_index in enumerate(ok_indices[start : start + self.chunk_size]):
                embeddings[input_index] = features[row : row + 1]

        return embeddings, errors

    def text_to_embedding(self, text: str):
        tokens = self.tokenizer([text]).to(self.device)

        with torch.no_grad():
            features = self.model.encode_text(tokens)
            features = features / features.norm(dim=-1, keepdim=True)

        return features.cpu().numpy().astype("float32")