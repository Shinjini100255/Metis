from datetime import datetime,timezone
import os
from app.core.errors import DataProblem
from app.services import artifacts
from app.services.dataset import build,FEATURES
from app.services.quality import calendar

def predict(request,now=None):
    now=now or datetime.now(timezone.utc)
    model,m=artifacts.load(request.artifact_id)
    if m["development_only"] and os.getenv("METIS_ALLOW_FIXTURES","false").lower()!="true":
        raise DataProblem("FIXTURE_DISABLED","Development artifacts cannot serve production predictions.",403)
    if (m["asset_id"],m["symbol"],m["provider"],m["development_only"]) != (request.asset_id,request.symbol,request.provider,request.development_only):
        raise DataProblem("MODEL_SERIES_MISMATCH","Select a model trained for this asset, provider, and data provenance.",409)
    _,features,_=build(request)
    latest=max(b["trading_date"] for b in request.bars)
    if features.empty or str(features.index[-1])!=latest:
        raise DataProblem("FEATURES_UNAVAILABLE","Latest observation has incomplete features.")
    if latest <= m["evaluation_label_end"]:
        raise DataProblem("NO_FORWARD_OBSERVATION","A genuine forward prediction needs a closed feature session after the final evaluation period.",409)
    closed=calendar().session_close(latest).to_pydatetime()
    if closed>now:raise DataProblem("SESSION_NOT_CLOSED","Daily close features are not yet available.",409)
    age=(now-closed).total_seconds()
    if age>7*86400:raise DataProblem("STALE_FEATURES","Latest feature session is more than seven calendar days old; import fresh local observations.",409)
    target=str(calendar().next_session(latest).date())
    # Never label an already observed target as a newly generated live prediction.
    if calendar().session_close(target).to_pydatetime()<=now:
        raise DataProblem("TARGET_ALREADY_CLOSED","The target session has already closed. Import the latest closed session before inference.",409)
    x=features.loc[[latest],FEATURES]
    value=float(model.predict(x)[0])
    probability=float(model.predict_proba(x)[0,1]) if m["task"]=="classification" else None
    return {"artifact_id":m["artifact_id"],"task":m["task"],"predicted_value":value,"probability":probability,
            "predicted_direction":int(value) if m["task"]=="classification" else int(value>0),
            "predicted_return":value if m["task"]=="regression" else None,"feature_date":latest,
            "feature_timestamp":closed.isoformat(),"created_at":now.isoformat(),"target_date":target,
            "target_horizon":"NEXT_XNYS_SESSION","data_age_seconds":int(age),"status":"DEVELOPMENT_ONLY" if m["development_only"] else "FORWARD",
            "development_only":m["development_only"],"evaluation":m["holdout"]["metrics"]}
