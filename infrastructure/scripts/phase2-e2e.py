"""Run only against the isolated fixture Compose stack on port 15173.
Never imports fixtures or model artifacts into the main application.
"""
import json
import math
from pathlib import Path
import sys
import time
from urllib.request import Request,urlopen
from urllib.error import HTTPError
from datetime import datetime,timezone,timedelta
from uuid import uuid4
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/"services/ml-api"))
from app.services.quality import calendar
BASE="http://127.0.0.1:15173/api/v1"
CASE=ROOT/".cache/phase2-e2e.json"

def call(path,body=None,expected=200,headers=None):
    r=Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers={"Content-Type":"application/json",**(headers or {})})
    try:
        with urlopen(r,timeout=30) as response:
            assert response.status==expected,(path,response.status)
            return json.load(response)
    except HTTPError as error:
        content=error.read().decode("utf-8",errors="replace")
        try:result=json.loads(content)
        except ValueError:raise AssertionError((path,error.code,"Non-JSON proxy response; inspect the isolated proxy log.")) from None
        assert error.code==expected,(path,error.code,result)
        return result

def fixture():
    now=datetime.now(timezone.utc)
    sessions=[d for d in calendar().sessions_in_range((now-timedelta(days=900)).date().isoformat(),now.date().isoformat()) if calendar().session_close(d).to_pydatetime()<=now][-520:]
    bars=[]
    for i,d in enumerate(sessions):
        c=100+i*.025+4*math.sin(i*.73)
        bars.append(dict(trading_date=str(d.date()),open=f"{c:.6f}",high=f"{c+3:.6f}",low=f"{c-3:.6f}",close=f"{c+math.sin(i*.19):.6f}",volume=100000+i*71))
    return bars

def verify(case):
    asset=case["asset_id"]
    assert call(f"/assets/{asset}/prices?from={case['first_date']}&to={case['last_date']}&size=1260")["total"]==520
    models=[m for m in call("/models") if m["asset_id"]==asset]
    assert len(models)==5 and all(m["development_only"] for m in models)
    jobs=call("/training/jobs")
    assert next(j for j in jobs if j["id"]==case["job_id"])["status"]=="SUCCEEDED"
    predictions=call(f"/predictions?assetId={asset}")
    assert len(predictions)==2 and all(p["status"]=="DEVELOPMENT_ONLY" for p in predictions)
    for model in models:
        if model["metadata"]["selected"]:
            r=call("/predictions",{"asset_id":asset,"model_version_id":model["id"]})
            assert r["id"] in case["prediction_ids"]
    assert call("/ingestion/protection")["actual_requests"]==0
    print("PASS: 520 isolated fixture rows, five model artifacts, terminal job and two idempotent predictions persist; zero provider attempts.")

def main():
    protection=call("/ingestion/protection")
    assert protection.get("fixture_mode") is True and protection["live_enabled"] is False and protection["key_configured"] is False,"Refusing fixture work unless the isolated no-key test stack is active."
    assert all(call("/health")[k]["status"]=="UP" for k in ("spring","postgres","ml"))
    if "--verify-after-restart" in sys.argv:
        verify(json.loads(CASE.read_text()));return
    bars=fixture()
    asset=call("/assets",{"symbol":"T"+uuid4().hex[:8].upper(),"company_name":"ISOLATED DEVELOPMENT FIXTURE ONLY"},201)["id"]
    initial=call(f"/assets/{asset}/import",{"bars":bars[:480],"development_only":True})
    assert initial["records_inserted"]==480 and initial["records_rejected"]==0,initial
    repeated=call(f"/assets/{asset}/import",{"bars":bars[:480],"development_only":True})
    assert repeated["records_inserted"]==0 and repeated["records_skipped"]==480
    plan=call("/ingestion/plan",{"asset_ids":[asset,asset],"from":bars[0]["trading_date"],"to":bars[479]["trading_date"]})
    assert len(plan["items"])==1 and plan["external_requests_made"]==0
    assert call(f"/assets/{asset}/ingest",{},409,{"X-Confirm-Live-Ingestion":"true"})["code"]=="LIVE_INGESTION_DISABLED"
    indicators=call(f"/assets/{asset}/indicators?from={bars[0]['trading_date']}&to={bars[479]['trading_date']}")
    assert len(indicators["points"])==480 and indicators["points"][-1]["sma200"] is not None
    features=call(f"/assets/{asset}/features")
    assert features["dataset"]["labeled_rows"]==280
    job=call("/training/jobs",{"asset_id":asset,"configuration":{"trees":10}},202)
    assert job["status"]=="RUNNING",job
    deadline=time.monotonic()+330
    while time.monotonic()<deadline:
        rows=call("/training/jobs");state=next(j for j in rows if j["id"]==job["id"])
        assert call("/health")["ml"]["status"]=="UP"
        if state["status"] not in ("QUEUED","RUNNING"):break
        time.sleep(2)
    assert state["status"]=="SUCCEEDED",state
    models=[m for m in call("/models") if m["asset_id"]==asset]
    assert len(models)==5
    chosen=[m for m in models if m["metadata"]["selected"]]
    assert call("/predictions",{"asset_id":asset,"model_version_id":chosen[0]["id"]},409)["code"]=="NO_FORWARD_OBSERVATION"
    assert call(f"/assets/{asset}/import",{"bars":bars,"development_only":True})["records_inserted"]==40
    ids=[]
    for model in chosen:
        r=call("/predictions",{"asset_id":asset,"model_version_id":model["id"]})
        assert r["development_only"] is True and r["feature_date"]==bars[-1]["trading_date"]
        assert r["prediction_target_date"]>r["feature_date"]
        ids.append(r["id"])
    case=dict(asset_id=asset,job_id=job["id"],prediction_ids=ids,first_date=bars[0]["trading_date"],last_date=bars[-1]["trading_date"],development_only=True)
    CASE.parent.mkdir(exist_ok=True);CASE.write_text(json.dumps(case,indent=2))
    verify(case)
    print("PASS: PostgreSQL -> cleaning -> indicators -> features -> five trained/evaluated models -> persistent registry/artifacts -> actual inference -> Spring/Nginx. All values are development fixtures only.")

if __name__=="__main__":main()
