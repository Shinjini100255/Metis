# Docker / WSL2 setup and operations

## Host state

WSL status was checked outside the sandbox after the initial read-only command was denied: Ubuntu, default version 2. Docker/Compose executables and Docker Desktop installation/settings were not found. No Docker image/volume location could be established. No system installation, feature enablement, reboot, storage relocation or Kubernetes operation was attempted.

Install Docker Desktop yourself if desired, select Linux containers and WSL2 integration, then check `docker version`, `docker compose version`, and `wsl --status`. Installing it requires your explicit system-level approval; this repository does not automate the installation.

## Storage

Keep the checkout on D:. Before the first image build, inspect Docker Desktop's disk-image/storage setting. Docker's Linux `/var/lib/docker` path is inside its VM and does not establish the Windows VHDX location. Use the supported Desktop relocation control if C: space is constrained, back up existing application data, and shut down workloads first. Do not manually move a VHDX or edit unrelated settings.

## Four-service stack

| Service | Internal port | Host publication | Memory ceiling | CPU ceiling |
|---|---|---|---|---|
| postgres | 5432 | None | 256 MiB | 0.5 |
| spring-backend | 8080 | 127.0.0.1:8080 | 768 MiB | 1.0 |
| ml-api | 8000 | 127.0.0.1:8000 | 256 MiB | 0.5 |
| frontend | 8080 | 127.0.0.1:5173 | 128 MiB | 0.25 |

Total runtime ceilings: 1,408 MiB. This excludes Docker/WSL and image-build memory. Actual consumption is not measured because containers could not be launched. Start with around 3 GB VM memory / two CPUs as an estimate; build sequentially and measure with `docker stats --no-stream` after startup. Do not edit `.wslconfig` automatically.

Images use Java 21, Python 3.12 slim, PostgreSQL 17 Alpine, Node build stage and unprivileged Nginx. Java and frontend builds are multi-stage; Python wheels are built separately and installed without a runtime compiler. No Redis, MLflow, Grafana, Prometheus or Kubernetes is launched. Container restart policy is deliberately `no` for development; crashes stay visible. Logs use json-file, 5 MB × two files/service.

## Startup / readiness

Run from the repository root:

```powershell
.\infrastructure\scripts\init-env.ps1
# Add ALPHA_VANTAGE_API_KEY to .env only if you want to ingest real history.
docker compose config --quiet
docker compose --parallel 1 build
docker compose up -d --wait --wait-timeout 240
.\infrastructure\scripts\smoke.ps1
```

PostgreSQL readiness gates Java startup. Python readiness also gates Java startup. Java's Actuator health gates frontend startup. Java reaches PostgreSQL as `postgres:5432` and Python as `ml-api:8000`. Nginx reaches Java as `spring-backend:8080`. Using `localhost` for these internal service-to-service URLs is incorrect.

For a minimal native-development database, start only `docker compose up -d postgres` and follow the loopback-only port override in DEVELOPMENT.md. For frontend development against the container API, start `docker compose up -d spring-backend` (its two dependencies are started), then `npm run dev --prefix frontend`; do not start the frontend container on the same port.

## Diagnostics

```powershell
docker compose ps
docker compose logs --tail 80 postgres
docker compose logs --tail 80 spring-backend
docker compose logs --tail 80 ml-api
docker compose logs --tail 80 frontend
```

- **docker not recognized:** Docker Desktop/CLI is missing or not on PATH. Install/repair explicitly; restart the terminal afterward.
- **daemon unavailable:** start Docker Desktop, wait for Linux engine readiness, inspect WSL integration. WSL2 availability alone is insufficient.
- **empty password:** fill POSTGRES_PASSWORD in your existing .env; the init helper deliberately preserves an existing file.
- **password authentication failed with existing volume:** restore the original credentials or perform a deliberate database password rotation. Editing .env does not rotate a database password. Preserve the volume.
- **migration checksum mismatch:** restore the original applied migration and add a new version for the change; never enable automatic Flyway clean/repair as a shortcut.
- **Java killed / low memory:** close unrelated apps, use sequential builds, inspect container limits and host pressure. Heap limit is 384 MB; native memory/metaspace requires additional headroom.
- **frontend API errors:** check Java health and its database dependency; Vite proxies 127.0.0.1:8080, Nginx proxies internal service DNS. No browser-facing provider keys are required.
- **provider not configured/quota/network:** inspect System Health and ingestion activity; correct the key privately, respect local/provider budgets, retry manually later. Existing cached data remains readable.
- **C: fills despite D: checkout:** inspect the Desktop storage location and use supported relocation after backup and explicit approval. Do not prune unrelated images or volumes.

## Shutdown and backup

`docker compose stop` preserves containers and the database volume. `docker compose down` removes this stack's containers/network and preserves the named volume. `down -v` is destructive and is never part of ordinary shutdown. Before changing PostgreSQL major versions, use pg_dump/pg_restore with a tested backup. SQL migrations and the volume are independent: deleting a volume deletes user holdings/history.

## Verification boundary

Compose YAML, Dockerfiles, health checks and CI are implemented. Docker CLI configuration validation, image builds, container readiness, persistent-volume restart, Swagger, Java-to-Python and browser-to-database end-to-end behavior have **not** been run here. CI is configured to exercise them when a Docker-capable runner is available.
