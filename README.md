# Quiet TV — TCL C635

A Java/AndroidX RecyclerView home screen with a black background, a dock, scrolling app grid, clock, moving glass focus, and Inputs, network and Settings shortcuts. No Internet permission, ads, video, blur, accessibility service or network polling. Requires Android 8+; this TV reported SDK 30 (Android 11).

## Changes made on the TV

Only `com.tcl.bi` (likely analytics) and `com.tcl.logkit` (diagnostics/remote support) were disabled during this session. Disabled-package output confirmed both. The owner reported working Home, Settings, volume and playback; screenshots showed Home rendering normally. Reboot persistence has not been checked yet. The owner declined a full backup.

Stan (`au.com.stan.and`), Google TV Movies (`com.google.android.videos`) and Rakuten (`tv.wuaki.apptv`) were already disabled. Restore instructions leave those untouched. Casting, assistants, updates, input and pairing services remain unchanged.

Google TV Home is `com.google.android.apps.tv.launcherx/.home.HomeActivity`. The setup recovery activity `com.google.android.tungsten.setupwraith/.RecoveryActivity` also claims HOME. Keep both enabled during launcher installation and testing. Disabling setupwraith can delete a Wi-Fi network it owns and terminate ADB access.

## Install from Windows

The computer and TV must be on the same Wi-Fi. The cloud machine cannot directly access the TV. Put the supplied `quiet-tv.apk` in the Google platform-tools folder and open PowerShell there. Run separately:

```powershell
.\adb.exe connect 172.20.10.12:5555
.\adb.exe push .\quiet-tv.apk /data/local/tmp/quiet-tv.apk
.\adb.exe shell pm install -r /data/local/tmp/quiet-tv.apk
```

Continue only if installation reports `Success`. A verification or manufacturer HOME-signature refusal is a device limitation; share the exact error and keep Google TV enabled. Then open the app explicitly:

```powershell
.\adb.exe shell am start -n dev.bawar.tv.launcher/.HomeActivity
```

This does not change default Home. Check D-pad movement, app launching, actual video playback, volume including holding volume up, Inputs/HDMI switching, network settings and Settings. Check real remote shortcuts too. Inputs uses the Android TV input framework; vendor-specific switching must be tested on the device.

After moving focus, capture and attach a screenshot:

```powershell
.\adb.exe shell screencap -p /sdcard/quiet-tv.png
.\adb.exe pull /sdcard/quiet-tv.png .
```

Inspect a screenshot after each meaningful change. Protected playback and HDMI may not capture; check directly and inspect `dumpsys activity activities`. Never open a factory-menu package to identify it.

`tools/install-launcher.ps1` automates installing and opening without changing Home. Pass `-AdbPath` and `-ApkPath` when needed. These PowerShell scripts have not been executed in the Linux cloud environment; use the manual path above initially.

## Home handover — only after functional checks pass

```powershell
.\adb.exe shell cmd package set-home-activity --user 0 dev.bawar.tv.launcher/.HomeActivity
.\adb.exe shell input keyevent HOME
.\adb.exe shell dumpsys activity activities | Select-String 'mFocusedApp|topResumedActivity'
```

Confirm both the focused activity and visible screen are Quiet TV, then test the real Home button. `Success` alone does not prove takeover. Keep Google TV enabled, reboot once, reconnect and repeat the checks. If Google TV or recovery still takes Home, stop and share the result rather than disabling either package. No remote-key helper is installed.

## Customize and navigate

Left/Right stays within a row. Up/Down moves one row; a short final row clamps to its last app. Up from the dock enters the top bar, whose ends never drop into the grid. Back returns to the dock. The last selected tile is retained when leaving. Press Menu for wallpaper, selected-app settings, or an explicit Google TV shortcut. Choosing wallpaper uses the system file picker; some firmware may not provide one.

Edit `launcher/app/src/main/res/values/config.xml` and rebuild to change `columns` (4–7), `dock_apps` order, greeting or `wallpaper_color`. Dock entries are package names; `@settings` is built in. Defaults are Netflix, YouTube, Prime Video and Settings. Missing dock apps are filled from installed TV apps; others appear below. Apps must expose an Android TV launcher activity. The grid refreshes on return Home.

The network pill shows Wi-Fi/Network/Offline. Open it to see the SSID in TV settings; the launcher does not request location access solely to read a network name. Outfit is bundled with its SIL Open Font License.

## Restore this session's changes

Run separately:

```powershell
.\adb.exe shell pm enable --user 0 com.tcl.bi
.\adb.exe shell pm enable --user 0 com.tcl.logkit
.\adb.exe shell pm enable --user 0 com.google.android.apps.tv.launcherx
.\adb.exe shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
.\adb.exe shell input keyevent HOME
```

Check that Google TV is visible. `tools/restore.ps1` performs these same steps. Quiet TV stays installed and may be removed from Settings after stock Home is restored. Do not uninstall system packages.

## Build

Use the existing checkout; cloud tasks already have isolated workspaces and need no Git worktree. Install JDK 21, Android SDK platform 35 and build tools 35.0.0. Set `ANDROID_HOME` or an untracked `launcher/local.properties` containing `sdk.dir=...`. From `launcher`, run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

The Gradle wrapper pins 8.10.2 with a distribution checksum. Android Gradle Plugin is 8.7.3; RecyclerView is 1.3.2. Output: `app/build/outputs/apk/debug/app-debug.apk`. `tools/build-and-install.ps1` builds and opens it. Reuse the debug signing key for updates. A different key gives `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; restore stock Home before considering removal of an older launcher. No release signing credentials are included.

## Build validation and remaining checks

The debug APK compiled successfully and its APK signature was verified. All six grid-navigation unit tests passed with no skips; they cover row boundaries, vertical movement, the shortcut-bar transition and incomplete final rows across 4–7 columns. Android lint completed with zero errors and six warnings (fixed TV orientation, backup metadata, full adapter refresh and intentional physical alignment).

No emulator or TV execution has been performed for this launcher yet. Installation, rendering, actual D-pad behavior, HDMI switching, real remote keys, Home assignment, reboot behavior and PowerShell script execution remain unverified. The network shortcut shows connection type rather than SSID, as described above. The initial download is a debug build.

## Finish the device session

After reboot, compare the disabled-package list with the two packages above; firmware may re-enable them. Once device checks and any restore are complete, switch USB debugging and Network/ADB debugging off, revoke debugging authorizations, and reboot. Verify ADB reconnect is refused from Windows; a timeout alone does not prove the port is closed. Never forward port 5555 through a router. This TV uses Wi-Fi with no USB accessories.

Optional ad-blocking DNS is not configured. If requested, Private DNS can use `dns.adguard-dns.com`, followed by a playback check. It does not block YouTube ads. Undo hostname mode with `settings put global private_dns_mode opportunistic`.
