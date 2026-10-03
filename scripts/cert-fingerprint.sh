#!/usr/bin/env bash
# Prints the SHA-256 of the signing certificate: the value of ANDROID_CERT_SHA256 on the site
# (cola docs/operations/deployment.md), which lets /.well-known/assetlinks.json verify the app's
# App Links (https://colabike.ru/app/auth for the native Yandex ID sign-in).
#   scripts/cert-fingerprint.sh app/build/outputs/apk/release/app-release.apk
#   scripts/cert-fingerprint.sh path/to/release.keystore [alias]
#
# Only the certificate's public fingerprint is read and printed; no key or password is. Use the
# fingerprint of the key the INSTALLABLE build is signed with (the release key for RuStore). Do not
# add a debug key to the site's list on a whim: whoever holds that key can then claim the links.
set -euo pipefail

target="${1:?usage: $0 <apk|keystore> [alias]}"
[ -f "$target" ] || { echo "no such file: $target" >&2; exit 1; }

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$sdk" ] || sdk="$(sed -n 's/^sdk.dir=//p' "$(dirname "$0")/../local.properties" 2>/dev/null || true)"

case "$target" in
  *.apk)
    apksigner="$(ls -1 "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
    [ -n "$apksigner" ] || { echo "apksigner not found: set ANDROID_HOME" >&2; exit 1; }
    "$apksigner" verify --print-certs "$target" |
      sed -n 's/^.*certificate SHA-256 digest: //p' | sort -u |
      sed 's/../&:/g; s/:$//' | tr 'a-f' 'A-F'
    ;;
  *)
    keytool -list -v -keystore "$target" ${2:+-alias "$2"} |
      sed -n 's/^[[:space:]]*SHA256: //p'
    ;;
esac
