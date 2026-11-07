#!/usr/bin/env bash
set +e
set -o pipefail
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
gradle connectedDebugAndroidTest --console=plain 2>&1 | tee -a build.log
status=${PIPESTATUS[0]}
mkdir -p screenshots
adb pull /sdcard/Download/. screenshots/
adb logcat -d -t 2000 '*:E' > screenshots/logcat-errors.txt
exit "$status"
