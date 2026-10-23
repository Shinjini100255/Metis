import math
import numpy as np
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score, confusion_matrix, mean_absolute_error, mean_squared_error, r2_score

def classification(y, predicted, probability):
    auc = float(roc_auc_score(y,probability)) if len(set(y)) == 2 and probability is not None else None
    return {"accuracy": float(accuracy_score(y,predicted)), "directional_accuracy": float(accuracy_score(y,predicted)),
            "precision": float(precision_score(y,predicted,zero_division=0)), "recall": float(recall_score(y,predicted,zero_division=0)),
            "f1": float(f1_score(y,predicted,zero_division=0)), "roc_auc": auc,
            "confusion_matrix": confusion_matrix(y,predicted,labels=[0,1]).tolist(),
            "class_distribution": {"non_positive":int(np.sum(np.asarray(y)==0)),"positive":int(np.sum(np.asarray(y)==1))},"samples":len(y)}

def regression(y,predicted):
    r2 = float(r2_score(y,predicted)) if len(y)>1 and np.var(y)>0 else None
    return {"mae":float(mean_absolute_error(y,predicted)),"rmse":float(math.sqrt(mean_squared_error(y,predicted))),
            "r2":r2,"directional_accuracy":float(np.mean((np.asarray(y)>0)==(np.asarray(predicted)>0))),"samples":len(y)}
