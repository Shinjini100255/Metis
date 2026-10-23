"""US exchange-session validation. No network calls and no price imputation."""
from datetime import datetime, timezone
from functools import lru_cache
from pydantic import ValidationError
from app.schemas.analytics import Bar

@lru_cache(maxsize=1)
def calendar():
    import exchange_calendars as xcals
    return xcals.get_calendar("XNYS", start="2000-01-01", end=f"{datetime.now(timezone.utc).year + 2}-12-31")

def clean(rows: list[dict], now: datetime | None = None) -> tuple[list[dict], dict]:
    now = now or datetime.now(timezone.utc)
    cal = calendar()
    accepted, conflicted, rejected, skipped, input_dates = {}, set(), [], 0, []
    for index, row in enumerate(rows):
        try:
            b = Bar.model_validate(row)
            d = b.trading_date
            if not cal.is_session(str(d)):
                raise ValueError("NON_TRADING_SESSION")
            if cal.session_close(str(d)).to_pydatetime() > now:
                raise ValueError("SESSION_NOT_CLOSED")
            if b.high < max(b.open, b.close) or b.low > min(b.open, b.close) or b.high < b.low:
                raise ValueError("INCONSISTENT_OHLC")
            value = b.model_dump(mode="json")
            input_dates.append(str(d))
            if str(d) in conflicted:
                raise ValueError("CONFLICTING_DUPLICATE")
            if str(d) in accepted:
                if accepted[str(d)] == value:
                    skipped += 1
                    continue
                del accepted[str(d)]
                conflicted.add(str(d))
                rejected.append({"row": index, "reason": "CONFLICTING_DUPLICATE_ORIGINAL"})
                raise ValueError("CONFLICTING_DUPLICATE")
            accepted[str(d)] = value
        except ValidationError:
            rejected.append({"row": index, "reason": "MISSING_OR_INVALID_FIELDS"})
        except (ValueError, KeyError) as error:
            reason = str(error) if str(error) in {"NON_TRADING_SESSION", "SESSION_NOT_CLOSED", "INCONSISTENT_OHLC", "CONFLICTING_DUPLICATE"} else "OUTSIDE_SUPPORTED_CALENDAR"
            rejected.append({"row": index, "reason": reason})
    ordered = [accepted[key] for key in sorted(accepted)]
    missing = []
    if ordered:
        expected = calendar().sessions_in_range(ordered[0]["trading_date"], ordered[-1]["trading_date"])
        missing = [str(day.date()) for day in expected if str(day.date()) not in accepted]
    return ordered, {"input_rows": len(rows), "accepted": len(ordered), "skipped": skipped, "rejected": len(rejected), "issues": rejected[:50], "missing_sessions": len(missing), "missing_session_examples": missing[:10], "input_was_ordered": input_dates == sorted(input_dates), "sufficient_for_sma200": len(ordered) >= 200, "price_basis": "UNADJUSTED", "calendar": "XNYS", "imputed_prices": 0}
