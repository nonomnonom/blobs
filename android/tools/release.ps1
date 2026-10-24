param(
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
        if (-not (Test-Path -LiteralPath $package -PathType Leaf) -or [IO.Path]::GetExtension($package) -cnotin @('.blobs', '.gguf')) {
            throw "Expected a .blobs or .gguf package file: $package"
        }
    }
    if ([IO.Path]::GetExtension($Library) -cne '.blobs') {
        throw "The library must be a .blobs package: $Library"
    }
}
Push-Location (Join-Path $projectRoot '../corpus')
try {
    & uv run --locked python -m unittest discover -s ../android/tools/tests
    if ($LASTEXITCODE -ne 0) { throw 'Release tooling tests failed' }
    & uv run --locked python -m unittest discover -s tests
    if ($LASTEXITCODE -ne 0) { throw 'Corpus preparation tests failed' }
} finally { Pop-Location }
$requiredSigningVariables = @(
    'BLOBS_KEYSTORE',
    'BLOBS_STORE_PASSWORD',
    'BLOBS_KEY_PASSWORD',
    'BLOBS_KEY_ALIAS',
    'BLOBS_CERT_SHA256'
)
foreach ($name in $requiredSigningVariables) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Missing required protected release setting: $name"
    }
}
$keyFile = Get-Item -LiteralPath $env:BLOBS_KEYSTORE -ErrorAction Stop
if ($keyFile.PSIsContainer) { throw 'BLOBS_KEYSTORE must name a keystore file.' }
Push-Location $projectRoot
try {
    & .\gradlew.bat verify --offline --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release verification failed' }
    & uv run --locked --project ../corpus python tools/verify_apk.py app/build/outputs/apk/release/app-release.apk --certificate-sha256 $env:BLOBS_CERT_SHA256
    if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
    if ($Model) {
        & uv run --locked --project ../corpus python tools/package_release.py --model $Model --library $Library --certificate-sha256 $env:BLOBS_CERT_SHA256
        if ($LASTEXITCODE -ne 0) { throw 'Release kit assembly failed' }
    }
} finally {
    Pop-Location
}
