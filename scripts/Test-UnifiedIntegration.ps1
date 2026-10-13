param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskContainer = 'wautech-unified-integration-fixture'
$taskStarted = $false
$taskEnvironment = @{}
foreach ($taskName in @('POSTGRES_PASSWORD','WAUTECH_TEST_DB_PASSWORD','WAUTECH_TEST_DB_URL')) {
    $taskEnvironment[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process')
}
Push-Location $taskRoot
try {
    $taskExisting = docker ps -a --filter "name=^/$taskContainer`$" --format '{{.Names}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker must be running.' }
    if ($taskExisting) { throw 'A fixture container already exists; refusing to replace it.' }
    $env:POSTGRES_PASSWORD = [guid]::NewGuid().ToString()
    $env:WAUTECH_TEST_DB_PASSWORD = $env:POSTGRES_PASSWORD
    $env:WAUTECH_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/wautech_integration_fixture'
    docker run --rm -d --name $taskContainer --tmpfs /var/lib/postgresql/data -e POSTGRES_PASSWORD -e POSTGRES_USER=fixture -e POSTGRES_DB=wautech_integration_fixture -p 127.0.0.1:15432:5432 postgres:17 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start isolated fixture PostgreSQL. Existing databases have not been touched.' }
    $taskStarted = $true
    & .\backend\mvnw.cmd -f backend/pom.xml '-Dtest=UnifiedFrontendLiveTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Live integration tests failed.' }
} finally {
    if ($taskStarted) { docker stop $taskContainer | Out-Null }
    foreach ($taskName in $taskEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($taskName, $taskEnvironment[$taskName], 'Process')
    }
    Pop-Location
}
