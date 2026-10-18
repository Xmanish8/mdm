param([string]$Domain,[string]$Operator,[string]$Contact,[string]$AdminEmail)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if(-not $Domain){$Domain=Read-Host 'Production domain (for example workspace.yourcompany.com)'}
if(-not $Operator){$Operator=Read-Host 'Legal operator / organization name'}
if(-not $Contact){$Contact=Read-Host 'Privacy and support contact email'}
if(-not $AdminEmail){$AdminEmail=Read-Host 'Initial administrator email'}
if($Domain -notmatch '^(?=.{1,253}$)[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?\.[a-zA-Z]{2,63}$' -or $Domain.EndsWith('.invalid')){throw 'Enter a real domain without scheme, path or port.'}
if($Operator.Length -lt 2 -or $Operator -match '[\r\n$#"'']'){throw 'Enter a simple operator name without line breaks or environment-file special characters.'}
foreach($email in @($Contact,$AdminEmail)){if($email -notmatch '^[^\s@"''$#]+@[^\s@"''$#]+\.[^\s@"''$#]+$'){throw 'Enter valid email addresses.'}}
$secrets=Join-Path $projectRoot '.secrets'
New-Item -ItemType Directory -Force $secrets | Out-Null
if($IsWindows -or $env:OS -eq 'Windows_NT'){
    $ownerSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls $secrets /inheritance:r /grant:r "*${ownerSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Could not restrict secret directory permissions.'}
}
$utf8=New-Object System.Text.UTF8Encoding($false)
foreach($name in @('db.password','root.password','provision.password','rate.secret')){
    $path=Join-Path $secrets $name
    if(-not(Test-Path -LiteralPath $path)){
        $bytes=New-Object byte[] 32
        $random=[Security.Cryptography.RandomNumberGenerator]::Create()
        try{$random.GetBytes($bytes)}finally{$random.Dispose()}
        $value=([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
        [IO.File]::WriteAllText($path,$value,$utf8)
    }
}
$bootstrap=Join-Path $secrets 'bootstrap.password'
if(-not(Test-Path -LiteralPath $bootstrap)){
    $secret=Read-Host 'Choose initial admin password (12+ characters; max 72 UTF-8 bytes)' -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try{
        $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if($plain.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($plain) -gt 72){throw 'Initial password does not meet length requirements.'}
        [IO.File]::WriteAllText($bootstrap,$plain,$utf8)
    }finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$plain=$null}
}
$envFile=Join-Path $projectRoot '.env'
$values=@{SITE_DOMAIN=$Domain.ToLowerInvariant();SITE_BASE_URL="https://$($Domain.ToLowerInvariant())";LEGAL_OPERATOR=$Operator;LEGAL_CONTACT=$Contact;BOOTSTRAP_ADMIN_EMAIL=$AdminEmail;ANALYTICS_ENABLED='false'}
if(Test-Path -LiteralPath $envFile){
    $old=Get-Content -LiteralPath $envFile -Raw
    if($old -match '(?m)^SITE_DOMAIN=.+$') {throw 'An existing configured .env was preserved. Edit it deliberately instead of overwriting it.'}
}
$lines=$values.GetEnumerator() | Sort-Object Name | ForEach-Object {if($_.Name -eq 'LEGAL_OPERATOR'){ $_.Name+'="'+$_.Value+'"'}else{$_.Name+'='+$_.Value}}
[IO.File]::WriteAllLines($envFile,$lines,$utf8)
Write-Host 'Configuration and secret files created. No passwords were printed.'
Write-Host 'On a Linux deployment host: restrict .secrets to the owner, set secret file owner to UID 10001 and mode 0400 before running Compose.'
