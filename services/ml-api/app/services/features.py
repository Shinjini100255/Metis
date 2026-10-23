"""Small, deterministic feature seam for future training; imported only when used."""


def daily_returns(closes: list[float]) -> list[float]:
    if any(value <= 0 for value in closes):
        raise ValueError("Closing prices must be positive.")
    return [closes[i] / closes[i - 1] - 1 for i in range(1, len(closes))]
