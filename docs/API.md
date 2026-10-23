# API contracts

Java base: `/api/v1`. OpenAPI: `/v3/api-docs`; Swagger: `/swagger-ui/index.html`. JSON fields use snake_case. Timestamps are UTC ISO-8601 and dates are `YYYY-MM-DD`. Monetary values are decimal JSON numbers (arithmetic is performed server-side).

Errors use `{ "code": "VALIDATION_ERROR", "message": "...", "timestamp": "..." }`. Statuses: 400 invalid fields/ranges, 404 missing resources, 409 integrity conflicts, 429 ingestion interval/budget/concurrency limits, 503 missing provider configuration; unexpected failures return a safe 500 without a trace. The ingestion endpoint reports provider failures in a persisted run and returns `{status: "FAILED", records_inserted: 0, message}`; the UI displays that message. A successful cache hit reports `CACHED` without calling the provider.

## Main application

| Method | Path | Behavior |
|---|---|---|
| GET | /health | Actual Spring, DB, Python and provider state (provider is last-request based) |
| GET | /assets | Asset page; `q`, `page` (default 0), `size` (default 20, max 100) |
| GET | /assets/search?q= | Same paginated query by symbol/company |
| POST | /assets | Register a US ticker/reference name; existing symbol returns its existing asset |
| GET | /assets/{id} | Asset metadata |
| GET | /assets/{id}/prices | `from`, `to`, `page`, `size` (default 100, max 1260); ascending historical page |
| GET | /assets/{id}/indicators | `from`, `to`; points plus annualized volatility, Sharpe and maximum drawdown |
| POST | /assets/{id}/ingest | Manual rate-limited compact daily fetch into PostgreSQL |
| GET | /quotes | Up to 100 cached asset summaries, latest close/date/volume and last-session percentage change |
| GET | /watchlists | Up to 100 watchlists with at most 100 assets each |
| POST | /watchlists | Create named watchlist |
| POST | /watchlists/{id}/assets | Add existing asset, idempotent below capacity |
| DELETE | /watchlists/{id}/assets/{assetId} | Remove membership |
| GET | /portfolios | Up to 100 portfolio references |
| POST | /portfolios | Create paper portfolio |
| GET | /portfolios/{id} | Holdings and completeness-aware valuation |
| DELETE | /portfolios/{id} | Delete paper portfolio and its positions |
| POST | /portfolios/{id}/holdings | Create one position; duplicate asset returns conflict |
| PUT | /portfolios/{id}/holdings/{holdingId} | Replace asset/quantity/average cost of that portfolio's position |
| DELETE | /portfolios/{id}/holdings/{holdingId} | Remove that portfolio's position |
| GET | /predictions | Actual stored records; optional `assetId`, bounded `page`/`size`; initially `[]` |
| GET | /models | Actual registry metadata, max 100; initially `[]`; artifact references are not exposed |
| GET | /models/status | Typed FastAPI status through internal HTTP, or explicit UNAVAILABLE |
| GET | /models/operations | Explicit empty training/evaluation collections and NOT_IMPLEMENTED promotion/deployment status |
| GET | /strategies | Reference definitions/configuration; readiness NOT_EXECUTABLE |
| GET | /backtests | Up to 100 actual stored runs; initially `[]` |
| GET | /system/status | Operational health, measured DB/Python response times, provider last-request state, last successful ingestion |
| GET | /system/ingestion | Most recent 50 runs, with symbol/provider/status/error |

Asset and price pages use `{items: [], total: 0, page: 0, size: 20}`. Page numbers are non-negative and capped at 10,000. Historical ranges are bounded to five calendar years and 1,260 sessions; empty data remains empty. Other bounded collections return arrays. No write contract exists for training, model promotion, prediction execution or backtest execution.

## Mutation examples

```json
// POST /assets: US symbols such as AAPL or BRK.B, max 10 base characters plus optional class suffix
{"symbol":"COST","company_name":"Costco Wholesale Corporation"}

// POST /watchlists
{"name":"Research universe"}

// POST /watchlists/1/assets
{"asset_id":1}

// POST /portfolios
{"name":"Long-term paper","base_currency":"USD"}

// POST or PUT /portfolios/1/holdings[/holdingId]
{"asset_id":1,"quantity":"2.500000","average_purchase_price":"100.100000"}
```

Portfolio base currencies currently allow USD/INR for completeness-aware mismatch handling; INR does not enable Indian prices or FX. Quantities/costs must be positive, at most 1 trillion and have at most six decimal places. Blank/oversized names and invalid identifiers are rejected. Mutation creates return 201 for asset/watchlist/portfolio, holding/member updates return 204. Strings are accepted for incoming decimals to preserve client input precision.

A portfolio returns `invested_capital`, `market_value`, `unrealized_pl`, `known_value`, `unvalued_positions`, `currency_mismatches`, and per-position `price_date`, `currency`, and `valuation_status`. Missing values are null rather than fabricated zeros. See architecture for the exact aggregation rules.

## Python service

| Method | Path | Contract |
|---|---|---|
| GET | /health | UP, service name, version |
| GET | /models | `[]` until a real registry is implemented |
| GET | /model-status | `available:false`, `status:NOT_TRAINED`, null version and explanation |
| GET | /docs | Interactive OpenAPI |
| GET | /openapi.json | OpenAPI schema |

Version-1 prediction request/response Pydantic schemas are defined in `app/schemas/prediction.py`, including symbol validation, model version, target date, value, bounded probability and timestamp. They are future contract types, not an advertised inference endpoint. No trained model, randomized prediction, or fake experiment is present.
