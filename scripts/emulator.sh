#!/usr/bin/env bash
# Starts or stops a headless Android 17 (API 37) emulator for instrumented tests in CI.
#   scripts/emulator.sh start | stop
set -euo pipefail

IMAGE="system-images;android-37.0;google_apis;x86_64"
AVD="colabike-api37"
SDK="${ANDROID_HOME:?ANDROID_HOME is not set}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
ADB="$SDK/platform-tools/adb"
LOG="${RUNNER_TEMP:-/tmp}/emulator.log"
PID="${RUNNER_TEMP:-/tmp}/emulator.pid"

fail() {
  echo "::error::$1"
  tail -n 200 "$LOG" || true
  exit 1
}

case "${1:-}" in
start)
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" --install "$IMAGE" "emulator" "platform-tools" >/dev/null
  echo no | "$AVDMANAGER" create avd --force --name "$AVD" --package "$IMAGE" --device "pixel_7"
  "$SDK/emulator/emulator" -accel-check || true
  nohup "$SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -camera-back none -camera-front none >"$LOG" 2>&1 &
  echo $! >"$PID"
  # Never wait forever: a dead emulator would leave adb waiting until the job times out.
  if ! timeout 300 "$ADB" wait-for-device; then
    fail "the emulator did not show up in adb within 5 minutes"
  fi
  booted=0
  for _ in $(seq 1 120); do
    if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      booted=1
      break
    fi
    kill -0 "$(cat "$PID")" 2>/dev/null || fail "the emulator process exited while booting"
    sleep 5
  done
  [ "$booted" = 1 ] || fail "the emulator did not finish booting within 10 minutes"
  "$ADB" shell getprop ro.build.version.sdk
  # Animations off: the tests wait for state, not for transitions.
  for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" shell settings put global "$setting" 0
  done
  "$ADB" shell input keyevent 82
  ;;
stop)
  "$ADB" emu kill || true
  ;;
*)
  echo "usage: $0 start|stop" >&2
  exit 2
  ;;
esac
