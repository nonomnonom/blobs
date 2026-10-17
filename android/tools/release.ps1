param(
    [switch]$CreateKey,
    [string]$Model,
    [string]$Library
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([bool]$Model -xor [bool]$Library) {
    throw 'Provide both -Model and -Library to assemble a release kit, or omit both to build the signed APK.'
}
if ($Model) {
    $Model = (Get-Item -LiteralPath $Model -ErrorAction Stop).FullName
    $Library = (Get-Item -LiteralPath $Library -ErrorAction Stop).FullName
    foreach ($package in @($Model, $Library)) {
        if (-not (Test-Path -LiteralPath $package -PathType Leaf) -or [IO.Path]::GetExtension($package) -cne '.blobs') {
            throw "Expected a .blobs package file: $package"
        }
    }
}
Push-Location (Join-Path $projectRoot '../corpus')
try {
    & uv run --locked python -m unittest discover -s ../android/tools/tests
    if ($LASTEXITCODE -ne 0) { throw 'Release tooling tests failed' }
    & uv run --locked python -m unittest discover -s tests
    if ($LASTEXITCODE -ne 0) { throw 'Corpus preparation tests failed' }
} finally { Pop-Location }
$signingRoot = Join-Path $projectRoot '.local/signing'
$keyFile = Join-Path $signingRoot 'blobs-release.jks'
$passwordFile = Join-Path $signingRoot 'password.dpapi'
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keyFile)) {
    if (-not $CreateKey) { throw 'No release key. Use -CreateKey once in your protected Windows account.' }
    New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
    $randomBytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $releasePassword = [Convert]::ToBase64String($randomBytes)
    $securePassword = ConvertTo-SecureString $releasePassword -AsPlainText -Force
    ConvertFrom-SecureString $securePassword | Set-Content -LiteralPath $passwordFile
    $env:BLOBS_STORE_PASSWORD = $releasePassword
    try {
        & $keytool -genkeypair -keystore $keyFile -storetype JKS -alias blobs -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Blobs, O=Blobs' -storepass:env BLOBS_STORE_PASSWORD -keypass:env BLOBS_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed' }
    } finally { Remove-Item Env:BLOBS_STORE_PASSWORD -ErrorAction SilentlyContinue }
}
if (-not (Test-Path -LiteralPath $passwordFile)) { throw 'The release password file is missing.' }
$securePassword = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$credential = New-Object System.Management.Automation.PSCredential('blobs', $securePassword)
$env:BLOBS_KEYSTORE = $keyFile
$env:BLOBS_STORE_PASSWORD = $credential.GetNetworkCredential().Password
$env:BLOBS_KEY_PASSWORD = $env:BLOBS_STORE_PASSWORD
$env:BLOBS_KEY_ALIAS = 'blobs'
Push-Location $projectRoot
try {
    & .\gradlew.bat verify --offline --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release verification failed' }
    & uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/release/app-release.apk
    if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
    if ($Model) {
        & uv run --locked --project ../corpus python tools/package_release.py --model $Model --library $Library
        if ($LASTEXITCODE -ne 0) { throw 'Release kit assembly failed' }
    }
} finally {
    Pop-Location
    'BLOBS_KEYSTORE','BLOBS_STORE_PASSWORD','BLOBS_KEY_PASSWORD','BLOBS_KEY_ALIAS' | ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
}
