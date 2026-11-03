#!/usr/bin/env bash
# Exercise the exact APKs produced by the verified build, without rebuilding them.
set -euo pipefail
mkdir -p device-results
app=$(find verified -path '*/apk/debug/app-debug.apk' -print -quit)
tests=$(find verified -name 'app-debug-androidTest.apk' -print -quit)
test -n "$app" && test -n "$tests"
adb install -r "$app"
adb install -r "$tests"
set +e
adb shell am instrument -w -r com.az.animewallpaper.dev.test/androidx.test.runner.AndroidJUnitRunner | tee device-results/instrumentation.txt
status=${PIPESTATUS[0]}
adb pull /sdcard/Android/data/com.az.animewallpaper.dev/files/ device-results/screenshots
adb logcat -d -s AndroidRuntime:E AZWallpaper:E > device-results/errors.txt
set -e
if [[ "$status" != 0 ]]; then exit "$status"; fi
grep -Eq '^OK \([0-9]+ tests?\)' device-results/instrumentation.txt
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' device-results/instrumentation.txt
