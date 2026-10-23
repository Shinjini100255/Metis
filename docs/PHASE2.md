# Phase 2: local financial data and model pipeline

Phase 2 extends the existing React → Spring Boot → PostgreSQL / FastAPI architecture. The eleven routes, theme, sidebar, layouts and chart library are preserved. Spring owns database writes; Python owns all indicator and ML calculations. No provider is called by startup, dashboard reads, training, inference, builds or automated tests. **No live provider requests are authorized for this work.**

## Data, ingestion and protection

Use Model Laboratory to select an asset and explicitly import a genuine local CSV. Required plain header: `trading_date,open,high,low,close,volume`. Maximum 1 MB / 5000 input rows; API requests are bounded to 5000 rows. Use unadjusted US OHLCV only. Decimal prices retain PostgreSQL NUMERIC(20,6) precision. Imports are transactional and idempotent on asset/date/provider; existing prices are never overwritten. Counters record inserted, skipped and rejected rows; updated is deliberately zero. Source is `Local CSV`. CSV parsing accepts plain fields, without quoted commas; malformed headers/rows are rejected.

`Plan ingestion` is a zero-external-request dry run. Asset IDs are deduplicated, local dates checked, and missing closed XNYS session ranges reported. One request can retrieve the compact endpoint's latest 100 sessions only. Older gaps require a local import; an estimate is never a promise of full backfill. Fresh successful fetches suppress repeat requests. Planning may call the internal Python calendar endpoint, never the provider.

Default Compose forces `LIVE_INGESTION_ENABLED=false`. A direct legacy ingest POST also needs `X-Confirm-Live-Ingestion: true`; both checks apply. The UI exposes planning, not live execution. Enabling live ingestion requires a deliberate reviewed Compose override and explicit authorization specifying symbol, `TIME_SERIES_DAILY / compact`, and request count. A configured key does not grant permission. Credential presence is returned as a boolean; values and request URLs never appear in application errors.

Configurable local protections: `MARKET_MINUTE_LIMIT=4`, `MARKET_DAILY_LIMIT=20`, `MARKET_MIN_INTERVAL_SECONDS=15`, `MARKET_JOB_LIMIT=2`, `PROVIDER_TIMEOUT_SECONDS=12`, `PROVIDER_RETRIES=0`, `MARKET_CACHE_HOURS=12`. Existing `.env` values remain effective. These are local caps, not claims about the provider's plan. Each network attempt reserves a persistent `provider_requests` ledger entry in an independent transaction under a PostgreSQL advisory lock. Failures consume budget. Only transient transport failures can be retried, at most once, each with a separate reservation and minimum interval. Provider quota/plan, HTTP and malformed-data responses are never automatically retried. Each per-asset ingestion run records actual attempts.

Validation rejects missing/invalid fields, non-positive/non-finite/excess-precision prices, negative or oversized volume, inconsistent OHLC, non-trading sessions and sessions not closed. Identical duplicate rows are skipped; conflicting duplicates remove both candidates and are reported. Data is sorted. Missing sessions and insufficient warmup are reported without imputation. US exchange validation uses the bundled XNYS calendar (2000 through current year + 2). Non-US markets are unsupported. No adjusted/unadjusted mixing, split correction or dividend correction is claimed. Training/inference reject histories with rejected rows rather than silently discarding them.

Charts, features and labels select one source series per asset (the provider of its latest session, latest ingestion breaking ties). They never combine providers. Quotes and paper-portfolio valuation use that latest source consistently. All calculations use unadjusted prices; split discontinuities can materially distort results.

## Indicator formulas

A single Python engine serves Spring chart requests and model features. Warmup values are null; all windows use only the current and earlier observations.

| Indicator | Definition |
|---|---|
| SMA 20/50/200 | Arithmetic mean of the last N closes; full N observations required |
| EMA 12/26 | Seed with first N-close SMA, then alpha = 2/(N+1) |
| RSI 14 | Wilder gain/loss means seeded from first 14 differences, then (13×prior + current)/14; flat = 50, positive-only = 100 |
| MACD / signal / histogram | EMA12 − EMA26; signal = SMA-seeded EMA9 of valid MACD values; histogram = MACD − signal |
| Bollinger 20 | SMA20 ± 2 × population standard deviation of last 20 closes |
| Daily return | close[t]/close[t−1] − 1 |
| Rolling volatility 20 | Sample standard deviation of last 20 simple daily returns × sqrt(252) |
| Volume SMA20 | Last 20 volumes' arithmetic mean |
| Momentum 5/10/20 | close[t]/close[t−N] − 1 |

Whole-range volatility uses at least 20 daily returns. Sharpe uses daily mean/sample SD × sqrt(252), risk-free rate zero; zero variance returns null. Drawdown is minimum close/running peak − 1, with at least two observations. Independent numerical tests also check that extending a series never changes earlier indicator values.

## Features, targets and leakage

Schema `daily-unadjusted-v1`: daily return, rolling volatility20, RSI14, momentum5/10/20, SMA20/50/200-to-close ratios minus one, EMA12/26-to-close ratios minus one, MACD/signal/histogram divided by close, Bollinger width divided by close, volume/volumeSMA20, close/open−1 and (high−low)/close. Non-finite/incomplete warmup rows are removed; zero average volume leaves features unavailable rather than inventing a denominator. No future input or target is filled.

Regression target is next available observation's close/current close−1. Classification is 1 only for positive target returns; zero/negative = 0. The final observation is retained for inference but has no training label. Feature and label dates are recorded. Gaps may make a historical label span several sessions; quality metadata exposes the gaps. Do not interpret such labels as a uniform elapsed-time return.

The SHA-256 dataset ID covers canonical sorted validated bars, asset/symbol, source, unadjusted basis, feature schema and development provenance. The preserved job request snapshot plus configuration permits reproducibility. Dataset metadata stores counts, dates, schema, features, horizon and quality report.

No random split/shuffle occurs. Default walk-forward evaluation uses 3 rolling folds, training window 160 sessions, test window 20; the last 40 labeled samples are reserved first as the final holdout. A one-observation purge separates training and each test/holdout, with explicit assertions that the last training label date precedes the first test feature date. All folds end before the holdout. Default settings require approximately **462 closed sessions**, including 200-session feature warmup. Windows are configurable within bounded limits. Preprocessing uses training-only VarianceThreshold and StandardScaler fitted separately inside each fold/final pipeline. Constant features are recorded. Missing feature rows are excluded, with no imputer, clipping or future-fit outlier handling.

## Training and evaluation

Five estimators cover three model families: Logistic Regression classification, Random Forest classification/regression, and CPU XGBoost classification/regression. Fixed seed 17 by default; no hyperparameter search. Ensembles default to 40 trees/depth 4, bounded at 80 trees/depth 6. Random Forest minimum leaf size is 4. Libraries use approximately two threads. Jobs run sequentially in one subprocess, one active slot, default 300-second timeout (hard maximum 600). Docker ML memory is capped at 1 GiB / 2 CPUs, including worker and API parent. Other service limits remain unchanged. The subprocess is killed on timeout; no model is registered from a failed/partial job. API health remains available. Interrupted jobs are marked on service restart; no automatic retraining occurs.

Each training window must contain both classes. Classification reports accuracy/directional accuracy, precision, recall, F1, ROC-AUC when both test classes exist, confusion matrix, class counts and samples. Undefined AUC/R² are null. Regression reports MAE, RMSE, R² and directional accuracy. Baselines: training-window majority class, previous available observation's direction, and zero return. Reports include fold periods, label periods, sample counts, training/inference durations, complexity and mean/SD aggregates. Selection uses walk-forward accuracy (classification) or RMSE (regression), then stability and simpler-family ties. The holdout is evaluated once per final fitted estimator and never used for selection. No refit on holdout or automatic promotion/retraining occurs. Saved models use only the pre-holdout training period; evaluation endpoints report their historical evidence.

Probabilities are uncalibrated model estimates. Accuracy, fixture results and baseline improvements do not establish financial profitability. There is no trading execution or backtest implementation in this phase.

## Registry, persistence and inference

V3 extends ingestion runs and adds the persistent provider ledger. V4 adds `training_datasets` and `training_jobs`, extends existing `model_versions` and `model_predictions`, and adds partial unique indexes for one active job, artifact references and per-model/asset/feature-date predictions. Original V1/V2 remain unchanged. There are **16 application tables** plus Flyway history after both migrations. Hibernate remains `ddl-auto: validate`.

Model records hold asset/task/algorithm, UUID version/reference, dataset ID, feature configuration, hyperparameters, periods, evaluation metrics, provenance and READY status. Large artifacts live in the named `model_artifacts` Docker volume at `/var/lib/metis/artifacts/<UUID>/`. Native development defaults to `services/ml-api/.artifacts/` when run from that directory; this is ignored by Git. A job UUID directory contains immutable request snapshot, job state and result. Each model UUID directory holds a manifest, checksums and safe artifact files. Preprocessor and sklearn pipelines use skops with an empty custom trusted-type list; XGBoost uses native JSON plus a skops preprocessor. No pickle/joblib import or user-supplied path is accepted. Canonical UUID path guards, SHA-256 integrity, exact library versions and feature schema are checked before inference. Missing, corrupted, unsafe or incompatible artifacts produce explicit errors. Libraries are pinned in `requirements.lock.txt`.

Spring polls the one active job every five seconds and transactionally persists its completed dataset/models. Terminal results are not dependent on browser polling. A missing service beyond the bounded reconciliation window marks a job INTERRUPTED. Python terminal state and artifacts survive normal restarts.

Inference is explicit. It needs compatible asset, source, provenance and complete latest closed-session features. The feature date must be later than the final evaluation label date, at most seven calendar days old, and its next XNYS target session must not already have closed. This prevents retroactive historical evaluation from becoming a new forward prediction. The response records feature timestamp (exchange close in UTC), creation time, next-session date/horizon, task, direction or return, probability, input age and status. Duplicate execution returns the original persisted prediction. Actual outcome fields are filled only when the same source's feature and target closes later exist locally; this reconciliation never downloads data. FORWARD and RESOLVED are production statuses; isolated fixture predictions stay DEVELOPMENT_ONLY.

## HTTP contracts

Browser requests go only through Spring `/api/v1` and Nginx `/api`. FastAPI remains the internal calculation/model service; loopback direct ports exist for development/diagnostics.

| Spring route | Behavior |
|---|---|
| POST `/ingestion/plan` | `asset_ids`, `from`, `to`; deduplicated zero-external-request plan |
| GET `/ingestion/protection` | Boolean key presence, live/fixture gates, configured caps and actual attempt total |
| POST `/assets/{id}/import` | `bars` and `development_only` (false for genuine local imports); idempotent validation/persistence |
| GET `/data/coverage` | Source-specific local counts and date bounds |
| GET `/assets/{id}/indicators` | Existing route, now shared Python indicator engine |
| GET `/assets/{id}/features` | Dataset identity/counts and latest feature values |
| POST `/training/jobs` | `asset_id`, optional bounded `configuration`; asynchronous 202 response with job ID/status |
| GET `/training/jobs` | Most recent 50 persisted jobs |
| GET `/models`, `/models/operations`, `/models/status` | Persisted versions, evidence, jobs and artifact readiness |
| POST `/predictions` | `asset_id`, `model_version_id`; actual compatible forward inference and idempotent persistence |
| GET `/predictions?assetId=...` | Existing paginated persisted prediction history |

FastAPI adds typed input contracts for POST `/data/validate`, `/data/plan`, `/indicators`, `/features`, `/training/jobs`, `/predict`; GET `/training/jobs/{UUID}` returns bounded persisted job state. Existing health/models/model-status remain. Errors include insufficient history/training data, invalid data, single-class training, TRAINING_BUSY, missing/incompatible/corrupt artifacts, stale features, series mismatch, no forward observation and target already closed. Swagger lists current contracts.

## Startup and verification

From `D:\QuantOPs\Metis`, use `docker compose up -d --no-build --wait --wait-timeout 240` with existing built images. Source changes can use `docker compose --parallel 1 build` followed by the startup command. Frontend: http://localhost:5173. Backend Swagger: http://localhost:8080/swagger-ui/index.html. No live call occurs at startup.

Python: from `services/ml-api`, run `..\..\.venv\Scripts\python.exe -m pytest -q`. Java/PostgreSQL: `.tools\apache-maven-3.9.11\bin\mvn.cmd -f backend/spring-boot/pom.xml -Dmaven.repo.local=D:\QuantOPs\Metis\.cache\m2 verify`. Frontend: `npm --prefix frontend test -- --run`, `npm --prefix frontend run build`. Existing smoke: `python infrastructure/scripts/smoke.py --restart-database` and `powershell -File infrastructure/scripts/smoke.ps1`. None calls the market provider. **Do not use the old ignored `.cache/phase1-runtime-check.py`: it predates the no-live instruction.**

### Isolated fixture end-to-end verification

Start using `docker compose -p metis-phase2-test -f docker-compose.yml -f infrastructure/docker/compose.phase2-test.yml up -d --no-build --wait --wait-timeout 240`. This reuses built images with separate project volumes, no provider key, explicit fixture gates and ports 15173/18080/18000. It never mounts the main data/artifact volumes. Run `.venv\Scripts\python.exe infrastructure/scripts/phase2-e2e.py`; it refuses fixture operations unless the isolated no-key fixture gate is verified. It creates one clearly labeled test asset, imports 480 fixture sessions twice, validates indicators/features, trains five estimators, registers models, rejects retroactive inference, imports 40 fresh fixture rows, runs two genuine estimator predictions and verifies duplicate prevention. These outputs are development tests, not financial results. Case IDs remain in ignored `.cache/phase2-e2e.json`.

Normal restart with the same two Compose files/project, then `phase2-e2e.py --verify-after-restart`, verifies price/model/job/prediction/artifact persistence. Stop the test project with the same arguments and `stop`; retain its volumes. No `down -v`, volume deletion, database reset or production fixture import is permitted.

## Dependencies and limitations

Added direct dependencies: scikit-learn 1.7.2, xgboost-cpu 3.0.5 (no GPU distribution), skops 0.13.0, exchange-calendars 4.11.2; pinned transitive CPU/scientific/calendar dependencies in the existing lock file. No Java or frontend dependencies were added. No additional application service was introduced.

Real historical data is absent in the main database. All model training/evaluation/inference verified with synthetic inputs must remain confined to isolated tests and labeled development-only. Genuine real-data model validation and genuine market predictions require a sufficiently long genuine local CSV (approximately 462 sessions with defaults). Live provider verification remains pending explicit authorization; compact 100-session downloads alone cannot satisfy default training warmup/windows. There is no reason to spend the provider quota merely to test presence of a key.

Main environment and final acceptance counts are recorded in `STATUS.md`. No feature should be considered real-data validated solely because fixture end-to-end tests pass.
