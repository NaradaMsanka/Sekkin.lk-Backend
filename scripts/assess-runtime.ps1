param(
    [string]$RuntimeImage = 'sekkin-backend-runtime-remediated:20261116',
    [string]$GrypePath = '',
    [switch]$UseCachedDatabase
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (!$GrypePath) { $GrypePath = Join-Path $taskRoot 'target/security-tools/grype-0.121.3/grype.exe' }
if (!(Test-Path -LiteralPath $GrypePath)) { throw 'Supply a verified official Grype executable using -GrypePath.' }
$taskOutput = Join-Path $taskRoot 'docs/verification'
$taskArchive = Join-Path $taskRoot 'target/security-tools/runtime-remediated.tar'
$taskJar = Join-Path $taskRoot 'target/cosmetic-ecommerce-backend-0.0.1-SNAPSHOT.jar'
$taskSaved = @{}
foreach ($taskName in @('GRYPE_DB_CACHE_DIR','GRYPE_DB_AUTO_UPDATE','GRYPE_CHECK_FOR_APP_UPDATE','GOMEMLIMIT','TMP','TEMP')) {
    $taskSaved[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process')
}
function Invoke-TaskGrype([string]$Target, [string]$Format, [string]$File) {
    # Native informational stderr must not become a terminating PowerShell 5 error.
    $taskPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $GrypePath $Target -o $Format --file (Join-Path $taskOutput $File)
        $taskExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $taskPreference }
    if ($taskExit -ne 0) { throw "Grype failed for $File (exit $taskExit)." }
}
function Get-TaskAssessment([string]$File) {
    $taskResult = Get-Content -LiteralPath (Join-Path $taskOutput $File) -Raw | ConvertFrom-Json
    if (!$taskResult.descriptor.db.status.valid) { throw "The advisory database is not valid for $File." }
    $taskSeverities = [ordered]@{}
    foreach ($taskLevel in @('Critical','High','Medium','Low','Negligible','Unknown')) {
        $taskSeverities[$taskLevel] = @($taskResult.matches | Where-Object { $_.vulnerability.severity -eq $taskLevel }).Count
    }
    return [ordered]@{
        rawReport = $File
        scanner = $taskResult.descriptor.name
        scannerVersion = $taskResult.descriptor.version
        sourceType = $taskResult.source.type
        scannedImageConfigId = $taskResult.source.target.imageID
        scannedImageManifestDigest = $taskResult.source.target.manifestDigest
        database = $taskResult.descriptor.db.status
        scannedAt = $taskResult.descriptor.timestamp
        packageAdvisoryMatches = @($taskResult.matches).Count
        uniqueAdvisories = @($taskResult.matches | ForEach-Object { $_.vulnerability.id } | Sort-Object -Unique).Count
        severities = $taskSeverities
        findings = @($taskResult.matches | ForEach-Object {
            [ordered]@{ package=$_.artifact.name; version=$_.artifact.version; advisory=$_.vulnerability.id; severity=$_.vulnerability.severity; fixState=$_.vulnerability.fix.state; fixedVersions=@($_.vulnerability.fix.versions); source=$_.vulnerability.dataSource }
        })
        # No --only-fixed, VEX, local ignore rules, or suppression document is applied.
        localSuppressionApplied = $false
    }
}
Push-Location $taskRoot
try {
    $taskTemp = Join-Path $taskRoot 'target/security-tools/tmp'
    New-Item -ItemType Directory -Force -Path $taskOutput, (Split-Path -Parent $taskArchive), $taskTemp | Out-Null
    $env:TMP = $taskTemp
    $env:TEMP = $taskTemp
    $env:GRYPE_DB_CACHE_DIR = Join-Path $taskRoot 'target/security-tools/grype-db'
    $env:GRYPE_DB_AUTO_UPDATE = if ($UseCachedDatabase) { 'false' } else { 'true' }
    $env:GRYPE_CHECK_FOR_APP_UPDATE = 'false'
    $env:GOMEMLIMIT = '512MiB'
    $taskImage = (& docker image inspect $RuntimeImage | ConvertFrom-Json)[0]
    if ($LASTEXITCODE -ne 0 -or !$taskImage) { throw 'Build the intended local runtime image first.' }
    & docker save --output $taskArchive $RuntimeImage
    if ($LASTEXITCODE -ne 0) { throw 'Could not export the runtime image for assessment.' }
    Invoke-TaskGrype "file:$taskJar" 'json' 'backend-grype.json'
    Invoke-TaskGrype "file:$taskJar" 'cyclonedx-json' 'backend-runtime.cdx.json'
    Invoke-TaskGrype "docker-archive:$taskArchive" 'json' 'runtime-remediated-grype.json'
    Invoke-TaskGrype "docker-archive:$taskArchive" 'cyclonedx-json' 'runtime-remediated.cdx.json'
    $taskJavaBom = Get-Content -LiteralPath (Join-Path $taskOutput 'backend-runtime.cdx.json') -Raw | ConvertFrom-Json
    $taskRuntimeBom = Get-Content -LiteralPath (Join-Path $taskOutput 'runtime-remediated.cdx.json') -Raw | ConvertFrom-Json
    $taskSummary = [ordered]@{
        checkedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        scope = 'Local tested application JAR and runtime-stage image; not a full source Maven Docker builder assessment or hosted verification'
        jarSha256 = (Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLowerInvariant()
        runtimeImage = $RuntimeImage
        runtimeImageId = $taskImage.Id
        runtimeImageDigests = @($taskImage.RepoDigests)
        runtimeConfiguredUser = $taskImage.Config.User
        runtimeArchiveSha256 = (Get-FileHash -LiteralPath $taskArchive -Algorithm SHA256).Hash.ToLowerInvariant()
        cachedDatabaseRequested = [bool]$UseCachedDatabase
        jarSbomComponents = @($taskJavaBom.components).Count
        runtimeSbomComponents = @($taskRuntimeBom.components).Count
        runtimeVersionedPurlComponents = @($taskRuntimeBom.components | Where-Object { $_.purl }).Count
        jar = Get-TaskAssessment 'backend-grype.json'
        runtime = Get-TaskAssessment 'runtime-remediated-grype.json'
    }
    [IO.File]::WriteAllText((Join-Path $taskOutput 'dependency-assessment.json'), ($taskSummary | ConvertTo-Json -Depth 14), (New-Object Text.UTF8Encoding($false)))
    Write-Host "Current advisory results: JAR $($taskSummary.jar.packageAdvisoryMatches); runtime $($taskSummary.runtime.packageAdvisoryMatches) matches / $($taskSummary.runtime.uniqueAdvisories) unique advisories."
} finally {
    foreach ($taskName in $taskSaved.Keys) { [Environment]::SetEnvironmentVariable($taskName, $taskSaved[$taskName], 'Process') }
    Pop-Location
}
