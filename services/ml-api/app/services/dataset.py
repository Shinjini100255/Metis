"""One causal feature schema; next observation labels, purged temporal partitions."""
import hashlib
import json
from app.core.errors import DataProblem
from app.services.quality import clean
from app.services.indicators import indicator_frame

SCHEMA = "daily-unadjusted-v1"
FEATURES = ["daily_return", "rolling_volatility20", "rsi14", "momentum5", "momentum10", "momentum20",
            "sma20_ratio", "sma50_ratio", "sma200_ratio", "ema12_ratio", "ema26_ratio", "macd_ratio",
            "macd_signal_ratio", "macd_histogram_ratio", "bollinger_width", "volume_ratio", "intraday_return", "range_ratio"]

def build(request):
    import numpy as np
    bars, quality = clean(request.bars)
    if quality["rejected"]:
        raise DataProblem("INVALID_DATA", "Clean rejected rows before training or inference; conflicting observations cannot be silently used.")
    if len(bars) < 201:
        raise DataProblem("INSUFFICIENT_HISTORY", "At least 201 closed trading observations are required after quality validation.")
    f = indicator_frame(bars)
    for key in ("sma20", "sma50", "sma200", "ema12", "ema26", "macd", "macd_signal", "macd_histogram"):
        f[key + "_ratio"] = f[key] / f.close - (0 if key.startswith("macd") else 1)
    f["bollinger_width"] = (f.bollinger_upper - f.bollinger_lower) / f.close
    f["volume_ratio"] = f.volume / f.volume_sma20.replace(0, np.nan)
    f["intraday_return"] = f.close / f.open - 1
    f["range_ratio"] = (f.high - f.low) / f.close
    f["target_return"] = f.close.shift(-1) / f.close - 1
    f["target_direction"] = (f.target_return > 0).astype(int)
    f["target_date"] = f.index.to_series().shift(-1)
    f = f.replace([np.inf, -np.inf], np.nan)
    complete = f.dropna(subset=FEATURES)
    labeled = complete.dropna(subset=["target_return", "target_date"])
    identity = {"schema": SCHEMA, "asset_id": request.asset_id, "symbol": request.symbol, "provider": request.provider,
                "price_basis": request.price_basis, "development_only": request.development_only, "bars": bars}
    dataset_id = hashlib.sha256(json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    metadata = {k: v for k, v in identity.items() if k != "bars"}
    metadata.update(dataset_id=dataset_id, input_rows=len(bars), labeled_rows=len(labeled), features=FEATURES,
                    feature_start=str(labeled.index[0]) if len(labeled) else None,
                    feature_end=str(labeled.index[-1]) if len(labeled) else None, quality=quality,
                    target="next available trading observation; direction=return>0", horizon=1)
    return labeled, complete, metadata

def partitions(frame, config):
    n = len(frame)
    holdout_start = n - config.holdout_size
    first = holdout_start - 1 - config.folds * config.test_window
    if first - 1 < config.train_window:
        required = 199 + config.holdout_size + config.folds * config.test_window + config.train_window + 3
        raise DataProblem("INSUFFICIENT_TRAINING_DATA", f"Configuration requires approximately {required} closed sessions; reduce bounded windows or import more local history.")
    folds = []
    for i in range(config.folds):
        start = first + i * config.test_window
        train = frame.iloc[start - config.train_window - 1:start - 1]
        test = frame.iloc[start:start + config.test_window]
        if str(train.target_date.iloc[-1]) >= str(test.index[0]):
            raise DataProblem("TEMPORAL_OVERLAP", "Training labels overlap the evaluation feature window.")
        folds.append((train, test))
    train, holdout = frame.iloc[:holdout_start - 1], frame.iloc[holdout_start:]
    assert str(train.target_date.iloc[-1]) < str(holdout.index[0])
    return folds, train, holdout
