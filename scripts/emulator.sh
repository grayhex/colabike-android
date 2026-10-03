#!/usr/bin/env bash
# Starts or stops a headless Android 17 (API 37) emulator for instrumented tests in CI.
#   scripts/emulator.sh start | stop
set -euo pipefail

IMAGE="system-images;android-37.0;google_apis;x86_64"
AVD="colabike-api37"
SDK="${ANDROID_HOME:?ANDROID_HOME is not set}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
LOG="${RUNNER_TEMP:-/tmp}/emulator.log"
PID="${RUNNER_TEMP:-/tmp}/emulator.pid"

# avdmanager and the emulator resolve the AVD directory differently on CI runners: avdmanager
# wrote the AVD where the emulator never looked ("Unknown AVD name"). Both read ANDROID_AVD_HOME
# first, so one explicit directory serves both.
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

fail() {
  echo "::error::$1"
  tail -n 200 "$LOG" || true
  exit 1
}

case "${1:-}" in
start)
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" --install "$IMAGE" "emulator" "platform-tools" >/dev/null
  mkdir -p "$ANDROID_AVD_HOME"
  echo no | "$AVDMANAGER" create avd --force --name "$AVD" --package "$IMAGE" --device "pixel_7"
  [ -f "$ANDROID_AVD_HOME/$AVD.ini" ] || fail "avdmanager did not create $AVD in $ANDROID_AVD_HOME"
  "$EMULATOR" -accel-check || true
  nohup "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -camera-back none -camera-front none >"$LOG" 2>&1 &
  echo $! >"$PID"
  # Poll instead of `adb wait-for-device`: a dead emulator fails the step at once
  # instead of leaving adb waiting until the timeout.
  booted=0
  for _ in $(seq 1 120); do
    kill -0 "$(cat "$PID")" 2>/dev/null || fail "the emulator process exited while booting"
    if [ "$(timeout 10 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      booted=1
      break
    fi
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
