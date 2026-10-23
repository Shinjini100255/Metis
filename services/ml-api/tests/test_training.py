import json
from uuid import uuid4
import numpy as np
import pytest
from app.schemas.analytics import TrainingRequest,TrainingConfig
from app.services.training import train,pipeline
from app.services.dataset import build,partitions,FEATURES
from app.services import artifacts
from app.services.metrics import classification,regression
from app.core.errors import DataProblem

@pytest.fixture(scope="module")
def output(tmp_path_factory):
    # Module fixture performs one bounded training run; prices exist only in this temporary directory.
    from app.services.quality import calendar
    import math,os
    rows=[]
    sessions=calendar().sessions_in_range("2023-01-03","2025-12-31")[:480]
    for i,d in enumerate(sessions):
        c=100+i*.025+4*math.sin(i*.73)
        rows.append(dict(trading_date=str(d.date()),open=f"{c:.6f}",high=f"{c+3:.6f}",low=f"{c-3:.6f}",close=f"{c+math.sin(i*.19):.6f}",volume=100000+i*71))
    path=tmp_path_factory.mktemp("isolated-fixture-models")
    old=os.environ.get("METIS_ARTIFACT_DIR");os.environ["METIS_ARTIFACT_DIR"]=str(path)
    request=TrainingRequest(asset_id=1,symbol="AAPL",provider="TEST_FIXTURE",development_only=True,bars=rows,job_id=str(uuid4()),configuration=TrainingConfig(trees=10))
    result=train(request)
    yield result,request,path
    if old is None:os.environ.pop("METIS_ARTIFACT_DIR",None)
    else:os.environ["METIS_ARTIFACT_DIR"]=old

def test_all_families_and_baselines_evaluated(output):
    result,req,_=output
    assert len(result["models"])==5
    assert set(m["algorithm"] for m in result["models"])=={"logistic_regression","random_forest","xgboost"}
    for m in result["models"]:
        assert m["development_only"] and len(m["folds"])==3
        assert m["holdout"]["metrics"]["samples"]==40
        assert m["holdout"]["training_label_end"] < m["holdout"]["test_start"]
        assert all(s["test_label_end"] < m["holdout"]["test_start"] for s in m["folds"])
        assert all(s["baselines"] for s in m["folds"])
        assert m["hyperparameters"]["seed"]==17

def test_safe_artifact_roundtrip_and_determinism(output):
    result,req,_=output
    frame,_,_=build(req);_,tr,te=partitions(frame,req.configuration)
    for m in result["models"]:
        loaded,manifest=artifacts.load(m["artifact_id"])
        independent=pipeline(m["algorithm"],m["task"],req.configuration)
        independent.fit(tr[FEATURES],tr["target_direction" if m["task"]=="classification" else "target_return"])
        np.testing.assert_allclose(loaded.predict(te[FEATURES]),independent.predict(te[FEATURES]),rtol=1e-6,atol=1e-9)
        # Scaler means are from the training partition, never the holdout.
        supported=loaded[0].get_support()
        np.testing.assert_allclose(loaded[1].mean_,tr[FEATURES].to_numpy()[:,supported].mean(axis=0))

def test_corruption_and_arbitrary_paths_rejected(output):
    result,_,path=output
    with pytest.raises(DataProblem):artifacts.load("../../secret")
    m=result["models"][0]
    f=path/m["artifact_id"]/"pipeline.skops"
    original=f.read_bytes();f.write_bytes(b"corrupt")
    with pytest.raises(DataProblem,match="checksum"):artifacts.load(m["artifact_id"])
    f.write_bytes(original)

def test_undefined_metrics_are_safe():
    assert classification([1,1],[1,1],[.7,.8])["roc_auc"] is None
    assert regression([0,0],[0,0])["r2"] is None
