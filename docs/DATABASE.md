# Database

## Authoritative migration path

Only `database/migrations` contains migration SQL. The Maven resource configuration maps it to `db/migration` inside the backend JAR. Docker copies this path into the same relative build layout. Flyway starts before Hibernate's `validate` step. Fresh databases apply V1 then V2; existing databases use Flyway checksums. Add V3+ files for changes; never edit applied migrations or enable automatic schema mutation.

## Tables

| Table | Purpose / constraints |
|---|---|
| assets | Unique validated symbol; company reference label, exchange, ISO currency, type, created UTC time |
| historical_prices | Decimal OHLC, nullable adjusted close/source timestamp, non-negative volume, provider, ingestion UTC time; unique asset/date/provider; OHLC consistency checks |
| watchlists | Unique name, created UTC time |
| watchlist_items | Asset/watchlist FKs, unique pair, cascading watchlist deletion |
| portfolios | Name, ISO base currency, created UTC time |
| portfolio_holdings | Asset/portfolio FKs, unique pair, positive decimal quantity/cost, updated time; cascading portfolio deletion |
| ingestion_runs | Asset FK, provider, RUNNING/SUCCESS/FAILED, started/completed time, inserted count, sanitized error |
| model_versions | Unique model name/version, status, created time, optional internal artifact reference |
| model_predictions | Model/asset FKs, target date, decimal value, nullable bounded probability, created time |
| strategies | Unique strategy type, name, JSONB configuration, created time |
| backtest_runs | Strategy/asset FKs, valid date range, positive decimal capital, status/time |
| backtest_results | Unique backtest FK, decimal cumulative return, Sharpe, drawdown and volatility |
| application_settings | Unique key, JSONB value and updated time; reserved for future server preferences |

Financial amounts use `NUMERIC(20,6)` and metrics use higher fractional precision. IDs are generated identities. Dates are exchange sessions; all timestamps use TIMESTAMPTZ. Price queries have an asset/date index. Ingestion and prediction history have retrieval indexes. No user/auth table is introduced for the single local development user.

V2 seeds ten US reference assets, one watchlist and its memberships, and three strategy definitions. It never seeds price history, portfolios/holdings, predictions, trained models or backtest results. Strategy configuration is reference metadata, not an executed result.

## Persistence and tests

The `postgres_data` named Compose volume mounts at `/var/lib/postgresql/data`. Normal stop/start/restart and `docker compose down` retain it. Database passwords are environment inputs. Changing `.env` cannot change credentials already stored in a volume.

`PersistenceIT` uses a disposable PostgreSQL 17 container, runs the actual Flyway SQL, counts all thirteen tables and checks reference-only seeding, unique/FK constraints, API validation, watchlist membership, portfolio create/add/edit/delete/read persistence, Swagger and OpenAPI. It skips explicitly when Docker is unavailable. Current host execution: all three integration tests skipped; no migration or PostgreSQL runtime verification is claimed.

The CI Compose smoke test checks service readiness, portfolio HTTP round trips through Nginx, and persistence after a normal PostgreSQL restart. Use `docker compose exec -T postgres pg_isready -U metis -d metis` with the configured username/database for a manual readiness check.

## Backup and change strategy

For database upgrades, stop application mutations, make a `pg_dump` backup using the running container's configured credentials, verify a restore in a disposable database, then apply a new migration. On Windows, use binary-safe redirection or a container-side file followed by `docker cp`; avoid legacy PowerShell pipelines corrupting custom-format dump bytes. Never prune unrelated volumes/images or delete the METIS volume as a troubleshooting shortcut.


Phase 2 additions and current contracts: see [PHASE2.md](PHASE2.md). Historical Phase 1 descriptions above are retained for context.
