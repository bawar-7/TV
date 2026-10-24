param([string]$TvAddress = '172.20.10.12:5555', [string]$AdbPath = '.\adb.exe', [string]$ApkPath = (Join-Path $PSScriptRoot '..\quiet-tv.apk'))
$ErrorActionPreference = 'Stop'
$AdbPath = (Resolve-Path -LiteralPath $AdbPath).Path
if (!(Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }
function Invoke-Adb {
    param([string[]]$Arguments)
    & $AdbPath -s $TvAddress @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB operation failed: $Arguments" }
}
& $AdbPath connect $TvAddress
if ($LASTEXITCODE -ne 0) { throw 'Connection failed. Accept the debugging prompt on the TV.' }
Invoke-Adb -Arguments @('get-state')
$sdk = Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.sdk')
if ([int]($sdk | Select-Object -Last 1) -lt 26) { throw 'This launcher requires Android 8 or newer.' }
Invoke-Adb -Arguments @('push', $ApkPath, '/data/local/tmp/quiet-tv.apk')
$install = Invoke-Adb -Arguments @('shell', 'pm', 'install', '-r', '/data/local/tmp/quiet-tv.apk')
$install | Write-Output
if (($install -join "`n") -notmatch '(?m)^Success\s*$') { throw 'Installation was not successful. Keep Google TV enabled and share the error.' }
Invoke-Adb -Arguments @('shell', 'am', 'start', '-n', 'dev.bawar.tv.launcher/.HomeActivity')
Write-Output 'Quiet TV opened. Google TV remains enabled; the default Home has not been changed.'
