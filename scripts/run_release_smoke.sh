#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/release-smoke
capture() {
    adb logcat -d -v threadtime > app/build/reports/release-smoke/logcat.txt 2>&1 || true
    adb shell dumpsys activity activities > app/build/reports/release-smoke/activities.txt 2>&1 || true
}
trap capture EXIT
# Standalone test dependencies are packaged in this CI-only APK, not removed
# as duplicates of classes that R8 optimized away from the target application.
timeout --signal=TERM --kill-after=15s 180s ./gradlew :release-smoke:assembleRelease --no-daemon --stacktrace
adb install -r app/build/outputs/apk/release/app-x86_64-release.apk
adb install -r release-smoke/build/outputs/apk/release/release-smoke-release.apk
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
timeout --signal=TERM --kill-after=15s 180s adb shell am instrument -w \
    -e class org.proxydroid.release.MinifiedReleaseSmokeTest \
    -e timeout_msec 60000 \
    org.proxydroid.release.smoke/androidx.test.runner.AndroidJUnitRunner \
    | tee app/build/reports/release-smoke/instrumentation.txt
# am instrument can exit 0 after a runner crash. Require all three tests.
grep -Eq '^OK \(3 tests\)' app/build/reports/release-smoke/instrumentation.txt
! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|Process crashed' app/build/reports/release-smoke/instrumentation.txt
