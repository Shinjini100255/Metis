from app.schemas.prediction import ModelStatus


class ModelRegistry:
    """Explicit foundation contract. No deserialization of untrusted model files."""

    def list_models(self) -> list[dict]:
        return []

    def status(self) -> ModelStatus:
        return ModelStatus()


registry = ModelRegistry()
