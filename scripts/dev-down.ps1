$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $Root

docker compose down
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
