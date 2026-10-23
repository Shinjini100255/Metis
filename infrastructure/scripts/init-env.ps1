$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$target = Join-Path $repo '.env'
if (Test-Path -LiteralPath $target) { Write-Output '.env already exists; keep your existing credentials.'; exit 0 }
$template = [IO.File]::ReadAllText((Join-Path $repo '.env.example'))
$buffer = New-Object byte[] 24
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
$secret = [Convert]::ToBase64String($buffer)
$content = $template.Replace('POSTGRES_PASSWORD=', "POSTGRES_PASSWORD=$secret")
[IO.File]::WriteAllText($target, $content, (New-Object Text.UTF8Encoding $false))
Write-Output '.env created with a random local database password. Add your market API key if needed. Keep this file private.'
