# CleanYourTV default theme

This is the upstream CleanYourTV launcher, not Quiet TV. Package: `com.gathin.tvhome`.
Source: https://github.com/Gathin23/cleanyourtv/tree/204143007f0bebfb14d6ef001a32e5836b1c90b5
Upstream launcher version: 2.7 (versionCode 8), minimum Android 8. Built from unchanged source using its pinned Gradle 8.9 and Android Gradle Plugin 8.5.2 with Android SDK 34 and build tools 34.0.0. The release build is optimized and signed using a local debug key as upstream configures. The source can be reproduced by checking out that commit and running `launcher/gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease` from `launcher`. Upstream license is included.

`cleanyourtv-default.json` is an exact copy of upstream `themes/default/config.json`: black background, four columns, no wallpaper, animated focus; Netflix, Prime Video, YouTube, Hotstar in the dock. A missing app is skipped and an available grid app fills the slot. The fresh install has the same built-in default layout. This is a separate application; Quiet TV is retained.

## Windows installation — keep Google TV enabled

Place `cleanyourtv-default.apk` and `cleanyourtv-default.json` in platform-tools and run these separately:

```powershell
.\adb.exe push .\cleanyourtv-default.apk /data/local/tmp/cleanyourtv-default.apk
.\adb.exe shell pm install -r /data/local/tmp/cleanyourtv-default.apk
```

Continue only on `Success`. Open without changing default Home:

```powershell
.\adb.exe shell am start -n com.gathin.tvhome/.MainActivity
```

To explicitly apply the exact default overlay on Android 11, stage the file and use the upstream ThemeProvider (the supported write path avoids Android/data file-ownership problems):

```powershell
.\adb.exe push .\cleanyourtv-default.json /data/local/tmp/cyt-default.json
.\adb.exe shell "content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/cyt-default.json"
.\adb.exe shell am force-stop com.gathin.tvhome
.\adb.exe shell am start -n com.gathin.tvhome/.MainActivity
```

The force-stop above is for a fresh install with no accessibility helpers enabled. If using such helpers later, use upstream tools/theme.ps1 to preserve their bindings. Full upstream theme application and reset are provided by `tools/theme.ps1 -TvHost <TV_IP> themes/default` and `tools/theme.ps1 -TvHost <TV_IP> -Reset` after preparing its documented Python environment. No upstream install script is run here because it can change Home assignment.

Verify the theme with D-pad navigation, Settings/Inputs, real playback and a screenshot:

```powershell
.\adb.exe shell screencap -p /sdcard/cleanyourtv-default.png
.\adb.exe pull /sdcard/cleanyourtv-default.png .
```

These device operations remain pending until the owner runs them on the same Wi-Fi as the TV. This cloud machine cannot directly reach the device.

## This TV's Home limitation

On this TCL C635, assigning Quiet TV as Home reported Success but Home resolved to Google TV. Disabling Google TV then caused a freeze; enabling and explicitly starting Google TV restored normal operation. Keep `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith` enabled. Some saved Wi-Fi networks are owned by setupwraith. Do not rerun that handover or enable accessibility/key helpers as part of a visual-theme change.

To leave CleanYourTV, press Home to return to stock Google TV or explicitly open it:

```powershell
.\adb.exe shell am start -n com.google.android.apps.tv.launcherx/.home.HomeActivity
```

No system package changes, Home assignment changes, permission grants or accessibility changes are part of these instructions.

## Cloud validation

Release build passed; 226 unit tests passed (0 skipped), Android lint completed with no errors, APK signature verification passed, and the APK is 512,257 bytes. Installation and appearance on the real TV remain unverified.
