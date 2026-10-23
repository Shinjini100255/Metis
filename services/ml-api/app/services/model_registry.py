from app.schemas.prediction import ModelStatus
from app.services import artifacts
from app.core.errors import DataProblem

class ModelRegistry:
    def list_models(self) -> list[dict]:return artifacts.list_manifests()
    def status(self) -> ModelStatus:
        models=self.list_models()
        if not models:return ModelStatus()
        try:
            artifacts.manifest(models[0]["artifact_id"])
            return ModelStatus(available=True,status="READY",model_version=models[0]["artifact_id"],message="Development fixture models are available only in this isolated test service; they are not financial evidence." if models[0].get("development_only") else "A compatible model is registered. Inference also requires fresh forward observations for its asset and series.")
        except DataProblem:
            return ModelStatus(available=False,status="ARTIFACT_UNAVAILABLE",message="Registered artifact is unavailable, corrupted, or incompatible.")

registry=ModelRegistry()
