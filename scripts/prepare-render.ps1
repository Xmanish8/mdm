param([string]$MavenRepository=(Join-Path $env:USERPROFILE '.m2\repository'),[switch]$SelfTest)
$ErrorActionPreference='Stop'
$taskProjectRoot=Split-Path -Parent $PSScriptRoot
$taskCryptoRoot=Join-Path $MavenRepository 'org\springframework\security\spring-security-crypto'
$taskCrypto=Get-ChildItem -LiteralPath $taskCryptoRoot -Filter 'spring-security-crypto-*.jar' -Recurse -ErrorAction SilentlyContinue |
    Where-Object {$_.Name -notmatch '-(sources|javadoc)\.jar$'} | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if(-not $taskCrypto){throw 'Run .\mvnw.cmd package first, or pass -MavenRepository with your Maven cache path.'}
$taskSource=Join-Path $PSScriptRoot 'RenderAdmin.java'
if($SelfTest){
    & java -cp $taskCrypto.FullName $taskSource --self-test
    if($LASTEXITCODE -ne 0){throw 'Administrator helper self-check failed.'}
    return
}
$taskSecrets=Join-Path $taskProjectRoot '.secrets'
$taskAdminFile=Join-Path $taskSecrets 'render-bootstrap.sql'
if(Test-Path -LiteralPath $taskAdminFile){throw 'Existing administrator initialization file was preserved. Use it for this deployment; do not overwrite it to reset an existing database.'}
New-Item -ItemType Directory -Force -Path $taskSecrets | Out-Null
if($IsWindows -or $env:OS -eq 'Windows_NT'){
    $taskOwnerSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    & icacls $taskSecrets /inheritance:r /grant:r "*${taskOwnerSid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Could not protect the secret directory.'}
}
$taskUtf8=New-Object Text.UTF8Encoding($false)
foreach($taskSecretName in @('db.password','root.password','provision.password','rate.secret')){
    $taskSecretPath=Join-Path $taskSecrets $taskSecretName
    if(-not(Test-Path -LiteralPath $taskSecretPath)){
        $taskBytes=New-Object byte[] 32
        $taskRandom=[Security.Cryptography.RandomNumberGenerator]::Create()
        try{$taskRandom.GetBytes($taskBytes)}finally{$taskRandom.Dispose()}
        [IO.File]::WriteAllText($taskSecretPath,([BitConverter]::ToString($taskBytes)).Replace('-','').ToLowerInvariant(),$taskUtf8)
    }
}
& java -cp $taskCrypto.FullName $taskSource $taskAdminFile
if($LASTEXITCODE -ne 0){throw 'Administrator initialization was not completed. Existing secret files were preserved.'}
Write-Host 'Render configuration prepared in .secrets. Follow RENDER_DEPLOYMENT.md; never commit or share this directory.'
