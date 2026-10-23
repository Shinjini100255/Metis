$ErrorActionPreference = 'Stop'
$urls = @('http://127.0.0.1:5173/', 'http://127.0.0.1:5173/api/v1/assets', 'http://127.0.0.1:8080/actuator/health', 'http://127.0.0.1:8000/health', 'http://127.0.0.1:8080/v3/api-docs')
foreach ($url in $urls) {
    $response = Invoke-WebRequest -Uri $url -TimeoutSec 15
    if ($response.StatusCode -ne 200) { throw "Failed: $url" }
    Write-Output "PASS $url"
}
$status = Invoke-RestMethod 'http://127.0.0.1:5173/api/v1/system/status'
if ($status.postgres.status -ne 'UP' -or $status.ml.status -ne 'UP') { throw 'Database or Java-to-Python communication is unhealthy.' }
docker compose exec -T postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) { throw 'Database readiness failed.' }
Write-Output 'PASS service readiness and frontend API proxy'
