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


from app.schemas.analytics import HistoryRequest
from app.services.quality import clean
from app.services.indicators import calculate

@router.post("/data/validate")
def validate_history(request: HistoryRequest) -> dict:
    bars, report = clean(request.bars)
    return {"bars": bars, "quality": report}

@router.post("/indicators")
def indicators(request: HistoryRequest) -> dict:
    bars, report = clean(request.bars)
    return {**calculate(bars), "quality": report}


from app.schemas.analytics import TrainingRequest,InferenceRequest,TrainingJobResponse,InferenceResponse
from app.services import jobs
from app.services.dataset import build,FEATURES
from app.services.indicators import safe_number
from app.services.inference import predict

@router.post("/features")
def features(request: HistoryRequest) -> dict:
    jobs.fixture_guard(request)
    _,complete,metadata=build(request)
    latest=complete.iloc[-1] if len(complete) else None
    return {"dataset":metadata,"latest_feature_date":str(complete.index[-1]) if latest is not None else None,
            "latest_features":{k:safe_number(latest[k]) for k in FEATURES} if latest is not None else {}}

@router.post("/training/jobs",status_code=202,response_model=TrainingJobResponse)
def start_training(request: TrainingRequest) -> dict:return jobs.start(request)

@router.get("/training/jobs/{job_id}",response_model=TrainingJobResponse)
def training_status(job_id: str) -> dict:return jobs.state(job_id)

@router.post("/predict",response_model=InferenceResponse)
def inference(request: InferenceRequest) -> dict:
    jobs.fixture_guard(request)
    return predict(request)


from app.services.planning import PlanRequest,plan

@router.post("/data/plan")
def data_plan(request: PlanRequest) -> dict:return plan(request)
