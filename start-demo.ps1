# Run from PowerShell. Demo data disappears when the backend process stops.
$ErrorActionPreference = 'Stop'
$demoEnvironmentKeys = @('JWT_SECRET', 'BOOTSTRAP_ADMIN_EMAIL', 'BOOTSTRAP_ADMIN_PASSWORD')
$previousDemoEnvironment = @{}
foreach ($demoKey in $demoEnvironmentKeys) {
    $previousDemoEnvironment[$demoKey] = [Environment]::GetEnvironmentVariable($demoKey, 'Process')
}

function New-DemoSecret {
    $demoBytes = New-Object byte[] 48
    $demoRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $demoRandom.GetBytes($demoBytes) } finally { $demoRandom.Dispose() }
    return [Convert]::ToBase64String($demoBytes)
}

Push-Location $PSScriptRoot
try {
    $env:JWT_SECRET = New-DemoSecret
    $env:BOOTSTRAP_ADMIN_EMAIL = 'admin@sekkin.local'
    $env:BOOTSTRAP_ADMIN_PASSWORD = New-DemoSecret
    Write-Host 'Local demo API: http://localhost:8080'
    Write-Host "Demo admin email: $env:BOOTSTRAP_ADMIN_EMAIL"
    Write-Host "Temporary demo admin password: $env:BOOTSTRAP_ADMIN_PASSWORD"
    Write-Host 'Use this account at http://localhost:5173/login, then open /admin. Ctrl+C stops the backend.'
    & .\mvnw.cmd -Pdemo spring-boot:run '-Dspring-boot.run.profiles=demo,local-admin-bootstrap'
    if ($LASTEXITCODE -ne 0) { throw 'Demo backend did not exit successfully. Check the startup error above.' }
} finally {
    foreach ($demoKey in $demoEnvironmentKeys) {
        [Environment]::SetEnvironmentVariable($demoKey, $previousDemoEnvironment[$demoKey], 'Process')
    }
    Pop-Location
}
