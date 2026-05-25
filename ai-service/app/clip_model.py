from pathlib import Path
import torch
from PIL import Image
import open_clip
import io # <-- NEW IMPORT

class ClipModel:
    def __init__(self, model_name: str = "ViT-L-14", pretrained: str = "openai") -> None:
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model, _, self.preprocess = open_clip.create_model_and_transforms(
            model_name=model_name,
            pretrained=pretrained,
        )
        self.tokenizer = open_clip.get_tokenizer(model_name)
        self.model.eval()
        self.model.to(self.device)

    def image_to_embedding(self, image_input):
        """Now accepts a file path (str/Path) or raw bytes."""
        
        # Check if the input is raw bytes from our stream
        if isinstance(image_input, bytes):
            image = Image.open(io.BytesIO(image_input)).convert("RGB")
        # Otherwise, treat it as a file path like before
        elif isinstance(image_input, (str, Path)):
            image = Image.open(image_input).convert("RGB")
        else:
            raise ValueError("Unsupported input type for image_to_embedding")

        image_tensor = self.preprocess(image).unsqueeze(0).to(self.device)

        with torch.no_grad():
            features = self.model.encode_image(image_tensor)
            features = features / features.norm(dim=-1, keepdim=True)

        return features.cpu().numpy().astype("float32")

    def text_to_embedding(self, text: str):
        tokens = self.tokenizer([text]).to(self.device)

        with torch.no_grad():
            features = self.model.encode_text(tokens)
            features = features / features.norm(dim=-1, keepdim=True)

        return features.cpu().numpy().astype("float32")