param([string]$TvAddress = '172.20.10.12:5555', [string]$AdbPath = '.\adb.exe')
$ErrorActionPreference = 'Stop'
$AdbPath = (Resolve-Path -LiteralPath $AdbPath).Path
$source = Join-Path $PSScriptRoot '..\launcher'
Push-Location $source
try {
    & .\gradlew.bat --no-daemon :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. Use JDK 21, SDK platform 35 and build tools 35.0.0.' }
} finally { Pop-Location }
& (Join-Path $PSScriptRoot 'install-launcher.ps1') -TvAddress $TvAddress -AdbPath $AdbPath -ApkPath (Join-Path $source 'app\build\outputs\apk\debug\app-debug.apk')
