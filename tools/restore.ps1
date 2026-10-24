param([string]$TvAddress = '172.20.10.12:5555', [string]$AdbPath = '.\adb.exe')
$ErrorActionPreference = 'Stop'
$AdbPath = (Resolve-Path -LiteralPath $AdbPath).Path
function Invoke-Adb {
    param([string[]]$Arguments)
    & $AdbPath -s $TvAddress @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB operation failed: $Arguments" }
}
& $AdbPath connect $TvAddress
if ($LASTEXITCODE -ne 0) { throw 'ADB connection failed.' }
# Restore only this session's disabled packages. Preserve pre-existing disabled apps.
Invoke-Adb -Arguments @('shell', 'pm', 'enable', '--user', '0', 'com.tcl.bi')
Invoke-Adb -Arguments @('shell', 'pm', 'enable', '--user', '0', 'com.tcl.logkit')
Invoke-Adb -Arguments @('shell', 'pm', 'enable', '--user', '0', 'com.google.android.apps.tv.launcherx')
Invoke-Adb -Arguments @('shell', 'cmd', 'package', 'set-home-activity', '--user', '0', 'com.google.android.apps.tv.launcherx/.home.HomeActivity')
Invoke-Adb -Arguments @('shell', 'input', 'keyevent', 'HOME')
Invoke-Adb -Arguments @('shell', 'pm', 'list', 'packages', '-d')
Write-Output 'Check the screen: Home should show Google TV. Quiet TV remains installed.'
