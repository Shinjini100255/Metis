"""Trusted internal training entry point, never a public shell interface."""
import json
import sys
from datetime import datetime,timezone
from app.schemas.analytics import TrainingRequest
from app.services import artifacts
from app.services.jobs import fixture_guard
from app.core.errors import DataProblem

def main(job_id):
    p=artifacts.directory(job_id)
    value=json.loads((p/"job.json").read_text(encoding="utf-8"))
    try:
        request=TrainingRequest.model_validate_json((p/"request.json").read_text(encoding="utf-8"))
        fixture_guard(request)
        from app.services.training import train
        result=train(request)
        artifacts.atomic_json(p/"result.json",result)
        value.update(status="SUCCEEDED",message="Models evaluated and artifacts saved.",result=result)
    except DataProblem as error:value.update(status="FAILED",code=error.code,message=error.message)
    except Exception:value.update(status="FAILED",code="TRAINING_FAILED",message="Training failed; validate features and class coverage. No models registered.")
    value["completed_at"]=datetime.now(timezone.utc).isoformat()
    artifacts.atomic_json(p/"job.json",value)

if __name__=="__main__":main(sys.argv[1])
