param([string]$MavenRepository=(Join-Path $env:USERPROFILE '.m2\repository'),[switch]$SelfTest)
$ErrorActionPreference='Stop'
$reviewProject=Split-Path -Parent $PSScriptRoot
$reviewCryptoRoot=Join-Path $MavenRepository 'org\springframework\security\spring-security-crypto'
$reviewCrypto=Get-ChildItem -LiteralPath $reviewCryptoRoot -Filter 'spring-security-crypto-*.jar' -Recurse -ErrorAction SilentlyContinue |
    Where-Object {$_.Name -notmatch '-(sources|javadoc)\.jar$'} | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if(-not $reviewCrypto){throw 'Run .\mvnw.cmd package first, or pass your Maven cache using -MavenRepository.'}
$reviewSource=Join-Path $PSScriptRoot 'ReviewerSetup.java'
if($SelfTest){
    & java -cp $reviewCrypto.FullName $reviewSource --self-test $reviewProject
    if($LASTEXITCODE -ne 0){throw 'Reviewer setup self-check failed.'}
    return
}
$reviewSecrets=Join-Path $reviewProject '.secrets'
foreach($reviewName in @('reviewer.password','reviewer-db.password','reviewer-bootstrap.sql')){
    if(Test-Path -LiteralPath (Join-Path $reviewSecrets $reviewName)){throw 'Existing reviewer files preserved. Continue using them; this script does not reset credentials.'}
}
$reviewNewDirectory=-not(Test-Path -LiteralPath $reviewSecrets)
if($reviewNewDirectory){New-Item -ItemType Directory -Path $reviewSecrets | Out-Null}
if($reviewNewDirectory -and ($IsWindows -or $env:OS -eq 'Windows_NT')){
    $reviewOwnerSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls $reviewSecrets /inheritance:r /grant:r "*${reviewOwnerSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Could not protect the reviewer setup directory.'}
}
& java -cp $reviewCrypto.FullName $reviewSource $reviewProject $reviewSecrets
if($LASTEXITCODE -ne 0){throw 'Reviewer setup was not completed. Preserve existing files and check the error.'}
Write-Host 'Follow REVIEWER_SETUP.md. Only reviewer.password is intentionally displayed on the enabled review login page.'
