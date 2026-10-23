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
