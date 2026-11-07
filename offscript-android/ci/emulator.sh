#!/usr/bin/env bash
set +e
set -o pipefail
gradle connectedDebugAndroidTest --console=plain 2>&1 | tee -a build.log
status=${PIPESTATUS[0]}
mkdir -p screenshots
adb pull /sdcard/Download/. screenshots/
adb logcat -d -t 500 '*:E' > screenshots/logcat-errors.txt
exit "$status"
