#!/usr/bin/env bash
# Fails unless the native libraries of an APK are ready for devices with 16 KB memory pages.
#   scripts/check-native-alignment.sh app/build/outputs/apk/release/app-release-unsigned.apk
#
# Two things must hold for the 64-bit ABIs (the 32-bit ones are exempt from the 16 KB rule):
# the library sits at a 16 KB boundary inside the APK (zipalign -P 16), and its own LOAD segments
# are aligned to 16 KB (a library built with 4 KB segments loads in compatibility mode at best).
set -euo pipefail

APK="${1:?usage: $0 <apk>}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ZIPALIGN="$(ls "$SDK"/build-tools/*/zipalign 2>/dev/null | sort -V | tail -n 1)"
[ -x "$ZIPALIGN" ] || {
  echo "::error::zipalign is not in the SDK build tools ($SDK)"
  exit 1
}

if ! "$ZIPALIGN" -c -P 16 -v 4 "$APK" >"${TMPDIR:-/tmp}/zipalign.log" 2>&1; then
  grep -v '(OK' "${TMPDIR:-/tmp}/zipalign.log" | tail -n 20 || true
  echo "::error::the native libraries are not 16 KB aligned inside the APK"
  exit 1
fi
echo "zipalign -P 16: ok"

python3 - "$APK" <<'PY'
import struct
import sys
import zipfile

MIN_ALIGN = 16384
bad = []
checked = 0
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in apk.namelist():
        parts = name.split("/")
        if len(parts) != 3 or parts[0] != "lib" or not name.endswith(".so"):
            continue
        if parts[1] not in ("arm64-v8a", "x86_64"):
            continue
        data = apk.read(name)
        if data[:4] != b"\x7fELF" or data[4] != 2:
            bad.append(f"{name}: not a 64-bit ELF")
            continue
        phoff = struct.unpack_from("<Q", data, 0x20)[0]
        phentsize, phnum = struct.unpack_from("<HH", data, 0x36)
        aligns = [
            struct.unpack_from("<Q", data, phoff + i * phentsize + 0x30)[0]
            for i in range(phnum)
            if struct.unpack_from("<I", data, phoff + i * phentsize)[0] == 1
        ]
        checked += 1
        if not aligns or min(aligns) < MIN_ALIGN:
            bad.append(f"{name}: LOAD segments aligned to {sorted(set(aligns))}")
print(f"64-bit libraries checked: {checked}")
if bad:
    print("::error::" + "; ".join(bad))
    sys.exit(1)
if checked == 0:
    print("::error::no 64-bit native library found: the check would prove nothing")
    sys.exit(1)
PY
echo "LOAD segments: ok"
