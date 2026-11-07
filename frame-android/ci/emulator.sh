#!/usr/bin/env bash
set +e
set -o pipefail
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher
gradle --stop
free -h
timeout --kill-after=15s 300s gradle --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.frame.app.BrowserTest#realExtensionBlocksAndDashboardWorks --console=plain 2>&1 | tee -a build.log
status=${PIPESTATUS[0]}
mkdir -p screenshots
timeout 20s adb pull /sdcard/Download/. screenshots/
timeout 20s adb logcat -d > screenshots/logcat-full.txt
grep -Ei "FATAL EXCEPTION|WATCHDOG|lmkd|lowmemorykiller|am_crash|System has crashed" screenshots/logcat-full.txt | tail -50 > screenshots/system-errors.txt
grep -Ei 'GeckoConsole|FrameTest|ExtensionError|WebExtension|GeckoSession|GeckoViewContent|GeckoViewNavigation' screenshots/logcat-full.txt | tail -100 > screenshots/extension-log.txt
timeout 20s adb logcat -d -t 2000 '*:E' > screenshots/logcat-errors.txt
exit "$status"
