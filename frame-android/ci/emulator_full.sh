#!/usr/bin/env bash
set -uo pipefail
bash ci/emulator.sh
core=$?
[ "$core" -eq 0 ] || exit "$core"
# Preserve the deterministic results before the separate external-network test.
cp -r app/build/outputs/androidTest-results screenshots/core-test-results
# Core succeeded. Attempt YouTube separately with a hard deadline; report its
# actual outcome, never treat a timeout as a successful playback/ad test.
timeout --kill-after=15s 240s gradle --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.frame.app.BrowserTest#publicYouTubePageSmoke --console=plain > youtube.log 2>&1
smoke=$?
echo "YouTube navigation-only smoke exit status: $smoke (0=passed; 124/137=timeout). Playback and ad absence NOT tested." | tee screenshots/youtube-result.txt
timeout 15s adb pull /sdcard/Download/. screenshots/ || true
timeout 15s adb logcat -d > screenshots/youtube-logcat.txt || true
exit 0
