param([switch]$Demo)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$previousPassword = $env:DB_PASSWORD
$previousDemo = $env:MDM_DEMO
$previousDemoPassword = $env:MDM_DEMO_PASSWORD
function Read-PrivateValue([string]$Prompt) {
    $secret = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
try {
    if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = Read-PrivateValue 'MySQL password' }
    if ($Demo) {
        $env:MDM_DEMO = 'true'
        if (-not $env:MDM_DEMO_PASSWORD) { $env:MDM_DEMO_PASSWORD = Read-PrivateValue 'Choose a demo login password (at least 12 characters)' }
        if ($env:MDM_DEMO_PASSWORD.Length -lt 12) { throw 'Demo password must contain at least 12 characters.' }
    }
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Application startup failed. Read the Maven error above.' }
} finally {
    $env:DB_PASSWORD = $previousPassword
    $env:MDM_DEMO = $previousDemo
    $env:MDM_DEMO_PASSWORD = $previousDemoPassword
}
