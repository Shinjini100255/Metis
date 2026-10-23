$ErrorActionPreference = 'Continue'
Write-Output "METIS environment inspection (read-only)"
Get-Location
foreach ($tool in @('git', 'java', 'node', 'npm', 'python', 'docker', 'wsl', 'mvn')) {
    $command = Get-Command $tool -ErrorAction SilentlyContinue
    if ($command) { Write-Output "$tool : $($command.Source)" } else { Write-Output "$tool : MISSING" }
}
Get-PSDrive -PSProvider FileSystem | Select-Object Name,Used,Free
if (Get-Command docker -ErrorAction SilentlyContinue) { docker version; docker compose version; docker info --format '{{.DockerRootDir}}' }
if (Get-Command wsl -ErrorAction SilentlyContinue) { wsl --status }
foreach ($file in @("$env:APPDATA\Docker\settings-store.json", "$env:APPDATA\Docker\settings.json")) {
 if (Test-Path -LiteralPath $file) {
   $settings = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
   $settings | Select-Object diskImageLocation,dataFolder,memoryMiB,cpus
 }
}
