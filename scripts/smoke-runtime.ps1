param(
    [string]$Image = 'sekkin-backend-runtime-remediated:20261116',
    [string]$PostgresImage = 'postgres:17-alpine'
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$taskNetwork = "sekkin-runtime-net-$taskSuffix"
$taskDatabase = "sekkin-runtime-pg-$taskSuffix"
$taskApplication = "sekkin-runtime-app-$taskSuffix"
$taskEvidence = [ordered]@{
    checkedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
    scope = 'Actual local non-root application container and disposable PostgreSQL; synthetic fixtures only'
    image = $Image
    postgresImage = $PostgresImage
    productionDataChanged = $false
    hostedRuntimeVerified = $false
    checks = @()
    passed = $false
}
function Assert-TaskCheck([bool]$Condition, [string]$Name) {
    if (!$Condition) { throw "Runtime check failed: $Name" }
    $taskEvidence.checks += $Name
}
function Invoke-TaskHttp([string]$Method, [string]$Path, [string]$Body = '', [hashtable]$Headers = @{}) {
    $taskParameters = @{ Uri = "$taskBase$Path"; Method = $Method; UseBasicParsing = $true; TimeoutSec = 8; Headers = $Headers }
    if ($Body) { $taskParameters.Body = $Body; $taskParameters.ContentType = 'application/json' }
    try { return Invoke-WebRequest @taskParameters }
    catch {
        if ($_.Exception.Response) { return $_.Exception.Response }
        throw
    }
}
Push-Location $taskRoot
try {
    $taskImageDetails = (& docker image inspect $Image | ConvertFrom-Json)[0]
    if ($LASTEXITCODE -ne 0 -or !$taskImageDetails) { throw 'Build the intended local runtime image first.' }
    $taskEvidence.imageId = $taskImageDetails.Id
    $taskEvidence.imageDigests = @($taskImageDetails.RepoDigests)
    $taskEvidence.configuredUser = $taskImageDetails.Config.User
    Assert-TaskCheck ($taskImageDetails.Config.User -eq '10001:10001') 'Image explicitly configures numeric non-root UID/GID 10001'
    $taskEvidence.jarSha256 = (Get-FileHash -LiteralPath 'target/cosmetic-ecommerce-backend-0.0.1-SNAPSHOT.jar' -Algorithm SHA256).Hash.ToLowerInvariant()
    $taskEvidence.migrationSha256 = (Get-FileHash -LiteralPath 'docs/migrations/2026-11-16-order-session-safety.sql' -Algorithm SHA256).Hash.ToLowerInvariant()
    & docker network create --label sekkin.release-verification=true $taskNetwork 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the task-owned network.' }
    # No host port is published for PostgreSQL. Trust is limited to this disposable task network.
    & docker run -d --name $taskDatabase --label sekkin.release-verification=true --network $taskNetwork `
        --memory 384m --cpus 1 -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=sekkin_test `
        -e POSTGRES_DB=sekkin_release_test $PostgresImage 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the disposable PostgreSQL container.' }
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 40; $taskAttempt++) {
        $taskProcess = (& docker exec $taskDatabase cat /proc/1/comm).Trim()
        if ($taskProcess -eq 'postgres') {
            & docker exec $taskDatabase pg_isready -U sekkin_test -d sekkin_release_test 1>$null 2>$null
            if ($LASTEXITCODE -eq 0) { $taskReady = $true; break }
        }
        Start-Sleep -Seconds 1
    }
    if (!$taskReady) { throw 'The isolated database did not start.' }
    $taskEvidence.postgresqlVersion = (& docker exec $taskDatabase postgres --version).Trim()
    foreach ($taskSql in @('src/test/resources/postgres/legacy-schema.sql', 'docs/migrations/2026-11-16-order-session-safety.sql')) {
        & docker cp $taskSql "${taskDatabase}:/tmp/sekkin-fixture.sql"
        if ($LASTEXITCODE -ne 0) { throw 'Could not copy the synthetic fixture or reviewed migration.' }
        & docker exec $taskDatabase psql -U sekkin_test -d sekkin_release_test -v ON_ERROR_STOP=1 -f /tmp/sekkin-fixture.sql 1>$null
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic schema/migration rehearsal failed.' }
    }
    Assert-TaskCheck $true 'Legacy synthetic schema accepts the exact manual migration'
    # Deliberately public synthetic signing material, confined to this ephemeral test process.
    # No deployment environments, .env files, external credentials, or Cloudinary accounts are read.
    & docker run -d --name $taskApplication --label sekkin.release-verification=true --network $taskNetwork `
        --memory 512m --cpus 2 -p '127.0.0.1::8080' `
        -e JAVA_TOOL_OPTIONS=-Xmx256m -e JWT_SECRET=synthetic-runtime-verification-key-not-a-production-secret `
        -e "SPRING_DATASOURCE_URL=jdbc:postgresql://${taskDatabase}:5432/sekkin_release_test?sslmode=disable" `
        -e SPRING_DATASOURCE_USERNAME=sekkin_test -e SPRING_DATASOURCE_PASSWORD= `
        -e JPA_DDL_AUTO=validate -e SPRING_PROFILES_ACTIVE=isolated-runtime-verification `
        -e CHECKOUT_ENABLED=true -e CORS_ALLOWED_ORIGINS=https://isolated.sekkin.test $Image 1>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start the application runtime container.' }
    $taskContainerDetails = (& docker inspect $taskApplication | ConvertFrom-Json)[0]
    $taskBinding = $taskContainerDetails.NetworkSettings.Ports.'8080/tcp'[0]
    Assert-TaskCheck ($taskBinding.HostIp -eq '127.0.0.1' -and !!$taskBinding.HostPort) 'Application HTTP port is exposed only on loopback'
    $taskBase = "http://127.0.0.1:$($taskBinding.HostPort)"
    $taskReady = $false
    for ($taskAttempt = 0; $taskAttempt -lt 90; $taskAttempt++) {
        try {
            $taskResponse = Invoke-TaskHttp 'GET' '/api/products'
            if ([int]$taskResponse.StatusCode -eq 200) { $taskReady = $true; break }
        } catch { }
        if ((& docker inspect --format '{{.State.Running}}' $taskApplication).Trim() -ne 'true') { break }
        Start-Sleep -Seconds 1
    }
    if (!$taskReady) { throw 'Application did not pass startup/schema validation and serve public products.' }
    Assert-TaskCheck $true 'Actual application starts with Hibernate validate and serves public product HTTP requests'
    $taskUid = (& docker exec $taskApplication id -u).Trim()
    Assert-TaskCheck ($taskUid -eq '10001') 'Running application process uses UID 10001'
    & docker exec $taskApplication sh -c 'test -r /app/app.jar && test ! -w /app/app.jar && test -w /tmp'
    Assert-TaskCheck ($LASTEXITCODE -eq 0) 'Application JAR is readable but not writable; temporary upload storage remains writable'
    $taskImageJarHash = ((& docker exec $taskApplication sha256sum /app/app.jar) -split '\s+')[0]
    Assert-TaskCheck ($taskImageJarHash -eq $taskEvidence.jarSha256) 'Running image contains the exact 209-test packaged JAR'
    & docker exec $taskApplication sh -c '! apk --no-network info -e sqlite-libs >/dev/null 2>&1 && ! apk --no-network info -e gnupg >/dev/null 2>&1'
    Assert-TaskCheck ($LASTEXITCODE -eq 0) 'Unused GnuPG and its vulnerable SQLite library are absent'
    $taskZlibEntry = @(& docker exec $taskApplication grep -A 1 '^P:zlib$' /lib/apk/db/installed)
    $taskEvidence.zlibVersion = ($taskZlibEntry | Where-Object { $_ -match '^V:' }) -replace '^V:', ''
    Assert-TaskCheck ($taskEvidence.zlibVersion -match '^1\.4(?:\.|-)' ) 'Installed zlib contains the Alpine 1.4 vendor fix'
    $taskOrderBody = @{ productId = 900001; quantity = 1; name = 'Synthetic runtime buyer'; phoneNumber = 'synthetic-test-phone'; address = 'Synthetic isolated address'; email = 'runtime@example.test'; paymentMethod = 'COD'; expectedTotal = 1350.00 } | ConvertTo-Json -Compress
    $taskKey = [Guid]::NewGuid().ToString()
    $taskFirstResponse = Invoke-TaskHttp 'POST' '/api/orders/direct' $taskOrderBody @{ 'Idempotency-Key' = $taskKey }
    Assert-TaskCheck ([int]$taskFirstResponse.StatusCode -eq 200) 'Guest COD order succeeds through the actual embedded HTTP server'
    $taskFirstOrder = $taskFirstResponse.Content | ConvertFrom-Json
    Assert-TaskCheck ($taskFirstOrder.deliveryCharge -eq 350 -and $taskFirstOrder.totalAmount -eq 1350 -and $taskFirstOrder.paymentStatus -eq 'PENDING_COD') 'Runtime receipt records fixed LKR 350 and pending COD, never automatic PAID'
    $taskRetry = Invoke-TaskHttp 'POST' '/api/orders/direct' $taskOrderBody @{ 'Idempotency-Key' = $taskKey }
    Assert-TaskCheck ([int]$taskRetry.StatusCode -eq 200 -and ($taskRetry.Content | ConvertFrom-Json).orderId -eq $taskFirstOrder.orderId) 'Repeated idempotent HTTP request returns the same order'
    $taskDatabaseState = (& docker exec $taskDatabase psql -U sekkin_test -d sekkin_release_test -A -t -v ON_ERROR_STOP=1 `
        -c "SELECT (SELECT count(*) FROM orders), (SELECT stock_quantity FROM products WHERE id=900001), (SELECT total_amount FROM orders WHERE id=900001), (SELECT delivery_charge FROM orders WHERE id=900001);" | Out-String).Trim()
    Assert-TaskCheck ($taskDatabaseState -eq '2|9|1000|0') 'Database has one new order, one stock decrement, and unchanged historical zero-delivery totals'
    $taskAdmin = Invoke-TaskHttp 'GET' '/api/admin/orders'
    Assert-TaskCheck ([int]$taskAdmin.StatusCode -in @(401, 403)) 'Anonymous runtime request cannot read admin orders'
    $taskPreflight = Invoke-TaskHttp 'OPTIONS' '/api/orders/direct' '' @{ Origin='https://isolated.sekkin.test'; 'Access-Control-Request-Method'='POST'; 'Access-Control-Request-Headers'='content-type,idempotency-key' }
    Assert-TaskCheck ([int]$taskPreflight.StatusCode -eq 200 -and $taskPreflight.Headers['Access-Control-Allow-Origin'] -eq 'https://isolated.sekkin.test') 'Actual runtime CORS accepts only the configured test browser origin'
    $taskEvidence.passed = $true
    Write-Host "Runtime smoke passed $($taskEvidence.checks.Count) checks against isolated PostgreSQL."
} finally {
    $taskPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $taskEvidence.cleanupCompleted = $true
    foreach ($taskName in @($taskApplication, $taskDatabase)) {
        $taskOwned = & docker inspect $taskName 2>$null | ConvertFrom-Json
        if ($taskOwned -and $taskName -match '^sekkin-runtime-(app|pg)-[0-9a-f]{12}$' -and $taskOwned[0].Config.Labels.'sekkin.release-verification' -eq 'true') {
            & docker rm -f -v $taskName 1>$null
            if ($LASTEXITCODE -ne 0) { $taskEvidence.cleanupCompleted = $false }
        }
    }
    $taskOwnedNetwork = & docker network inspect $taskNetwork 2>$null | ConvertFrom-Json
    if ($taskOwnedNetwork -and $taskNetwork -match '^sekkin-runtime-net-[0-9a-f]{12}$' -and $taskOwnedNetwork[0].Labels.'sekkin.release-verification' -eq 'true') {
        & docker network rm $taskNetwork 1>$null
        if ($LASTEXITCODE -ne 0) { $taskEvidence.cleanupCompleted = $false }
    }
    [IO.File]::WriteAllText((Join-Path $taskRoot 'docs/verification/runtime-smoke.json'), ($taskEvidence | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
    Pop-Location
    $ErrorActionPreference = $taskPreference
}
