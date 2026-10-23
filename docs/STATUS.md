# METIS Phase 2 — local pipeline verified; real-data acceptance pending

Verified **24 October 2026 (Asia/Calcutta)** in `D:\QuantOPs\Metis`.

**Phase 1 remains operational. Phase 2's local-data, feature, training, registry, inference and React integration are implemented and verified using isolated development fixtures. Phase 2 is not fully real-data accepted: the main database contains no genuine historical prices, and live-provider verification was deliberately not performed.**

Frontend: **http://localhost:5173**. Swagger: **http://localhost:8080/swagger-ui/index.html**.

**Alpha Vantage requests made: exactly 0.** The configured key's presence was checked as a boolean, without printing its value. Main live ingestion remains disabled, fixture mode remains disabled, and the persistent provider-attempt ledger is empty. No credential is missing; no live request is authorized. Fixture prices, model metrics and predictions were never put in the main database or main artifact volume.

## Implemented milestones

| Milestone | Result and verification |
|---|---|
| A — data engineering | Local CSV/API import; transactional, idempotent persistence with numeric precision and source preservation; rejection/skip counters; closed-session validation; deduplicated dry-run session-gap planning; persistent per-attempt quota protection; all requested indicators share one Python engine and pass independent numerical/causality checks. Live requests default off. |
| B — features/targets | 18 causal features, SHA-256 dataset identity, next-available-observation direction/return targets, final unlabeled observation excluded, chronological rolling folds, label-boundary purge, final untouched holdout, training-only variance selection/scaling. |
| C — models/evaluation | Logistic Regression, Random Forest and CPU XGBoost; five fitted classifier/regressor variants across three families; majority/previous-direction/zero-return baselines; per-fold and aggregate metrics, periods, counts, timing, complexity, class distribution/confusion matrix and null-safe undefined metrics. Sequential fixture training only; no financial-performance claim. |
| D — registry/inference | Existing model tables extended; dataset/job persistence; one bounded asynchronous worker; skops/native-JSON artifacts and preprocessing in a named volume; checksums/schema/library compatibility/UUID path guards; typed training-status and inference contracts; Spring reconciliation/persistence; fresh after-close forward inference, evaluation-date exclusion, stale/already-closed-target rejection, duplicate prevention and actual-outcome fields. |
| E — React/integration | Existing eleven routes and styles preserved. Connected coverage, indicators/overlays/features, explicit CSV import/training/prediction controls, registry/evaluation/job/prediction history, protection settings and research status. No automatic download or training on page open. Existing read-only Markets functionality uses the repaired stored-source queries. |

The verified isolated end-to-end case imports **480 fixture sessions twice**, validates indicators/features, trains/evaluates/registers **five estimators**, rejects retroactive inference, imports **40 subsequent fixture sessions**, then persists **two genuine estimator outputs** (classification and return) marked **DEVELOPMENT_ONLY**. These are synthetic-input software tests, never genuine market predictions. The verified case therefore has 520 price rows; the retained test environment also contains earlier explicitly labeled diagnostic cases.

## Errors identified and repaired during execution

| Root cause | Repair |
|---|---|
| The existing Nginx 32 KB request limit rejected a 66 KB daily-history import with HTTP 413 | Added a 1 MB limit scoped only to `/api/v1/assets/{id}/import`; retained the original small limit elsewhere. |
| RestClient created its own default Jackson converter, serializing Java dates differently from the application contract; Python planning returned 422 | MlClient now uses Spring's configured ObjectMapper. Added an actual local-HTTP serialization regression test for LocalDate and SQL Date. |
| The model reconciliation scheduler could throw while PostgreSQL was normally restarting, producing an ERROR even though services recovered | Added database-outage handling that preserves jobs and retries on the next scheduled check; regression test verifies recovery without provider calls or retraining. Repeated the normal-restart smoke; final backend has zero ERROR/FATAL entries. |
| New quota configuration needed proxy-safe access because transactional Spring services use proxies | Quota values are obtained through service methods rather than proxy fields. Persistent reservation/outcome writes use separate transactions and advisory locking; failed attempts consume budget. |
| Local imports could otherwise be mistaken for successful Alpha Vantage activity | Provider health now scopes its last-request query to Alpha Vantage; local coverage and ingestion history remain independently visible. |
| The prediction summary could show a different task's newest record, hiding the selected classifier's probability | Summary selects the chosen model's latest prediction; probabilities use a probability format. Added a regression test. Removed the obsolete Predictions SOON tag. |
| The Windows console could not print an arrow in the final E2E diagnostic | Used ASCII diagnostic output; the complete E2E script subsequently exits successfully. Source files use UTF-8. |

No applied migration was edited. No PostgreSQL reset, volume deletion, financial-data overwrite, tool reinstallation, global Docker/WSL change, architecture replacement or frontend redesign occurred. Only the three affected application images were built; PostgreSQL's existing image/volume were reused. Dependency caches were reused for repair builds.

## Final verification

| Check | Result |
|---|---|
| Frontend tests | **24 passed**, 0 failed/skipped; includes all eleven routes, no automatic POSTs, dry-run-only preview, deliberate training and selected-model probability |
| Java/infrastructure unit tests | **24 passed**, 0 failures/errors/skips |
| PostgreSQL integration tests | **6 passed**, 0 failures/errors/skips, using a separate disposable Testcontainers database; migrations, constraints, CRUD, fixture import precision/deduplication, persistent budget and live gate |
| Python tests | **23 passed**, 0 failed/skipped; quality, independent indicator values, causality, labels/dataset identity/purging, train-only preprocessing, all model families/baselines, safe artifacts, actual subprocess execution, health responsiveness, restart recovery and forward inference |
| Total automated suite | **77 passed; 0 failures; 0 skipped** |
| Builds | Frontend TypeScript/production build and Java JAR/image builds pass; ML CPU dependencies import successfully with container networking disabled |
| Docker E2E | Full PostgreSQL → cleaning → indicators → features → training/evaluation → persistent registry/artifacts → inference → Spring/Nginx passes in `metis-phase2-test` with no key and explicit fixture provenance |
| Artifact/data persistence | Price rows, terminal job, five artifacts and two idempotent predictions survive normal test-service restarts and ML-container recreation; loading does not retrain |
| Browser | **18 views pass**: all eleven main routes, Overview mobile, Laboratory/Predictions/Settings mobile, and isolated registry/prediction/explorer pages. Zero error panels, stuck loaders or horizontal overflow. Real estimator outputs render only on the isolated test pages. |
| Existing smoke tests | `smoke.py --restart-database` and `smoke.ps1` both pass against the final main stack |
| Runtime/API | Java ↔ PostgreSQL, Java ↔ FastAPI, frontend ↔ Spring, Swagger UI/OpenAPI and typed ML response schemas verified |
| Main database | All **16 application tables** plus Flyway history; **V1–V4** successful; Hibernate schema validation enabled and passing |
| Existing data | All original **13 application tables** retain identical pre/post row counts and content fingerprints; Flyway history adds V3/V4 as intended |
| Production isolation | Main historical prices, models, predictions, training datasets/jobs and provider requests remain empty; no synthetic data or fixture artifact was published |
| Logs/health | Four main services healthy, zero automatic restart counts and zero ERROR/FATAL entries in the repaired backend |

Non-blocking test/build notices remain: a dependency deprecation in Starlette's test client, Java 25 test-agent/Unsafe warnings, and the existing lazy ECharts bundle-size advisory. They do not represent failed tests or application runtime errors.

## Final main Docker state

| Service | State | Published address | Measured memory / limit |
|---|---|---|---|
| postgres | Running / healthy | Internal only | 25.43 MiB / 256 MiB |
| spring-backend | Running / healthy | 127.0.0.1:8080 | 303.4 MiB / 768 MiB |
| ml-api | Running / healthy | 127.0.0.1:8000 | 38.28 MiB / 1 GiB |
| frontend | Running / healthy | 127.0.0.1:5173 | 13.08 MiB / 128 MiB |

The separate `metis-phase2-test` stack is **stopped to release resources**; its database/artifact volumes are retained. Main application services remain running. The ML limit includes its bounded worker; the table measures idle final-state usage, not peak training memory.

## Remaining acceptance dependencies

- A genuine local unadjusted daily OHLCV dataset is needed for real-data training/evaluation and genuine market predictions. Default windows require approximately **462 closed sessions** per asset. The user explicitly selected isolated fixtures and no live requests. Import controls are ready; production models correctly remain absent until genuine local history is supplied and training is deliberately started.
- Live Alpha Vantage ingestion remains **unverified pending explicit per-operation authorization**. The key is present, but no request was made. Compact history covers only the latest 100 sessions and cannot alone satisfy default training requirements. This is an authorization/data dependency, not an application startup defect.
- Actual-outcome reconciliation is implemented; genuine outcomes cannot be validated before genuine forward predictions and their later local observations exist. No profitability or live-provider success is claimed.

**Phase 2 is implemented and fixture-verified, but is not declared fully complete against genuine-data/live-provider acceptance.** No user intervention is needed to keep the application running.

## Change list, dependencies, artifacts and commands

Exact changed/new files: [PHASE2_FILES.md](PHASE2_FILES.md) (**75** source/configuration/test/documentation files). Full architecture, formulas, leakage rules, API contracts, bounds and instructions: [PHASE2.md](PHASE2.md).

New direct Python dependencies: **scikit-learn 1.7.2**, **xgboost-cpu 3.0.5**, **skops 0.13.0**, **exchange-calendars 4.11.2**; transitive versions recorded in the existing requirements lock file. No frontend or Java dependency was added.

Persistent artifact location: `/var/lib/metis/artifacts/<UUID>/`, mounted from `metis_model_artifacts` for the main application (empty) and `metis-phase2-test_model_artifacts` for isolated fixture models (retained). Native development defaults to `services/ml-api/.artifacts/`; pytest uses temporary directories. No fixture model is promoted to the main volume.

From the project directory, existing images start with:

```powershell
docker compose up -d --no-build --wait --wait-timeout 240
```

Ignored `.cache` files hold non-secret acceptance summaries, database fingerprints, E2E case IDs and browser screenshots. Main source/data validation state was checked again after the final repairs. The Phase 1 acceptance record below is historical; its former missing-key state has been superseded by the current presence-only check.

---

<details>
<summary>Retained Phase 1 acceptance and earlier progress (historical)</summary>

# METIS Phase 1 — operational acceptance

Verified **24 October 2026 (Asia/Calcutta)** in `D:\QuantOPs\Metis`.

**Phase 1 is fully operational and all required runtime acceptance checks pass. All four application containers remain running and healthy at handoff. The optional Alpha Vantage key is absent; live market ingestion remains pending credentials, separately from application readiness.**

Frontend: **http://localhost:5173**. Swagger: **http://localhost:8080/swagger-ui/index.html**.

## Repairs completed

| Error / root cause | Repair |
|---|---|
| Hibernate rejected `assets.currency`: PostgreSQL reports CHAR/bpchar, while a Java String still inferred JDBC VARCHAR despite the `char(3)` DDL declaration | Added `@JdbcTypeCode(SqlTypes.CHAR)` and length 3 to the existing entity field. Hibernate `ddl-auto: validate` remains enabled. No migration or database column was changed. |
| Watchlist membership returned HTTP 500 / NullPointerException; the same pattern affected holding creation, ingestion and database health | Services accessed the public `jdbc` field on Spring's repository proxy instead of invoking its target. Made the field private, added typed `Store.scalar(...)`, and routed every scalar query through a repository method. |
| PostgreSQL integration tests still skipped against Docker Engine 29 because its API rejected the older docker-java default | Added test-classpath `docker-java.properties` with API 1.44. All three integration tests now execute without JVM overrides. No dependency or Docker daemon setting changed. |
| This Codex session could not find Docker or its credential helper after Docker's installation | Located the existing per-user Docker installation and adjusted PATH only for the verification commands. No global environment change, reinstall or credential configuration was needed. |

Only the affected backend image/container was rebuilt/replaced. Existing frontend, FastAPI and PostgreSQL images were reused. The frontend source/design, Compose configuration, V1/V2 migrations and existing database volume were preserved. A test-only configuration change was isolated from the POM so the existing Docker dependency layer could be reused; the POM is unchanged.

## Source files modified

- `backend/spring-boot/src/main/java/io/metis/entity/Asset.java` — explicit CHAR JDBC mapping.
- `backend/spring-boot/src/main/java/io/metis/repository/Store.java` — private JdbcTemplate and typed scalar-query method.
- `backend/spring-boot/src/main/java/io/metis/service/WatchlistService.java` — proxy-safe count query.
- `backend/spring-boot/src/main/java/io/metis/service/PortfolioService.java` — proxy-safe holding count query.
- `backend/spring-boot/src/main/java/io/metis/service/IngestionService.java` — proxy-safe advisory lock, cache and budget queries.
- `backend/spring-boot/src/main/java/io/metis/service/SystemService.java` — real database probe through the repository method.
- `backend/spring-boot/src/test/java/io/metis/PersistenceIT.java` — added a database-UP health regression assertion to the existing integration test.
- `backend/spring-boot/src/test/resources/docker-java.properties` — new, test-only Docker API compatibility configuration.
- `docs/STATUS.md` — this completion record; previous progress retained below as history.

Ignored `.cache` artifacts contain the verification scripts, database-content fingerprints (no row contents), final container state, and connected-dashboard screenshot. No secrets were printed.

## Runtime and integration acceptance

| Check | Result |
|---|---|
| PostgreSQL integration suite | **3 passed; 0 failures, 0 errors, 0 skipped**, with a separate disposable Testcontainers PostgreSQL 17.6 database |
| Affected Java unit tests | **7 passed**: IngestionServiceTest (4), PortfolioServiceTest (3) |
| Previously successful unrelated checks | Existing 20 frontend / 22 Java-infrastructure / 9 Python results retained; unrelated suites, frontend build and optional setup were not repeated |
| Backend build | Repaired Spring image builds with Java 21; original dependency cache reused; other application images not rebuilt |
| Hibernate / Flyway | Main application initializes JPA successfully with schema validation enabled; two migrations validate, schema remains at V2, no reapplication/reset |
| Database schema | All **13 application tables**, plus Flyway history, confirmed in the running database |
| PostgreSQL constraints / CRUD | Integration tests pass reference seeds, unique/FK constraints, watchlist add/remove and portfolio/holding create/read/update/delete |
| Persistence across normal restart | Existing `smoke.py --restart-database` passes: a newly created temporary holding remains after normal PostgreSQL restart; temporary records cleaned up |
| Existing data preserved | Before/after row counts and content fingerprints match for all 13 application tables and Flyway history |
| Existing readiness smoke | `infrastructure/scripts/smoke.ps1` passes every check, including PostgreSQL readiness |
| Existing HTTP/persistence smoke | `infrastructure/scripts/smoke.py --restart-database` passes all checks |
| API runtime | Health, asset/search/detail, prices, indicators, watchlists, portfolios, quotes, predictions, models/status/operations, strategies, backtests and ingestion history return successful read responses through Nginx |
| Java → PostgreSQL | Application health reports PostgreSQL UP; actual queries and CRUD succeed |
| Java → FastAPI | Application health reports ML service UP; typed model status responds accurately |
| Swagger / OpenAPI | Swagger UI and `/v3/api-docs` return HTTP 200 in the running backend |
| Frontend → backend | Existing smoke succeeds through :5173 `/api`; real browser loads AAPL and ten database-backed watchlist rows |
| Browser dashboard | Real React dashboard has zero error panels and zero stuck loaders; source-not-configured state shown accurately |
| Optional missing key | Presence checked without printing a value; backend reports NOT_CONFIGURED, manual ingestion returns documented 503 PROVIDER_NOT_CONFIGURED, application remains healthy |
| Logs / stability | Repaired backend has zero ERROR/FATAL structured log entries; all four running containers have zero restart counts after the checks |

No live provider request was made because no key is configured. No financial history or prediction was fabricated. The temporary paper portfolio used test acquisition inputs only and was deleted by the smoke test. No pre-existing records were overwritten or deleted; no volume was removed or reset. No global Docker/WSL settings were changed.

## Final Docker service state

| Service | State / health | Published address | Measured container memory |
|---|---|---|---|
| postgres | Running / healthy | Internal only | 29.94 MiB / 256 MiB limit |
| spring-backend | Running / healthy | 127.0.0.1:8080 | 297.8 MiB / 768 MiB limit |
| ml-api | Running / healthy | 127.0.0.1:8000 | 51.02 MiB / 256 MiB limit |
| frontend | Running / healthy | 127.0.0.1:5173 | 16.3 MiB / 128 MiB limit |

Measured application-container memory is approximately **395 MiB total**, excluding Docker/WSL, OS and browser overhead. Existing conservative CPU/memory/log limits remain unchanged. Normal PostgreSQL restart was followed by an explicit Compose health wait; every service returned healthy. The stack is left running for use.

## Remaining external items

- **Optional market-data credentials:** Alpha Vantage ingestion and real-history chart verification remain pending an API key. This is not an application defect or startup blocker, and no key was requested or exposed.
- Advanced ML training, prediction execution and full backtesting remain the intentionally deferred future phases described in the original brief.

There are **no remaining Phase 1 application or integration blockers** under the requested acceptance criteria.

---

<details>
<summary>Historical foundation record — 23 October 2026, before Docker runtime acceptance (superseded by the completion record above)</summary>

# Foundation status / acceptance record

Recorded: **23 October 2026 (Asia/Calcutta)**. Repository: `D:\QuantOPs\Metis`.

**The foundation is implemented, with verified local builds/tests. Full integrated runtime acceptance is blocked by the missing Docker environment and unconfigured market-data credentials. This document does not mark unexecuted checks as passing.**

## Implemented and verified

| Check | Evidence / result |
|---|---|
| Frontend TypeScript | `npm run typecheck --prefix frontend` passes |
| Frontend production build | `npm run build --prefix frontend` passes; 0.91 MiB output; lazy route/chart chunks |
| Frontend automated tests | **20 passed** in Vitest/Testing Library; all 11 routes, navigation, search, theme, network error/retry, empty state, form validation, disabled execution |
| Real browser layout | Installed Edge, isolated project profile: 1440px desktop and 390px mobile; horizontal scroll widths 1425/390 respectively; no overflow; zero stuck loaders in backend-unavailable view |
| Theme switching | Real browser click changed root theme to light; UI test also verifies persistence |
| Java build | Compiles release-21 bytecode on installed Java 25; runnable Spring Boot JAR packaged |
| Java automated tests | **22 passed**: API validation/error/health, assets, decimal valuation/missing data/currency mismatch, indicators, provider JSON, missing credentials, concurrency, cache and failed-run handling |
| Infrastructure syntax / paths | YAML parsing and Compose build-context/dependency/loopback-port checks pass in Java; PowerShell/Python helper syntax and documentation links checked |
| Migration packaging | JAR inspected: V1 and V2 present only in `BOOT-INF/classes/db/migration` |
| Python tests | **9 passed**: health, registry/model absence, strict schemas, features, unavailable inference seam, OpenAPI and sanitized errors |
| Live Python HTTP | `/health` returned UP; `/model-status` returned NOT_TRAINED, available false, null model version |
| Live frontend HTTP | Vite root returned HTTP 200; browser confirmed truthful backend-unavailable states |
| WSL2 | Read-only escalated check confirmed Ubuntu/default WSL version 2 |
| Resource containment | No system installs/configuration/storage moves, no optional infrastructure/model training, no startup market download; local dependencies/caches remain under D: |

UI tests use isolated API fixtures and a mocked canvas boundary. Java unit tests use isolated repositories/JDBC fixtures. These results do not prove PostgreSQL persistence or real-market chart interaction. No automated test depends on a live provider.

Non-failing tool notes: Vite reports the lazy ECharts chunk above 500 kB (approximately 189 kB gzip). Python's Starlette/AnyIO test adapter emits a deprecation warning. Java 25 prints Maven/Mockito native/agent compatibility warnings. All recorded successful tests/builds returned exit code 0.

## Implemented but not verified in the required runtime

| Capability | Implemented artifacts | Missing verification |
|---|---|---|
| PostgreSQL schema | Thirteen normalized tables, FK/unique/check constraints, indexes, V1/V2 Flyway SQL | Migration execution, actual constraints and JPA schema validation |
| Asset/reference retrieval | JPA queries, search/page endpoints, ten reference US assets | Actual database-backed browser search |
| Watchlists | Create/list, add/remove membership, default reference watchlist | DB round trip and refresh persistence |
| Paper portfolios | Create/list/detail/delete; add/update/remove positions; decimal valuation and coverage | DB CRUD, page refresh and normal database restart persistence |
| Historical ingestion | Replaceable provider interface, compact daily adapter, validation, cache/advisory lock/budget/interval, provenance and failure records | Real provider authentication, supported symbol fetch, actual caching/incremental inserts |
| Financial charts | ECharts line/candlestick/volume/SMA, ranges, zoom, history table | Interactive chart against real ingested history |
| Health integration | DB query probe, Java-to-FastAPI typed/time-bounded HTTP, provider last-run status | Java running against real DB and Python over Compose networking |
| API documentation | springdoc/Swagger dependency and contracts | Runtime `/v3/api-docs` and Swagger UI |
| Docker | Three Dockerfiles with build stages and four-service Compose, Compose DNS/dependencies/readiness, non-root app users, loopback ports, persistent volume | `docker compose config`, image pulls/builds, startup/health/restart |
| CI | Frontend, Python, Java/PostgreSQL and full Compose jobs; smoke CRUD and PostgreSQL restart checks | Workflow execution on GitHub; no repository/remote/push was created |

`mvn verify` reported **3 PostgreSQL integration tests skipped**, explicitly because Testcontainers could not obtain a Docker engine. They are not counted as passes. A separate Docker-capable CI job is configured to run the full service graph and persistence smoke test.

## Acceptance matrix

| Requirement | Status |
|---|---|
| All navigation routes render / dashboard layout / theme | Verified via UI tests + desktop/mobile browser QA |
| Global search requests correct API and navigates | Verified with isolated backend fixture; actual PostgreSQL result blocked |
| Charts use available real data | Implemented; real provider/DB path blocked; no fake history seeded |
| Portfolio/watchlist persistence and CRUD | Implemented; unit/API contracts verified; PostgreSQL workflow blocked |
| Accurate model absence / no fake predictions | Verified Python response and UI tests; no trained model/artifact/experiments present |
| Loading/error/empty states | Verified automated tests and real backend-unavailable browser view |
| Java build / validation / safe error contracts | Verified unit/API tests and JAR packaging |
| Flyway execution / relationships / Swagger | Implemented; Docker/PostgreSQL runtime blocked |
| FastAPI start / health / model status / tests | Verified on local Python 3.13; Python 3.12 container not built |
| Java communicates with Python | Typed bounded client implemented; runtime blocked by DB/Docker |
| Compose validates / images build / containers healthy | Blocked; Docker/Compose missing |
| Persistent volume survives restart | Isolated CI test authored; not executed here |
| Frontend → Java → PostgreSQL integration | Implemented; full runtime blocked |
| Local-only credentials / no fabricated data / bounded services | Reviewed configuration/source; no .env or real keys created/printed |
| Documentation | README and architecture, database, API, Docker, development, roadmap and status guides created |

## Created implementation

108 source, configuration and documentation files were created (generated dependencies, caches, bytecode and build outputs excluded).

- `frontend`: React/TypeScript/Vite/Tailwind terminal with 11 lazy routes, responsive sidebar, search, notifications, market selector, local preferences, light/dark themes, ECharts, typed API/cancellation, TanStack caching, mutations and toasts.
- `backend/spring-boot`: Spring Boot 3.5.7 / Java 21 target, JPA/JDBC repository layer, services/controllers/DTOs/errors, Flyway, Actuator, OpenAPI, provider and Python clients, JUnit/API/PostgreSQL tests.
- `services/ml-api`: Python 3.12 container / FastAPI 0.118.0, strict version-1 future schemas, explicit empty registry, deterministic feature seam, sanitized failures, pytest tests, pinned dependencies/constraints.
- `database/migrations`: V1 schema, V2 reference-only seed. No authoritative migration copies elsewhere.
- `infrastructure`: read-only environment inspection, private env initialization helper, native database override, HTTP/persistence smoke checks, isolated browser QA.
- Root `.env.example`, ignore/line-ending rules, Compose, Docker contexts, GitHub Actions and documentation.

Tables: assets, historical_prices, watchlists, watchlist_items, portfolios, portfolio_holdings, ingestion_runs, model_versions, model_predictions, strategies, backtest_runs, backtest_results, application_settings. This is thirteen tables; Flyway's own history table is additional.

All requested versioned Java endpoints are implemented. Additional endpoints required by UI behavior are POST `/assets`, POST `/assets/{id}/ingest`, GET `/quotes`, DELETE `/portfolios/{id}`, GET `/models/status` and GET `/models/operations`. Full request/response inventory is in [API.md](API.md).

## Deferred later phases

Trained models/inference execution, model history/metrics/promotions/deployments, training orchestration, strategy execution, full backtesting/equity/trade results, MLflow, XGBoost, walk-forward experiments, portfolio covariance/aggregate risk, adjusted-data handling, FX, reliable tested Indian markets, authentication/authorization, Redis, Kubernetes, Prometheus/Grafana, drift monitoring and real-money brokerage integration.

Strategy parameter and backtest configuration forms save browser-local drafts; they do not execute or persist fake results. Model operations contracts return explicit empty collections and NOT_IMPLEMENTED availability. Index cards remain unavailable because no index provider is configured.

## Environment / permissions blockers

| Item | Inspection |
|---|---|
| Workspace / Git | Initially empty, not a Git repository; Git 2.52.0 is available; no commit/remote created |
| Node | 24.13.0, npm 11.6.2 available |
| Java | 25.0.2 available; Java 21 not installed natively; container targets 21 |
| Python | 3.13.7 available; 3.11/3.12 not installed natively; container targets 3.12 |
| Maven | Not installed system-wide; approved project-local Maven 3.9.11 downloaded into ignored `.tools` |
| Docker / Compose | Executables/installation not found; Testcontainers engine detection unsuccessful |
| WSL | Ubuntu/default WSL2 confirmed; initial sandbox denial resolved with a read-only escalated check |
| Docker storage | No Desktop settings/storage path found; location unknown, no relocation performed |
| Market provider | No API key supplied/configured; no real market fetch attempted |
| Disk at inspection | D: 56.1 GiB free; C: 24.2 GiB free (approximate) |

Dependency downloads were explicitly approved and isolated to project directories. No system-wide package installation or unrelated user-file changes were made. Any Docker Desktop installation, Windows feature change, reboot or storage relocation remains a separate explicit user action.

## Resource measurements and estimates

Measured file sizes after verification: node_modules about 198.4 MiB; Java target about 58.2 MiB; Python virtual environment about 157.1 MiB; combined local caches/browser profiles about 471.8 MiB; project-local Maven about 10.2 MiB. These are generated/ignored development artifacts, not repository source. Frontend production output: about 0.91 MiB uncompressed.

One live FastAPI process was measured at about 46.2 MiB working set / 47.0 MiB peak during QA. This is a native process measurement, not a Python-container measurement.

Configured container ceilings: PostgreSQL 256 MiB, Java 768 MiB (384 MiB heap), Python 256 MiB, Nginx 128 MiB: **1,408 MiB total**. Actual container/VM usage was not measured. Initial image/build storage estimate 4–6 GB and VM allowance around 3 GB/two CPUs are guidance, not measured requirements. Image builds run sequentially in documented commands.

## Running services and launch

Vite and FastAPI were started temporarily for live HTTP/browser QA. Spring/PostgreSQL were never started, because their required database/runtime was unavailable. Temporary QA services are stopped at handoff; the full application is not running. No containers or optional infrastructure were started.

After installing and starting Docker Desktop:

```powershell
Set-Location D:\QuantOPs\Metis
.\infrastructure\scripts\init-env.ps1
# Optionally configure ALPHA_VANTAGE_API_KEY privately in .env.
docker compose config --quiet
docker compose --parallel 1 build
docker compose up -d --wait --wait-timeout 240
.\infrastructure\scripts\smoke.ps1
```

Then open http://localhost:5173. Stop normally with `docker compose stop` or `docker compose down`; do not remove volumes. Recommended next development work: close the Docker/PostgreSQL/provider acceptance gaps first, then build a versioned, split-aware dataset/temporal feature pipeline before training any model.


</details>


</details>
