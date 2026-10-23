"""Only server-created UUID paths. Safe skops/native JSON; checksums and versions."""
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
from uuid import UUID
from app.core.errors import DataProblem
from app.services.dataset import SCHEMA,FEATURES

def root():
    p=Path(os.getenv("METIS_ARTIFACT_DIR",".artifacts")).resolve()
    p.mkdir(parents=True,exist_ok=True)
    return p

def identifier(value):
    try:
        if str(UUID(value)) != value: raise ValueError()
    except (ValueError,TypeError,AttributeError):
        raise DataProblem("INVALID_ARTIFACT_ID","A canonical model identifier is required.",400)
    return value

def directory(value,create=False):
    p=root()/identifier(value)
    if p.is_symlink() or p.resolve().parent != root():
        raise DataProblem("INVALID_ARTIFACT_PATH","Artifact path is not permitted.",400)
    if create:p.mkdir(exist_ok=True)
    return p

def versions():
    return {key:importlib.metadata.version(key) for key in ("scikit-learn","xgboost-cpu","skops","numpy","pandas")}

def atomic_json(path,value):
    temporary=path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value,sort_keys=True,allow_nan=False),encoding="utf-8")
    temporary.replace(path)

def manifest(model_id):
    try:
        p=directory(model_id)
        m=json.loads((p/"manifest.json").read_text(encoding="utf-8"))
        if m["schema"]!=SCHEMA or m["features"]!=FEATURES or m["library_versions"]!=versions():
            raise DataProblem("ARTIFACT_INCOMPATIBLE","Artifact versions or feature schema differ from the running service.",409)
        for name,digest in m["checksums"].items():
            if name not in {"pipeline.skops","preprocessor.skops","model.json"}:raise ValueError()
            f=p/name
            if f.is_symlink() or hashlib.sha256(f.read_bytes()).hexdigest()!=digest:
                raise DataProblem("ARTIFACT_CORRUPT","Artifact checksum failed; inference is unavailable.",503)
        return m
    except DataProblem:raise
    except Exception:raise DataProblem("ARTIFACT_UNAVAILABLE","Model artifact is missing or unreadable.",503)

def save(model_id,model,metadata,native=False):
    import skops.io as sio
    p=directory(model_id,True)
    if native:
        sio.dump(model[:-1],p/"preprocessor.skops")
        model[-1].save_model(p/"model.json")
        names=["preprocessor.skops","model.json"]
    else:
        sio.dump(model,p/"pipeline.skops")
        names=["pipeline.skops"]
    m={**metadata,"artifact_id":model_id,"schema":SCHEMA,"features":FEATURES,"library_versions":versions(),
       "format":"xgboost-json" if native else "skops","checksums":{n:hashlib.sha256((p/n).read_bytes()).hexdigest() for n in names}}
    atomic_json(p/"manifest.json",m)
    return m

def load(model_id):
    import skops.io as sio
    m=manifest(model_id)
    p=directory(model_id)
    try:
        if m["format"]=="skops":
            return sio.load(p/"pipeline.skops",trusted=[]),m
        from sklearn.pipeline import Pipeline
        from xgboost import XGBClassifier,XGBRegressor
        pre=sio.load(p/"preprocessor.skops",trusted=[])
        model=XGBClassifier(n_jobs=2) if m["task"]=="classification" else XGBRegressor(n_jobs=2)
        model.load_model(p/"model.json")
        model.set_params(n_jobs=2)
        return Pipeline([*pre.steps,("model",model)]),m
    except Exception:raise DataProblem("ARTIFACT_UNSAFE","Artifact could not be safely loaded without trusted custom types.",503)

def list_manifests():
    result=[]
    for p in sorted(root().glob("*/manifest.json")):
        try:
            m=json.loads(p.read_text(encoding="utf-8"))
            job_file=root()/m["training_job_id"]/"job.json"
            if not job_file.exists() or json.loads(job_file.read_text(encoding="utf-8")).get("status")!="SUCCEEDED":continue
            if m.get("development_only") and os.getenv("METIS_ALLOW_FIXTURES","false").lower()!="true":continue
            result.append(m)
        except (OSError,ValueError):continue
    return sorted(result,key=lambda m:m["created_at"],reverse=True)
