#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/release-smoke
capture() {
    adb logcat -d -v threadtime > app/build/reports/release-smoke/logcat.txt 2>&1 || true
    adb shell dumpsys activity activities > app/build/reports/release-smoke/activities.txt 2>&1 || true
}
trap capture EXIT
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
# Bound test infrastructure as well as each test. A timeout is a failure and
# blocks publication; logs are uploaded by the workflow's always() step.
timeout --signal=TERM --kill-after=15s 360s ./gradlew connectedReleaseAndroidTest \
    -PtestBuildType=release \
    -Pandroid.testInstrumentationRunnerArguments.timeout_msec=60000 --no-daemon --stacktrace
