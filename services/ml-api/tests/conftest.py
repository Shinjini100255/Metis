"""All generated prices are isolated test fixtures, never genuine market data."""
import math
import pytest
from app.services.quality import calendar

@pytest.fixture
def bars():
    sessions = calendar().sessions_in_range("2023-01-03", "2025-12-31")[:480]
    return [{"trading_date": str(d.date()), "open": f"{100 + i * .025 + 4 * math.sin(i * .73):.6f}",
             "close": f"{100 + i * .025 + 4 * math.sin(i * .73) + math.sin(i * .19):.6f}",
             "high": f"{103 + i * .025 + 4 * math.sin(i * .73):.6f}",
             "low": f"{97 + i * .025 + 4 * math.sin(i * .73):.6f}", "volume": 100000 + i * 71} for i, d in enumerate(sessions)]
