"""Bounded sequential CPU training; final holdout never influences selection."""
import time
from datetime import datetime,timezone
from uuid import uuid4
import numpy as np
from sklearn.pipeline import Pipeline
from sklearn.feature_selection import VarianceThreshold
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier,RandomForestRegressor
from xgboost import XGBClassifier,XGBRegressor
from threadpoolctl import threadpool_limits
from app.services.dataset import build,partitions,FEATURES
from app.services.metrics import classification,regression
from app.services import artifacts
from app.core.errors import DataProblem

SPECS=[("logistic_regression","classification"),("random_forest","classification"),("random_forest","regression"),("xgboost","classification"),("xgboost","regression")]

def pipeline(name,task,c):
    if name=="logistic_regression":model=LogisticRegression(max_iter=500,random_state=c.seed,C=1.0)
    elif name=="random_forest":
        cls=RandomForestClassifier if task=="classification" else RandomForestRegressor
        model=cls(n_estimators=c.trees,max_depth=c.max_depth,min_samples_leaf=4,n_jobs=2,random_state=c.seed)
    else:
        cls=XGBClassifier if task=="classification" else XGBRegressor
        model=cls(n_estimators=c.trees,max_depth=c.max_depth,learning_rate=.05,n_jobs=2,random_state=c.seed,tree_method="hist",subsample=1,colsample_bytree=1,verbosity=0)
    return Pipeline([("constant_columns",VarianceThreshold()),("scaler",StandardScaler()),("model",model)])

def evaluate(model,task,train,test):
    ycol="target_direction" if task=="classification" else "target_return"
    start=time.perf_counter();model.fit(train[FEATURES],train[ycol]);fit=time.perf_counter()-start
    start=time.perf_counter();pred=model.predict(test[FEATURES]);inference=time.perf_counter()-start
    if task=="classification":
        result=classification(test[ycol],pred,model.predict_proba(test[FEATURES])[:,1])
        majority=int(train[ycol].mean()>.5)
        baseline={"majority":classification(test[ycol],np.full(len(test),majority),np.full(len(test),train[ycol].mean())),
                  "previous_direction":classification(test[ycol],(test.daily_return>0).astype(int),None)}
    else:
        result=regression(test[ycol],pred);baseline={"zero_return":regression(test[ycol],np.zeros(len(test)))}
    return {"metrics":result,"baselines":baseline,"train_start":str(train.index[0]),"train_end":str(train.index[-1]),
            "training_label_end":str(train.target_date.iloc[-1]),"train_samples":len(train),"test_start":str(test.index[0]),
            "test_end":str(test.index[-1]),"test_label_end":str(test.target_date.iloc[-1]),"fit_seconds":fit,"inference_seconds":inference}

def train(request):
    frame,_,dataset=build(request)
    folds,final_train,holdout=partitions(frame,request.configuration)
    if final_train.target_direction.nunique()<2 or any(t.target_direction.nunique()<2 for t,_ in folds):
        raise DataProblem("SINGLE_CLASS_TRAINING","Each classification training window must contain both target classes.")
    reports=[]
    with threadpool_limits(limits=2):
        for name,task in SPECS:
            scores=[evaluate(pipeline(name,task,request.configuration),task,tr,te) for tr,te in folds]
            key="accuracy" if task=="classification" else "rmse"
            values=[s["metrics"][key] for s in scores]
            baselines=[s["baselines"]["majority" if task=="classification" else "zero_return"][key] for s in scores]
            reports.append({"algorithm":name,"task":task,"folds":scores,"cv_mean":float(np.mean(values)),"cv_std":float(np.std(values)),
                            "cv_aggregate":{k:{"mean":float(np.mean([s["metrics"][k] for s in scores if s["metrics"].get(k) is not None])),"std":float(np.std([s["metrics"][k] for s in scores if s["metrics"].get(k) is not None]))} for k,v in scores[0]["metrics"].items() if isinstance(v,(float,int)) and k!="samples"},
                            "selection_metric":key,"baseline_improvement":float(np.mean(values)-np.mean(baselines))*(1 if task=="classification" else -1),
                            "cv_fit_seconds":sum(s["fit_seconds"] for s in scores)})
        # Lower variance and simpler family break performance ties; no holdout information used.
        chosen={}
        for task in ("classification","regression"):
            candidates=[r for r in reports if r["task"]==task]
            chosen[task]=min(candidates,key=lambda r:((-r["cv_mean"] if task=="classification" else r["cv_mean"]),r["cv_std"], ["logistic_regression","random_forest","xgboost"].index(r["algorithm"])))["algorithm"]
        models=[]
        for r in reports:
            name,task=r["algorithm"],r["task"]
            model=pipeline(name,task,request.configuration)
            final=evaluate(model,task,final_train,holdout)
            model_id=str(uuid4())
            metadata={**r,"dataset":dataset,"asset_id":request.asset_id,"symbol":request.symbol,"provider":request.provider,
                      "development_only":request.development_only,"created_at":datetime.now(timezone.utc).isoformat(),
                      "training_job_id":request.job_id,"hyperparameters":request.configuration.model_dump(),"holdout":final,
                      "selected":name==chosen[task],"training_label_end":str(final_train.target_date.iloc[-1]),
                      "training_feature_start":str(final_train.index[0]),"training_feature_end":str(final_train.index[-1]),
                      "evaluation_label_end":str(holdout.target_date.iloc[-1]),"constant_features":[FEATURES[i] for i,ok in enumerate(model[0].get_support()) if not ok],
                      "complexity":{"trees":0 if name=="logistic_regression" else request.configuration.trees,"max_depth":None if name=="logistic_regression" else request.configuration.max_depth},
                      "interpretation":"Development fixture only; no financial evidence." if request.development_only else "Historical out-of-sample evaluation; no profitability or calibrated-probability claim."}
            models.append(artifacts.save(model_id,model,metadata,name=="xgboost"))
    return {"dataset":dataset,"models":models,"selected":chosen,"development_only":request.development_only}
