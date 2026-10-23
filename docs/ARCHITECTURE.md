# Architecture

## Responsibility boundaries

The browser talks only to `/api/v1` on its current origin. Production frontend Nginx proxies to Spring; Vite proxies to the native backend. Spring controllers validate transport input; services own application behavior; `AssetRepository` provides JPA asset queries; `Store` provides parameterized JDBC for joins, transactions and PostgreSQL-specific ingestion. JPA schema generation is disabled. Flyway owns all schema changes.

PostgreSQL stores reference assets, watchlists, paper holdings, daily prices and ingestion provenance. The Python service has API, configuration, versioned schemas, feature, registry and prediction boundaries. It loads no models and imports no large analytics libraries at startup. The prediction service seam explicitly raises `ModelUnavailableError`; no prediction execution route is advertised.

## Provider interface

`MarketDataProvider` supplies typed decimal OHLCV bars and a provider identity. `AlphaVantageProvider` implements manual compact daily data retrieval, 2–30 second configurable request/connect timeout, and 0–1 retries for transport errors. HTTP/provider payload failures are not automatically retried. Only US equity registration is exposed; a non-US provider timezone is rejected when supplied. No NSE/BSE capability is claimed.

An ingestion request obtains a transaction-scoped PostgreSQL advisory lock. Concurrent requests are rejected rather than queued. Before any provider request, it checks per-asset successful-cache age, global UTC daily budget, and minimum interval. Retry configuration reserves worst-case attempts against the budget. Failures are stored in ingestion runs with sanitized messages. Successful bars append with a unique `(asset_id, trading_date, provider)` key; duplicate observations are ignored. No scheduler or startup downloader is enabled.

Holding a connection and advisory lock across this bounded network call intentionally serializes ingestion for this local resource budget. A future task queue can replace this without changing the provider interface or price contracts. An externally killed process can roll back its run; successful ingestion timestamps are not invented.

The adapter does not correct past bars, corporate actions, or gaps outside the latest 100 sessions. A later provider may supply adjusted close and reliable source timestamps. Until then both are null; the actual exchange trading date and UTC ingestion time are retained.

## Analytics conventions

All indicators are calculated by Spring from the exact ascending price series used by charts. The default query spans one year; price/indicator queries allow at most five calendar years and return at most 1,260 sessions.

- Daily return: `close[t] / close[t-1] - 1`; first observation is unavailable.
- SMA 20/50: arithmetic mean including the current close; unavailable before the full window.
- RSI 14: initial 14-session mean gain/loss, then Wilder smoothing. Flat gains/losses give 50; gains without losses give 100.
- Annualized volatility: sample standard deviation of simple daily returns × √252; minimum 20 returns.
- Sharpe: mean daily return / sample standard deviation × √252, assumed risk-free rate zero; unavailable for zero variance or fewer than 20 returns.
- Maximum drawdown: minimum `close / running_peak - 1`; minimum two observations.

These are descriptive asset statistics for the selected range. Unadjusted prices can distort results around splits/dividends. Portfolio covariance, portfolio Sharpe/volatility, FX, leverage and risk forecasting are not implemented.

## Paper portfolio valuation

Quantities, acquisition prices and monetary arithmetic use `NUMERIC`/Java `BigDecimal`. One position per asset per portfolio is supported. Each position uses the latest cached daily close from the configured provider, retaining its date/currency. Position P/L is value minus quantity × average acquisition price. No price means null value/P&L, not zero.

The portfolio's complete market value/P&L is null if any position lacks a matching-currency price. Invested capital is null for mixed currencies. `known_value` sums only priced base-currency positions. Allocation displays only that covered subset and labels the exclusion. There is no implicit FX conversion.

## Frontend behavior

Eleven lazy routes share a responsive collapsible sidebar, debounced global stock search, persisted theme, provider status and notices. TanStack Query caches server state, cancels queries via fetch signals, retries queries once, and never retries mutations automatically. Focus polling is disabled. Health refresh is manual by default, optionally five/fifteen minutes. Historical data ingestion always requires an explicit user action.

Settings, strategy parameter drafts and backtest drafts live in browser local storage. Watchlists and portfolios live in PostgreSQL. Planned functionality is shown as unavailable rather than simulated. ECharts is lazy loaded and provides an interactive crosshair, linked volume, zoom and line/candlestick modes. Financial rows remain accessible as tables.

## Security / deployment boundaries

Local ports bind to loopback; PostgreSQL stays internal. JSON mutations use validation and bound SQL parameters, errors omit traces, provider keys stay server-side, and no authentication is simulated. No permissive CORS configuration is enabled. Runtime application containers use non-root users. This is a single local user's workspace and is not ready for internet exposure without authentication, authorization, CSRF/origin review, TLS and broader production hardening.

No outbound messages, deployments, Git commits, storage relocation, system installations or optional infrastructure are performed by this foundation.


Phase 2 additions and current contracts: see [PHASE2.md](PHASE2.md). Historical Phase 1 descriptions above are retained for context.
