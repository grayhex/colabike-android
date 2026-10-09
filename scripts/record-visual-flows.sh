#!/usr/bin/env bash
# A debug emulator demonstration with fake repositories. Never records LiveSmokeTest or real login.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ANDROID_HOME:?ANDROID_HOME is required}/platform-tools/adb"
output="${1:-build/visual-evidence}"
mkdir -p "$output"
git rev-parse HEAD > "$output/source-commit.txt"
printf '%s/%s/actions/runs/%s\n' "${GITHUB_SERVER_URL:-https://github.com}" "${GITHUB_REPOSITORY:-grayhex/colabike-android}" "${GITHUB_RUN_ID:-local}" > "$output/ci-run.txt"
cp app/src/test/resources/visual/photos/sources.json "$output/photo-credits.json"
cp app/src/test/resources/visual/README.md "$output/fixture-licenses.md"
old_window="$($ADB shell settings get global window_animation_scale | tr -d '\r')"
old_transition="$($ADB shell settings get global transition_animation_scale | tr -d '\r')"
old_animator="$($ADB shell settings get global animator_duration_scale | tr -d '\r')"
record_pid=""
stop_recording() {
  if [ -n "$record_pid" ]; then
    local remote_pid
    remote_pid="$($ADB shell cat /sdcard/colabike-visual.pid 2>/dev/null | tr -d '\r')"
    if [[ "$remote_pid" =~ ^[0-9]+$ ]]; then "$ADB" shell kill -2 "$remote_pid" || true; fi
    wait "$record_pid" || true
    record_pid=""
  fi
}
cleanup() {
  stop_recording
  "$ADB" shell settings put global window_animation_scale "$old_window"
  "$ADB" shell settings put global transition_animation_scale "$old_transition"
  "$ADB" shell settings put global animator_duration_scale "$old_animator"
}
trap cleanup EXIT
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for motion in normal reduced; do
  scale=1
  theme=light
  if [ "$motion" = reduced ]; then scale=0; fi
  for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" shell settings put global "$setting" "$scale"
  done
  for method in rideMapAndFindCompany bicyclePhotosAndProfile addBicycleFromBuild; do
    name="$method-$motion-$theme"
    "$ADB" shell "echo \$\$ > /sdcard/colabike-visual.pid; exec screenrecord --bit-rate 6000000 --time-limit 180 /sdcard/$name.mp4" > "$output/$name-recorder.log" 2>&1 &
    record_pid=$!
    "$ADB" shell am instrument -w -r \
      -e class "ru.colabike.app.VisualAcceptanceTest#$method" \
      -e recordVisual true -e visualTheme "$theme" \
      ru.colabike.app.test/androidx.test.runner.AndroidJUnitRunner > "$output/$name-test.log" 2>&1
    stop_recording
    "$ADB" pull "/sdcard/$name.mp4" "$output/$name.mp4"
    "$ADB" shell rm -f "/sdcard/$name.mp4"
    if ! grep -Eq 'OK \(1 test\)' "$output/$name-test.log"; then
      cat "$output/$name-test.log"
      echo "::error::visual flow $name did not pass exactly one instrumented test"
      exit 1
    fi
  done
done
"$ADB" shell getprop ro.build.fingerprint > "$output/device.txt"
"$ADB" shell wm size >> "$output/device.txt"
"$ADB" shell wm density >> "$output/device.txt"
printf '%s\n' 'Debug emulator demonstration; not a physical-device frame timing measurement.' > "$output/README.txt"
