# Development and verification

## Native development

The reproducible target is Java 21, Node 22+ and Python 3.12 in containers. This host's Java 25 can compile release-21 bytecode, and its Python 3.13 passed the service tests; this does not replace container verification.

1. Initialize `.env` with `infrastructure/scripts/init-env.ps1`. Keep it private.
2. With Docker available, expose only the development database to loopback:

```powershell
docker compose -f docker-compose.yml -f infrastructure/docker/compose.native.yml up -d postgres
```

3. In a Java terminal, set the same database credentials as `.env`. The helper's generated password has no surrounding quotes, so it can be loaded without printing it:

```powershell
$env:POSTGRES_USER = 'metis'
$env:POSTGRES_PASSWORD = ((Get-Content -LiteralPath .env | Where-Object { $_.StartsWith('POSTGRES_PASSWORD=') }) -replace '^POSTGRES_PASSWORD=', '')
# Set DATABASE_URL separately if you changed the default DB/port/user.
mvn -f backend/spring-boot/pom.xml spring-boot:run
```

Spring defaults to loopback and `jdbc:postgresql://localhost:5432/metis`, with Python at `http://127.0.0.1:8000`. Native Spring does not automatically load `.env`. Set ALPHA_VANTAGE_API_KEY privately in its process environment if desired. Do not print credentials or put them in command arguments.

4. In a Python terminal:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r services/ml-api/requirements-dev.txt -c services/ml-api/requirements.lock.txt
Push-Location services/ml-api
..\..\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 1 --no-access-log
```

5. In a frontend terminal:

```powershell
npm ci --prefix frontend
npm run dev --prefix frontend
```

The Vite proxy targets local Java :8080. Stop each owned native service with Ctrl+C. For only UI development against container Java, `docker compose up -d spring-backend` starts PostgreSQL/Python as dependencies; skip the frontend container and use Vite on :5173. Do not start both frontend servers on the same port.

## Automated checks

```powershell
npm run typecheck --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
mvn -B -ntp -f backend/spring-boot/pom.xml verify
Push-Location services/ml-api
..\..\.venv\Scripts\python.exe -m pytest -q
Pop-Location
```

The UI tests use in-memory API responses, never live prices. They exercise all routes, sidebar navigation, search request/navigation, theme storage, network errors/retry controls, empty states, paper-position validation, unavailable model state and disabled backtesting. Charts are isolated from jsdom's canvas boundary; actual chart interaction with real records still requires provider/database runtime verification.

Java unit/API tests cover bounded asset queries, safe errors and health, DTO validation, exact portfolio decimals, missing-price/currency coverage, SMA/RSI/volatility/drawdown, provider JSON/malformed/quota payloads, missing credentials, concurrent ingestion rejection, cache suppression and failed-run logging. Testcontainers integration tests exercise actual PostgreSQL migrations and HTTP persistence when Docker is present.

Python tests cover health, empty registry/accurate availability, strict future schemas, deterministic feature returns, unavailable prediction behavior, OpenAPI, unknown route safety, explicit model exceptions and sanitized unexpected exceptions. pandas/NumPy are installed for the future feature boundary but are not imported at startup. No scikit-learn dependency is needed yet.

## Integration / resource checks

```powershell
docker compose config --quiet
docker compose --parallel 1 build
docker compose up -d --wait --wait-timeout 240
.\infrastructure\scripts\smoke.ps1
python infrastructure/scripts/smoke.py
docker stats --no-stream
```

The Python smoke script mutates only a temporary paper portfolio and deletes it in a finally block. Use an isolated local test stack. It performs no provider fetches. Normal restart/volume persistence should also be checked before accepting this phase. CI builds/starts all four services and runs the smoke workflow with a normal PostgreSQL restart; CI itself has not run here.

Manual acceptance after Docker is available: load dashboard assets, search AAPL, configure a provider key, fetch once, inspect its price/volume/history, add/remove a watchlist member, create a portfolio, add/edit a position, reload the browser, restart PostgreSQL normally, verify the position persists, check Swagger, and inspect Java/Python/DB health. Stop normally without removing volumes. Provider outages must produce explicit errors with cached data still usable.

## Browser QA

On Windows with Microsoft Edge installed and a Vite server on :5173:

```powershell
node infrastructure/scripts/browser-qa.mjs
```

This helper uses an isolated `.cache/edge-cdp` profile, a loopback-only debugging endpoint, true 1440/390px viewport emulation, bounded waits, screenshots and a light-theme click. It never uses the user's browser profile or downloads a browser. Override EDGE_PATH if installed elsewhere. Desktop/mobile screenshots and dimensions are stored in ignored `.cache/`. It closes its owned browser after the check. Current QA verified the backend-unavailable UI, not real-data chart behavior. Do not leave debugging browsers running.

## Dependency / repository hygiene

Use `npm ci` for reproducible installs; review dependency updates deliberately. Python direct pins and `requirements.lock.txt` constraints are committed; regenerate the lock only in a clean intended environment and test Python 3.12 as well as the local host. Maven uses the pinned parent/BOM. Do not check in `.env`, `.venv`, `.tools`, `.cache`, node_modules, target, dist, screenshots or credentials. No Git repository was present at inspection; ignore rules/CI are prepared, but no remote, commit, push or hosted deployment was created.
