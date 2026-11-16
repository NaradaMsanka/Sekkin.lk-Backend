param(
    [string]$Image = 'postgres:17-alpine',
    [switch]$Online
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (!(Test-Path -LiteralPath (Join-Path $taskRoot 'pom.xml'))) { throw 'Run this repository verification script from the Backend checkout.' }
$taskSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$taskContainer = "sekkin-pg-verify-$taskSuffix"
$taskNetwork = "sekkin-pg-net-$taskSuffix"
$taskLog = Join-Path ([IO.Path]::GetTempPath()) "sekkin-postgres-$taskSuffix.log"
$taskSavedRun = [Environment]::GetEnvironmentVariable('SEKKIN_RUN_POSTGRES_TESTS', 'Process')
$taskSavedUrl = [Environment]::GetEnvironmentVariable('SEKKIN_POSTGRES_TEST_URL', 'Process')
$taskSavedMaven = [Environment]::GetEnvironmentVariable('MAVEN_OPTS', 'Process')
$taskExit = 1
$taskEvidence = [ordered]@{
    checkedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    scope = 'Disposable loopback PostgreSQL; synthetic legacy schema and records only; no hosted verification'
    image = $Image
    database = 'sekkin_release_test'
    tls = 'Not used in the disposable loopback trust fixture; hosted TLS/CA/proxy settings still require operator verification'
    productionDataChanged = $false
    fullPackage = $false
}
Push-Location $taskRoot
try {
    & docker info --format '{{.ServerVersion}}' 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'The existing Docker daemon must be running.' }
    & docker pull $Image
    if ($LASTEXITCODE -ne 0) { throw 'Could not pull the official PostgreSQL test image.' }
    & docker network create --label sekkin.release-verification=true $taskNetwork 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the task-owned test network.' }
    & docker run -d --name $taskContainer --label sekkin.release-verification=true --network $taskNetwork `
        --memory 512m --cpus 2 -p '127.0.0.1::5432' -e POSTGRES_HOST_AUTH_METHOD=trust `
        -e POSTGRES_USER=sekkin_test -e POSTGRES_DB=sekkin_release_test $Image 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the disposable PostgreSQL container.' }
    $taskDetails = (& docker inspect $taskContainer | ConvertFrom-Json)[0]
    $taskBinding = $taskDetails.NetworkSettings.Ports.'5432/tcp'[0]
    if ($taskBinding.HostIp -ne '127.0.0.1' -or !$taskBinding.HostPort) { throw 'PostgreSQL must be published to loopback only.' }
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 30; $taskAttempt++) {
        # Official-image initialization briefly runs a temporary server before creating the requested DB.
        # Wait for the final PID1 server rather than mistaking that temporary pg_isready response for readiness.
        $taskMainProcess = (& docker exec $taskContainer cat /proc/1/comm).Trim()
        if ($taskMainProcess -ne 'postgres') { Start-Sleep -Seconds 1; continue }
        & docker exec $taskContainer pg_isready -U sekkin_test -d sekkin_release_test 1>$null 2>$null
        if ($LASTEXITCODE -eq 0) { $taskReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$taskReady) { throw 'The isolated database did not become ready.' }
    & docker exec $taskContainer psql -U sekkin_test -d sekkin_release_test -v ON_ERROR_STOP=1 `
        -c "COMMENT ON DATABASE sekkin_release_test IS 'SEKKIN_DISPOSABLE_RELEASE_VERIFICATION';" 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not mark the disposable database.' }
    $taskEvidence.postgresqlVersion = (& docker exec $taskContainer postgres --version).Trim()
    $taskEvidence.imageDigests = @((& docker image inspect $Image | ConvertFrom-Json)[0].RepoDigests)
    $taskEvidence.publishedHost = '127.0.0.1'
    $taskEvidence.migrationSha256 = (Get-FileHash -LiteralPath 'docs/migrations/2026-11-16-order-session-safety.sql' -Algorithm SHA256).Hash.ToLowerInvariant()
    $env:SEKKIN_RUN_POSTGRES_TESTS = 'true'
    $env:SEKKIN_POSTGRES_TEST_URL = "jdbc:postgresql://127.0.0.1:$($taskBinding.HostPort)/sekkin_release_test"
    $env:MAVEN_OPTS = '-Xmx256m'
    $taskArguments = @('-B', '-DargLine=-Xmx512m', '-Dspring.test.context.cache.maxSize=4', 'package')
    if (!$Online) { $taskArguments = @('-o') + $taskArguments }
    Write-Host 'Running the complete backend package and real PostgreSQL integration tests on synthetic data.'
    # Windows PowerShell 5.1 otherwise treats harmless JVM stderr notices as terminating errors.
    $taskNativePreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & (Join-Path $taskRoot 'mvnw.cmd') @taskArguments *> $taskLog
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskNativePreference }
    $taskEvidence.packageExitCode = $taskExit
    $taskEvidence.fullPackage = $taskExit -eq 0
    $taskSuites = @()
    foreach ($taskXml in Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter 'TEST-*.xml') {
        [xml]$taskResult = Get-Content -LiteralPath $taskXml.FullName -Raw
        $taskSuite = $taskResult.testsuite
        $taskSuites += [pscustomobject][ordered]@{ name = [string]$taskSuite.name; tests = [int]$taskSuite.tests; failures = [int]$taskSuite.failures; errors = [int]$taskSuite.errors; skipped = [int]$taskSuite.skipped }
    }
    $taskEvidence.suites = $taskSuites
    $taskEvidence.tests = ($taskSuites | Measure-Object -Property tests -Sum).Sum
    $taskEvidence.failures = ($taskSuites | Measure-Object -Property failures -Sum).Sum
    $taskEvidence.errors = ($taskSuites | Measure-Object -Property errors -Sum).Sum
    $taskEvidence.skipped = ($taskSuites | Measure-Object -Property skipped -Sum).Sum
    $taskEvidence.postgresqlTests = @($taskSuites | Where-Object { $_.name -eq 'com.cosmetic.ecommerce.postgres.PostgresReleaseIntegrationTest' })
    if ($taskExit -eq 0) { $taskEvidence.jarSha256 = (Get-FileHash -LiteralPath 'target/cosmetic-ecommerce-backend-0.0.1-SNAPSHOT.jar' -Algorithm SHA256).Hash.ToLowerInvariant() }
    Get-Content -LiteralPath $taskLog -Tail 12
    if ($taskExit -ne 0) { throw 'The backend/PostgreSQL verification failed; inspect the temporary test log.' }
} finally {
    [Environment]::SetEnvironmentVariable('SEKKIN_RUN_POSTGRES_TESTS', $taskSavedRun, 'Process')
    [Environment]::SetEnvironmentVariable('SEKKIN_POSTGRES_TEST_URL', $taskSavedUrl, 'Process')
    [Environment]::SetEnvironmentVariable('MAVEN_OPTS', $taskSavedMaven, 'Process')
    # Remove only this invocation's generated, labelled resources, including its anonymous data volume.
    $taskSavedErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $taskCleanupConfirmed = $true
    & docker info --format '{{.ServerVersion}}' 1>$null 2>$null
    $taskDaemonAvailable = $LASTEXITCODE -eq 0
    if (!$taskDaemonAvailable) { $taskCleanupConfirmed = $false }
    $taskOwnedContainer = & docker inspect $taskContainer 2>$null | ConvertFrom-Json
    if ($taskOwnedContainer -and $taskContainer -match '^sekkin-pg-verify-[0-9a-f]{12}$' `
            -and $taskOwnedContainer[0].Config.Labels.'sekkin.release-verification' -eq 'true') {
        & docker rm -f -v $taskContainer 1>$null
        if ($LASTEXITCODE -ne 0) { $taskCleanupConfirmed = $false }
    }
    $taskOwnedNetwork = & docker network inspect $taskNetwork 2>$null | ConvertFrom-Json
    if ($taskOwnedNetwork -and $taskNetwork -match '^sekkin-pg-net-[0-9a-f]{12}$' `
            -and $taskOwnedNetwork[0].Labels.'sekkin.release-verification' -eq 'true') {
        & docker network rm $taskNetwork 1>$null
        if ($LASTEXITCODE -ne 0) { $taskCleanupConfirmed = $false }
    }
    $taskEvidence.cleanupCompleted = $taskCleanupConfirmed
    if (!$taskCleanupConfirmed) { $taskEvidence.cleanupPending = @($taskContainer, $taskNetwork) }
    New-Item -ItemType Directory -Path 'docs/verification' -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $taskRoot 'docs/verification/postgres-release.json'),
        ($taskEvidence | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    Pop-Location
    $ErrorActionPreference = $taskSavedErrorAction
}
