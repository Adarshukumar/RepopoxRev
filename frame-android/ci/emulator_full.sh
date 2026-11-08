#!/usr/bin/env bash
set -uo pipefail
bash ci/emulator.sh
core=$?
[ "$core" -eq 0 ] || exit "$core"
# Preserve the deterministic results before the separate external-network test.
cp -r app/build/outputs/androidTest-results screenshots/core-test-results
# Core succeeded. Attempt YouTube separately with a hard deadline; report its
# actual outcome, never treat a timeout as a successful playback/ad test.
timeout --kill-after=15s 300s gradle --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.frame.app.YouTubePlaybackTest#realVideoAdvancesWithFullUbo --console=plain > youtube.log 2>&1
smoke=$?
echo "YouTube playback observation exit status: $smoke (0=passed; 124/137=timeout). Playback requires actual decoded frames and media time advance; ad removal is NOT established by an absence of ads." | tee screenshots/youtube-result.txt
timeout 15s adb pull /sdcard/Download/. screenshots/ || true
timeout 15s adb logcat -d > screenshots/youtube-logcat.txt || true
grep -E "FrameTest|FrameWarmup|FrameNetwork" screenshots/youtube-logcat.txt | tail -20 >> screenshots/youtube-result.txt
tail -60 youtube.log >> screenshots/youtube-result.txt
grep -Ei "GeckoConsole|FrameNetwork|FATAL EXCEPTION|am_crash|lowmemorykiller|lmkd|ANR in|FrameEvidence|NativeMessaging" screenshots/youtube-logcat.txt | tail -70 > screenshots/youtube-diagnostics.txt
exit "$smoke"
