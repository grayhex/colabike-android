#!/usr/bin/env bash
# Fails unless every module with instrumented tests really ran them on the device.
#   scripts/check-connected-tests.sh [gradle-output.log]
#
# A green connectedDebugAndroidTest proves nothing by itself. When an APK does not install, AGP's
# test engine logs "AndroidTestRunner failed on <device>" and finishes the task successfully with
# 0 tests; it also stayed green with a failed test in its report. So each module with
# src/androidTest must report at least one test that ran (not skipped) and no failures.
set -euo pipefail
cd "$(dirname "$0")/.."

status=0
error() {
  echo "::error::$1"
  status=1
}

# The number in Gradle's HTML report box <div class="infoBox" id="NAME"><div class="counter">N</div>.
counter() {
  awk -v id="id=\"$2\"" 'index($0, id) { getline; gsub(/[^0-9]/, ""); print; exit }' "$1"
}

if [ -n "${1:-}" ] && grep 'AndroidTestRunner failed' "$1" >/dev/null; then
  error "the instrumented test runner failed on the device: see 'AndroidTestRunner failed' in the log"
fi

modules=0
while IFS= read -r source; do
  module="${source%/src/androidTest}"
  module="${module#./}"
  modules=$((modules + 1))
  report="$module/build/reports/androidTests/connected/debug/index.html"
  if [ ! -f "$report" ]; then
    error "$module: no connected test report at $report"
    continue
  fi
  tests="$(counter "$report" tests)"
  failures="$(counter "$report" failures)"
  skipped="$(counter "$report" skipped)"
  echo "$module: ${tests:-?} tests, ${failures:-?} failures, ${skipped:-?} skipped"
  if [ -z "$tests" ] || [ -z "$failures" ] || [ -z "$skipped" ]; then
    error "$module: the report has no test counters"
  elif [ "$((tests - skipped))" -lt 1 ]; then
    error "$module: no instrumented test ran on the device"
  elif [ "$failures" -ne 0 ]; then
    error "$module: $failures instrumented tests failed"
  fi
done < <(find . -type d \( -name build -o -name .gradle -o -name .git \) -prune -o \
  -type d -path '*/src/androidTest' -print | sort)

[ "$modules" -gt 0 ] || error "no module has instrumented tests"
exit "$status"
