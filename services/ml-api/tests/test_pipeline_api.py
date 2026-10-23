"""Subprocess, persisted artifacts and inference with isolated, explicitly labeled fixtures."""
from datetime import datetime,timezone,timedelta
import json
import time
from uuid import uuid4
import math
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.services import artifacts,jobs
from app.services.quality import calendar
from app.schemas.analytics import InferenceRequest
from app.services.inference import predict
from app.core.errors import DataProblem

def recent_fixture():
    now=datetime.now(timezone.utc)
    sessions=[d for d in calendar().sessions_in_range((now-timedelta(days=900)).date().isoformat(),now.date().isoformat()) if calendar().session_close(d).to_pydatetime()<=now][-520:]
    rows=[]
    for i,d in enumerate(sessions):
        c=100+i*.025+4*math.sin(i*.73)
        rows.append(dict(trading_date=str(d.date()),open=f"{c:.6f}",high=f"{c+3:.6f}",low=f"{c-3:.6f}",close=f"{c+math.sin(i*.19):.6f}",volume=100000+i*71))
    return rows

def test_isolated_job_and_forward_inference_survive_restart(tmp_path,monkeypatch):
    monkeypatch.setenv("METIS_ARTIFACT_DIR",str(tmp_path));monkeypatch.setenv("METIS_ALLOW_FIXTURES","true")
    rows=recent_fixture();job_id=str(uuid4())
    payload=dict(asset_id=1,symbol="AAPL",provider="TEST_FIXTURE",development_only=True,bars=rows[:480],job_id=job_id,configuration=dict(trees=10))
    with TestClient(app) as client:
        assert client.post("/training/jobs",json=payload).status_code==202
        # A second job cannot enter the occupied single-worker slot.
        second=client.post("/training/jobs",json={**payload,"job_id":str(uuid4())})
        assert second.status_code==409
        assert client.get("/health").json()["status"]=="UP"
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            state=client.get(f"/training/jobs/{job_id}").json()
            if state["status"]!="RUNNING":break
            time.sleep(.2)
        assert state["status"]=="SUCCEEDED",state
        assert len(client.get("/models").json())==5
        model=next(m for m in state["result"]["models"] if m["task"]=="classification" and m["selected"])
        inference={k:v for k,v in payload.items() if k not in ("job_id","configuration")}
        inference.update(artifact_id=model["artifact_id"],bars=rows)
        r=client.post("/predict",json=inference)
        assert r.status_code==200,r.text
        prediction=r.json()
        assert prediction["status"]=="DEVELOPMENT_ONLY" and 0<=prediction["probability"]<=1
        assert prediction["feature_date"]==rows[-1]["trading_date"]
        assert prediction["target_date"]>prediction["feature_date"]
        old=client.post("/predict",json={**inference,"bars":rows[:480]})
        assert old.status_code==409 and old.json()["code"]=="NO_FORWARD_OBSERVATION"
        with pytest.raises(DataProblem,match="seven calendar days"):
            predict(InferenceRequest.model_validate(inference),datetime.now(timezone.utc)+timedelta(days=10))
    # A new app lifespan reads the same artifacts and terminal job without retraining.
    with TestClient(app) as restarted:
        assert restarted.get(f"/training/jobs/{job_id}").json()["status"]=="SUCCEEDED"
        assert restarted.get("/model-status").json()["available"]
        assert restarted.post("/predict",json=inference).json()["predicted_value"]==prediction["predicted_value"]
    monkeypatch.setenv("METIS_ALLOW_FIXTURES","false")
    with TestClient(app) as production:
        assert production.get("/models").json()==[]
        assert production.post("/predict",json=inference).status_code==403

def test_interrupted_job_recovery_and_invalid_ids(tmp_path,monkeypatch):
    monkeypatch.setenv("METIS_ARTIFACT_DIR",str(tmp_path))
    value=str(uuid4());p=artifacts.directory(value,True)
    artifacts.atomic_json(p/"job.json",dict(job_id=value,status="RUNNING"))
    jobs.recover()
    assert jobs.state(value)["status"]=="INTERRUPTED"
    with TestClient(app) as client:
        assert client.get("/training/jobs/not-a-uuid").status_code==400
        assert client.get("/training/jobs/"+str(uuid4())).status_code==404
