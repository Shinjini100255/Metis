import math
import statistics
import pytest
from app.services.quality import clean
from app.services.indicators import calculate

def test_cleaning_rejects_invalid_and_conflicting_without_imputing(bars):
    first = bars[0]
    rows = [*bars[:4], dict(first), {**bars[1], "close": "-1"}, {**bars[2], "volume": -1},
            {**first, "trading_date": "2023-01-07"}, {**bars[3], "close": "101"}, {}]
    accepted, q = clean(rows)
    assert q["skipped"] == 1 and q["rejected"] == 6
    assert len(accepted) == 3 and q["imputed_prices"] == 0
    assert bars[3]["trading_date"] not in [b["trading_date"] for b in accepted]

def test_order_missing_sessions_and_warmup(bars):
    accepted, q = clean([bars[2], bars[0]])
    assert not q["input_was_ordered"] and q["missing_sessions"] == 1
    assert accepted[0] == bars[0]
    assert calculate(accepted)["points"][-1]["sma200"] is None

def test_indicators_independently_calculated(bars):
    result = calculate(bars)
    p = result["points"][-1]
    c = [float(b["close"]) for b in bars]
    for n in (20, 50, 200):
        assert p[f"sma{n}"] == pytest.approx(sum(c[-n:]) / n)
    assert p["bollinger_upper"] == pytest.approx(statistics.mean(c[-20:]) + 2 * statistics.pstdev(c[-20:]))
    returns = [c[i] / c[i-1] - 1 for i in range(1, len(c))]
    assert p["rolling_volatility20"] == pytest.approx(statistics.stdev(returns[-20:]) * math.sqrt(252))
    for n in (5,10,20):
        assert p[f"momentum{n}"] == pytest.approx(c[-1] / c[-n-1] - 1)
    for n in (12,26):
        expected = sum(c[:n]) / n
        for value in c[n:]:
            expected = (value * 2 + expected * (n-1)) / (n+1)
        assert p[f"ema{n}"] == pytest.approx(expected)
    gains = sum(max(c[i]-c[i-1],0) for i in range(1,15))/14
    losses = sum(max(c[i-1]-c[i],0) for i in range(1,15))/14
    for i in range(15,len(c)):
        gains = (gains*13 + max(c[i]-c[i-1],0))/14
        losses = (losses*13 + max(c[i-1]-c[i],0))/14
    assert p["rsi14"] == pytest.approx(100-100/(1+gains/losses))
    assert p["volume_sma20"] == pytest.approx(statistics.mean(b["volume"] for b in bars[-20:]))
    assert p["macd"] == pytest.approx(p["ema12"]-p["ema26"])
    assert p["macd_histogram"] == pytest.approx(p["macd"]-p["macd_signal"])

def test_indicators_are_causal(bars):
    short = calculate(bars[:250])["points"][-1]
    assert short == calculate(bars)["points"][249]


def test_planning_uses_closed_sessions_and_local_cache_without_provider(bars):
    from app.services.planning import PlanRequest,plan
    p=plan(PlanRequest(start="2023-01-03",end="2023-01-06",existing=[b["trading_date"] for b in bars[:2]]))
    assert p["missing_sessions"]==2 and p["missing_ranges"]==[{"from":"2023-01-05","to":"2023-01-06"}]
    assert p["compact_missing_sessions"]==0 and p["older_missing_sessions"]==2
