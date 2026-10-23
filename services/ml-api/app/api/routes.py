from fastapi import APIRouter
from app.core.config import settings
from app.schemas.prediction import ModelStatus
from app.services.model_registry import registry

router = APIRouter()


@router.get("/health")
def health() -> dict:
    return {"status": "UP", "service": settings.service_name, "version": settings.version}


@router.get("/models")
def models() -> list[dict]:
    return registry.list_models()


@router.get("/model-status", response_model=ModelStatus)
def model_status() -> ModelStatus:
    return registry.status()
