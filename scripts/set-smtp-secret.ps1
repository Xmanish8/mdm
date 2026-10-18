param([switch]$Docker)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$folder=Join-Path $projectRoot $(if($Docker){'.secrets'}else{'.smtp-secret'})
New-Item -ItemType Directory -Force $folder | Out-Null
if($IsWindows -or $env:OS -eq 'Windows_NT'){
    $ownerSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls $folder /inheritance:r /grant:r "*${ownerSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Could not restrict SMTP secret folder permissions.'}
}
$secret=Read-Host 'SMTP password / provider app password (hidden)' -AsSecureString
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try{
    $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if([string]::IsNullOrWhiteSpace($plain)){throw 'SMTP password must not be empty.'}
    [IO.File]::WriteAllText((Join-Path $folder 'smtp.password'),$plain,(New-Object Text.UTF8Encoding($false)))
}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$plain=$null}
Write-Host 'SMTP secret saved privately. It was not printed. Do not commit or share the secret folder.'
if($Docker){Write-Host 'On the Linux Docker host, set this file owner to UID 10001 and mode 0400.'}
