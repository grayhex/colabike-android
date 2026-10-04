#!/usr/bin/env bash
# Starts the R8 release build on the running emulator and checks what a minified build can break
# without showing it in the debug tests: it launches, the start screen is drawn, the shell with its
# sections opens, and after the system kills the process in the background the person comes back to
# the section they were in (the saved back stack is read through the serializers R8 kept).
#   scripts/release-smoke.sh app/build/outputs/apk/release/app-release-unsigned.apk
#
# The APK is signed here with a key made for this run and thrown away: it proves the build works,
# it is not a release candidate (the real signing key stays with the owner, outside Git).
# Nothing is typed into the app and no account is used; guest mode reads public pages only.
set -euo pipefail

APK="${1:?usage: $0 <release-apk>}"
PACKAGE="ru.colabike.app"
SDK="${ANDROID_HOME:?ANDROID_HOME is not set}"
ADB="$SDK/platform-tools/adb"
APKSIGNER="$(ls "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() {
  echo "::error::$1"
  echo "--- screen text at the failure"
  screen | grep -o '\(text\|content-desc\)="[^"]\+"' | head -n 60 || true
  echo "--- crashes"
  timeout 30 "$ADB" logcat -d -b crash 2>/dev/null | tail -n 60 || true
  exit 1
}

# The view hierarchy as XML; Compose puts text and descriptions into it through accessibility.
screen() {
  "$ADB" shell uiautomator dump /sdcard/colabike-ui.xml >/dev/null 2>&1 || true
  "$ADB" shell cat /sdcard/colabike-ui.xml 2>/dev/null | tr -d '\r' || true
}

# Waits up to $2 seconds until the screen has an element with this text or description.
wait_for() {
  local what="$1" seconds="${2:-60}" end=$((SECONDS + ${2:-60}))
  while [ "$SECONDS" -lt "$end" ]; do
    if screen | grep -F -e "text=\"$what\"" -e "content-desc=\"$what\"" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  fail "'$what' did not appear within ${seconds} s"
}

# Taps the middle of the first element with this text or description. A node lists its text, then
# its description, and its bounds last, all inside one tag.
tap() {
  local what="$1" bounds x y
  bounds="$(screen |
    grep -o "\(text\|content-desc\)=\"$what\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" |
    head -n 1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' || true)"
  [ -n "$bounds" ] || fail "no '$what' on the screen to tap"
  read -r x y < <(echo "$bounds" | sed 's/\]\[/,/; s/[][]//g' | tr ',' ' ' |
    awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }')
  "$ADB" shell input tap "$x" "$y"
}

[ -n "$APKSIGNER" ] || fail "apksigner is not in the SDK build tools"
keytool -genkeypair -keystore "$WORK/smoke.jks" -storepass android -keypass android -alias smoke \
  -keyalg RSA -keysize 2048 -validity 2 -dname "CN=colabike-release-smoke" >/dev/null 2>&1
"$APKSIGNER" sign --ks "$WORK/smoke.jks" --ks-pass pass:android --key-pass pass:android \
  --out "$WORK/app.apk" "$APK"

# The debug build of the instrumented tests has another signature: it has to go first.
"$ADB" uninstall "$PACKAGE" >/dev/null 2>&1 || true
"$ADB" install "$WORK/app.apk" >/dev/null || fail "the release APK did not install"
"$ADB" logcat -c
"$ADB" shell settings put global window_animation_scale 0

echo "1. cold start opens sign-in"
"$ADB" shell am start -W -n "$PACKAGE/.MainActivity" | grep -E 'Status|LaunchState|TotalTime' || true
wait_for "Вход в ColaBike" 90

echo "2. guest mode opens the shell"
tap "Смотреть без входа"
wait_for "Сообщения" 60

echo "3. the Messages section for a guest explains and asks to sign in"
tap "Сообщения"
wait_for "Сообщения — для участников" 30

echo "4. after the system kills the process the person returns to the same section"
"$ADB" shell input keyevent KEYCODE_HOME
sleep 3
"$ADB" shell am kill "$PACKAGE"
sleep 2
if "$ADB" shell pidof "$PACKAGE" | grep -q '[0-9]'; then
  fail "the process is still alive after am kill: the run would not show a restore"
fi
"$ADB" shell am start -W -n "$PACKAGE/.MainActivity" | grep -E 'Status|LaunchState|TotalTime' || true
wait_for "Сообщения — для участников" 60

echo "5. no crash in the log"
if timeout 30 "$ADB" logcat -d -b crash 2>/dev/null | grep -F "$PACKAGE" >/dev/null; then
  fail "the app crashed"
fi
echo "The R8 release build starts, opens the shell and restores its section"
