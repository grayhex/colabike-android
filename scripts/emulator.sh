#!/usr/bin/env bash
# Starts or stops a headless Android 17 (API 37) emulator for instrumented tests in CI.
#   scripts/emulator.sh start | stop
set -euo pipefail

IMAGE="system-images;android-37.0;google_apis;x86_64"
AVD="colabike-api37"
SDK="${ANDROID_HOME:?ANDROID_HOME is not set}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

case "${1:-}" in
start)
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" --install "$IMAGE" "emulator" "platform-tools" >/dev/null
  echo no | "$AVDMANAGER" create avd --force --name "$AVD" --package "$IMAGE" --device "pixel_7"
  nohup "$SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -camera-back none -camera-front none >/tmp/emulator.log 2>&1 &
  "$SDK/platform-tools/adb" wait-for-device
  for _ in $(seq 1 120); do
    if [ "$("$SDK/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      break
    fi
    sleep 5
  done
  "$SDK/platform-tools/adb" shell getprop ro.build.version.sdk
  # Animations off: the tests wait for state, not for transitions.
  for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$SDK/platform-tools/adb" shell settings put global "$setting" 0
  done
  "$SDK/platform-tools/adb" shell input keyevent 82
  ;;
stop)
  "$SDK/platform-tools/adb" emu kill || true
  ;;
*)
  echo "usage: $0 start|stop" >&2
  exit 2
  ;;
esac
