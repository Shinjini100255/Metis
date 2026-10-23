"""Canonical indicator engine used by both Java charts and Python ML features."""
import math

def frame_from_bars(bars):
    import pandas as pd
    return pd.DataFrame(bars).set_index("trading_date").astype({k: "float64" for k in ["open", "high", "low", "close", "volume"]})

def ema(series, period):
    import numpy as np
    import pandas as pd
    out = pd.Series(np.nan, index=series.index, dtype=float)
    valid = series.dropna()
    if len(valid) < period:
        return out
    start = valid.index[period - 1]
    out.loc[start] = valid.iloc[:period].mean()
    last = float(out.loc[start])
    for key, value in valid.iloc[period:].items():
        last = (2 / (period + 1)) * float(value) + (1 - 2 / (period + 1)) * last
        out.loc[key] = last
    return out

def indicator_frame(bars):
    import numpy as np
    f = frame_from_bars(bars)
    close = f["close"]
    for n in (20, 50, 200):
        f[f"sma{n}"] = close.rolling(n, min_periods=n).mean()
    for n in (12, 26):
        f[f"ema{n}"] = ema(close, n)
    f["macd"] = f.ema12 - f.ema26
    f["macd_signal"] = ema(f.macd, 9)
    f["macd_histogram"] = f.macd - f.macd_signal
    f["bollinger_middle"] = f.sma20
    sd = close.rolling(20, min_periods=20).std(ddof=0)
    f["bollinger_upper"], f["bollinger_lower"] = f.sma20 + 2 * sd, f.sma20 - 2 * sd
    f["daily_return"] = close.pct_change(fill_method=None)
    f["rolling_volatility20"] = f.daily_return.rolling(20, min_periods=20).std(ddof=1) * np.sqrt(252)
    f["volume_sma20"] = f.volume.rolling(20, min_periods=20).mean()
    for n in (5, 10, 20):
        f[f"momentum{n}"] = close / close.shift(n) - 1
    f["rsi14"] = np.nan
    changes = close.diff()
    gain = loss = 0.0
    for i in range(1, len(f)):
        diff = changes.iloc[i]
        if i <= 14:
            gain += max(diff, 0) / 14
            loss += max(-diff, 0) / 14
        else:
            gain = (13 * gain + max(diff, 0)) / 14
            loss = (13 * loss + max(-diff, 0)) / 14
        if i >= 14:
            f.iloc[i, f.columns.get_loc("rsi14")] = (50 if gain == 0 else 100) if loss == 0 else 100 - 100 / (1 + gain / loss)
    return f

def safe_number(value):
    return float(value) if value is not None and math.isfinite(float(value)) else None

def calculate(bars):
    if not bars:
        return {"points": [], "observations": 0, "annualized_volatility": None, "sharpe_ratio": None, "maximum_drawdown": None, "methodology": METHODOLOGY}
    f = indicator_frame(bars)
    cols = [k for k in f.columns if k not in ["open", "high", "low", "close", "volume"]]
    points = [{"trading_date": str(index), **{k: safe_number(row[k]) for k in cols}} for index, row in f.iterrows()]
    returns = f.daily_return.dropna()
    sd = returns.std(ddof=1) if len(returns) >= 20 else float("nan")
    return {"points": points, "observations": len(f), "annualized_volatility": safe_number(sd * math.sqrt(252)), "sharpe_ratio": safe_number(returns.mean() / sd * math.sqrt(252)) if sd > 0 else None, "maximum_drawdown": safe_number((f.close / f.close.cummax() - 1).min()) if len(f) >= 2 else None, "methodology": METHODOLOGY}

METHODOLOGY = "Unadjusted OHLCV; SMA 20/50/200; SMA-seeded EMA 12/26; Wilder RSI 14; MACD 12-26 / signal EMA 9; Bollinger 20 ±2 population SD; simple daily returns; rolling sample volatility 20 ×√252; volume SMA 20; momentum 5/10/20; Sharpe risk-free rate 0. No split/dividend correction or price imputation."
