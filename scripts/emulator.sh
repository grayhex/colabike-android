#!/usr/bin/env bash
# Starts, inspects or stops a headless Android 17 (API 37) emulator for instrumented tests in CI.
#   scripts/emulator.sh start | diagnose | stop
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

alive() {
  kill -0 "$(cat "$PID")" 2>/dev/null || fail "the emulator process exited while booting"
}

device() {
  timeout 20 "$ADB" shell "$@" 2>/dev/null | tr -d '\r'
}

# sys.boot_completed is not enough: right after it the package manager may be missing or the
# private volume unmounted ("Requested internal only, but not enough space", "Can't find service:
# package"), and system_server can still restart. Ready means both answer and system_server keeps
# the same start count for a minute, longer than one crash loop of SurfaceFlinger (about 30 s).
wait_until_ready() {
  local stable=0 count="" current packages volumes
  for _ in $(seq 1 60); do
    alive
    packages="$(device pm path android || true)"
    volumes="$(device sm list-volumes private || true)"
    if [[ "$packages" == package:* ]] && [[ "$volumes" == "private mounted"* ]]; then
      current="$(device getprop sys.system_server.start_count || true)"
      if [ "$current" = "$count" ]; then
        stable=$((stable + 1))
      else
        count="$current"
        stable=0
      fi
      if [ "$stable" -ge 12 ]; then
        echo "Device ready: system_server start count ${count:-unknown}"
        return 0
      fi
    else
      stable=0
    fi
    sleep 5
  done
  fail "system_server did not stay up for a minute within 5 minutes after boot (start count ${count:-unknown}); see the crash buffer in the diagnostics"
}

case "${1:-}" in
start)
  yes | "$SDKMANAGER" --licenses >/dev/null || true
  "$SDKMANAGER" --install "$IMAGE" "emulator" "platform-tools" >/dev/null
  mkdir -p "$ANDROID_AVD_HOME"
  echo no | "$AVDMANAGER" create avd --force --name "$AVD" --package "$IMAGE" --device "pixel_7"
  [ -f "$ANDROID_AVD_HOME/$AVD.ini" ] || fail "avdmanager did not create $AVD in $ANDROID_AVD_HOME"
  # On the runner the AVD got an 800 MB /data that the first boot of API 37 fills up: system_server
  # restarts and no APK installs. Size /data and RAM (2 GB in the pixel_7 profile) explicitly.
  config="$(sed -n 's/^path=//p' "$ANDROID_AVD_HOME/$AVD.ini")/config.ini"
  [ -f "$config" ] || fail "no AVD config at $config"
  sed -i '/^disk\.dataPartition\.size=/d; /^hw\.ramSize=/d' "$config"
  printf '%s\n' "disk.dataPartition.size=6G" "hw.ramSize=4096" >>"$config"
  "$EMULATOR" -accel-check || true
  # The gralloc mapper of the API 37 image reads buffers back only through the renderer's
  # ANDROID_EMU_read_color_buffer_dma, which the emulator offers only with both GLDirectMem and
  # HasSharedSlotsHostMemoryAllocator. The image does not declare the latter, so it stays off and
  # SurfaceFlinger aborts every ~20-30 s ("Assertion failed: !rcEnc->featureInfo()->
  # hasReadColorBufferDma"), taking system_server along. Force both on.
  nohup "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data \
    -gpu swiftshader -feature GLDirectMem,HasSharedSlotsHostMemoryAllocator \
    -camera-back none -camera-front none >"$LOG" 2>&1 &
  echo $! >"$PID"
  # Poll instead of `adb wait-for-device`: a dead emulator fails the step at once
  # instead of leaving adb waiting until the timeout.
  booted=0
  for _ in $(seq 1 120); do
    alive
    if [ "$(device getprop sys.boot_completed || true)" = "1" ]; then
      booted=1
      break
    fi
    sleep 5
  done
  [ "$booted" = 1 ] || fail "the emulator did not finish booting within 10 minutes"
  wait_until_ready
  echo "API level $(device getprop ro.build.version.sdk || true)"
  device df -h /data || true
  free_kb="$(device df -k /data | awk 'NR == 2 { print $4 }' || true)"
  case "$free_kb" in
  '' | *[!0-9]*) fail "could not read the free space on /data" ;;
  esac
  [ "$free_kb" -ge 1048576 ] || fail "/data has $((free_kb / 1024)) MB free; the tests need at least 1 GB"
  # Animations off: the tests wait for state, not for transitions.
  for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" shell settings put global "$setting" 0
  done
  "$ADB" shell input keyevent 82
  ;;
diagnose)
  # Printed into the job log rather than uploaded: GitHub masks secrets only in logs.
  "$ADB" devices || true
  for prop in sys.boot_completed sys.system_server.start_count ro.build.version.sdk; do
    echo "$prop=$(device getprop "$prop" || true)"
  done
  device df -h /data || true
  device sm list-volumes all || true
  echo "--- crash buffer"
  timeout 30 "$ADB" logcat -d -b crash 2>/dev/null |
    grep -E 'Fatal signal|Abort message|FATAL EXCEPTION|Process: |Cmdline: |#0[0-4] pc' |
    tail -n 120 || true
  echo "--- system events"
  timeout 30 "$ADB" logcat -d -b main,system 2>/dev/null |
    grep -E -i 'watchdog|fatal|died|lowmemorykiller|lmkd|no space left|not enough space|low on storage|devicestoragemonitor' |
    tail -n 200 || true
  ;;
stop)
  "$ADB" emu kill || true
  ;;
*)
  echo "usage: $0 start|diagnose|stop" >&2
  exit 2
  ;;
esac
