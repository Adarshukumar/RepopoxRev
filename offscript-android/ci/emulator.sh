#!/usr/bin/env bash
set +e
set -o pipefail
# The Google image's Pixel Launcher can ANR under headless CI and cover the app.
# Disable only that unused launcher, never suppress target-app ANRs.
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
gradle connectedDebugAndroidTest --console=plain 2>&1 | tee -a build.log
status=${PIPESTATUS[0]}
mkdir -p screenshots
adb pull /sdcard/Download/. screenshots/
adb logcat -d -t 500 '*:E' > screenshots/logcat-errors.txt
exit "$status"
