"""One subprocess at a time; bounded runtime; snapshots and status survive restarts."""
import json
import os
import subprocess
import sys
import threading
from datetime import datetime,timezone
from app.core.errors import DataProblem
from app.services import artifacts
from app.services.dataset import build,partitions

_lock=threading.Lock()
_active=None

def timestamp():return datetime.now(timezone.utc).isoformat()

def fixture_guard(request):
    fixture=request.development_only or request.provider=="TEST_FIXTURE"
    if fixture and (not request.development_only or request.provider!="TEST_FIXTURE" or os.getenv("METIS_ALLOW_FIXTURES","false").lower()!="true"):
        raise DataProblem("FIXTURE_DISABLED","Fixtures require explicit provenance and an isolated test service.",403)

def state(job_id):
    p=artifacts.directory(job_id)/"job.json"
    try:return json.loads(p.read_text(encoding="utf-8"))
    except (OSError,ValueError):raise DataProblem("JOB_NOT_FOUND","Training job was not found.",404)

def recover():
    for p in artifacts.root().glob("*/job.json"):
        try:
            value=json.loads(p.read_text(encoding="utf-8"))
            if value.get("status") in ("RUNNING","QUEUED"):
                value.update(status="INTERRUPTED",completed_at=timestamp(),message="Service restarted; rerun explicitly using the preserved local dataset.")
                artifacts.atomic_json(p,value)
        except (OSError,ValueError):continue

def run(job_id):
    global _active
    p=artifacts.directory(job_id)
    value=state(job_id)
    try:
        env=dict(os.environ,OMP_NUM_THREADS="2",OPENBLAS_NUM_THREADS="2",MKL_NUM_THREADS="2")
        # No shell, no user command/path. Worker receives only a canonical UUID.
        proc=subprocess.Popen([sys.executable,"-m","app.services.worker",job_id],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:code=proc.wait(timeout=min(600,max(30,int(os.getenv("METIS_TRAINING_TIMEOUT_SECONDS","300")))))
        except subprocess.TimeoutExpired:
            proc.kill();proc.wait();value.update(status="TIMED_OUT",message="Bounded training timeout reached.",completed_at=timestamp());artifacts.atomic_json(p/"job.json",value);return
        if code!=0 or state(job_id)["status"]=="RUNNING":
            value.update(status="FAILED",message="Training worker exited unexpectedly; no model was registered.",completed_at=timestamp());artifacts.atomic_json(p/"job.json",value)
    except Exception:
        value.update(status="FAILED",message="Training worker could not start.",completed_at=timestamp());artifacts.atomic_json(p/"job.json",value)
    finally:
        _active=None;_lock.release()

def start(request):
    global _active
    fixture_guard(request)
    # Validate before occupying the single training slot; this never invokes a provider.
    frame,_,_=build(request);partitions(frame,request.configuration)
    try:return state(request.job_id)
    except DataProblem as error:
        if error.code!="JOB_NOT_FOUND":raise
    if not _lock.acquire(blocking=False):raise DataProblem("TRAINING_BUSY","One training job is already active.",409)
    try:
        p=artifacts.directory(request.job_id,True)
        artifacts.atomic_json(p/"request.json",request.model_dump(mode="json"))
        value={"job_id":request.job_id,"status":"RUNNING","started_at":timestamp(),"development_only":request.development_only,"message":"Sequential CPU training in progress."}
        artifacts.atomic_json(p/"job.json",value)
        _active=request.job_id
        threading.Thread(target=run,args=(request.job_id,),daemon=True).start()
        return value
    except Exception:
        _lock.release();raise
