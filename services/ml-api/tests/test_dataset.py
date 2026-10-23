import pytest
from app.schemas.analytics import HistoryRequest,TrainingConfig
from app.services.dataset import build,partitions,FEATURES
from app.core.errors import DataProblem

def request(bars):
    return HistoryRequest(asset_id=1,symbol="AAPL",provider="TEST_FIXTURE",development_only=True,bars=bars)

def test_labels_dates_and_reproducibility(bars):
    f, full, meta = build(request(bars))
    assert f.index[0] == bars[199]["trading_date"]
    expected = float(bars[200]["close"]) / float(bars[199]["close"]) - 1
    assert f.target_return.iloc[0] == pytest.approx(expected)
    assert f.target_direction.iloc[0] == int(expected > 0)
    assert f.target_date.iloc[0] == bars[200]["trading_date"]
    assert bars[-1]["trading_date"] not in f.index
    assert full.index[-1] == bars[-1]["trading_date"]
    assert meta["dataset_id"] == build(request(list(reversed(bars))))[2]["dataset_id"]

def test_no_future_inputs_and_purged_splits(bars):
    f, full, _ = build(request(bars))
    _, short, _ = build(request(bars[:300]))
    assert full.loc[short.index,FEATURES].equals(short[FEATURES])
    folds, train, holdout = partitions(f,TrainingConfig())
    for tr, te in folds:
        assert tr.target_date.iloc[-1] < te.index[0]
        assert te.target_date.iloc[-1] < holdout.index[0]
        assert len(tr)==160 and len(te)==20
    assert train.target_date.iloc[-1] < holdout.index[0]
    assert len(holdout)==40

def test_sufficiency_and_zero_return_labels(bars):
    with pytest.raises(DataProblem,match="201"):
        build(request(bars[:100]))
    f,_,_=build(request(bars[:250]))
    with pytest.raises(DataProblem,match="Configuration requires"):
        partitions(f,TrainingConfig())
    equal=[{**b,"open":"100","high":"100","low":"100","close":"100"} for b in bars]
    f,_,_=build(request(equal))
    assert set(f.target_direction)=={0} and set(f.target_return)=={0}
