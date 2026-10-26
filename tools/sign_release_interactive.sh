#!/usr/bin/env bash
# Manual owner-only signing. Private passwords are requested by apksigner,
# never passed as argv, stored in logs or transmitted to GitHub.
set -euo pipefail
edition="${1:-}"
case "$edition" in
  standard)
    name="Antigravity-Android-v2.4.5-kr1"
    ;;
  bypass)
    name="Antigravity-Android-v2.4.5-kr1-region-bypass"
    ;;
  *)
    echo "Usage: bash tools/sign_release_interactive.sh {standard|bypass}" >&2
    exit 2
    ;;
esac
downloads="/storage/emulated/0/Download"
input="$downloads/$name-unsigned.apk"
output="$downloads/$name.apk"
keystore="$HOME/anima-workspace/.antigravity-release-identity/antigravity-kr-release.jks"
expected="23adac86423f7298b0a8ccd48758e6e19be7654d4fbdec184e184b7e16030d95"
[ -s "$input" ] || { echo "Missing unsigned APK: $input" >&2; exit 1; }
[ -s "$keystore" ] || { echo "Release keystore missing. Never generate a replacement." >&2; exit 1; }
[ ! -e "$output" ] || { echo "Refusing to overwrite an existing signed APK: $output" >&2; exit 1; }
command -v apksigner >/dev/null || { echo "apksigner missing" >&2; exit 1; }
command -v aapt >/dev/null || { echo "aapt missing" >&2; exit 1; }
echo "Signing: $name"
echo "Enter permanent store/key passwords only when apksigner prompts in Termux."
# Without --ks-pass/--key-pass, apksigner prompts directly on the owner's terminal.
apksigner sign --ks "$keystore" --ks-key-alias antigravity-kr-release-v1 --out "$output" "$input"
details="$(apksigner verify --verbose --print-certs "$output")"
printf '%s\n' "$details" | grep -E 'Verified using v2|certificate SHA-256 digest'
printf '%s\n' "$details" | grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true'
actual="$(printf '%s\n' "$details" | awk '/certificate SHA-256 digest:/ { if (digest == "") digest = $NF } END { print digest }' | tr -d ':[:space:]' | tr 'A-F' 'a-f')"
[ "$actual" = "$expected" ] || { echo "ERROR: unexpected signing certificate. DO NOT PUBLISH." >&2; exit 1; }
badging="$(aapt dump badging "$output" | sed -n '1p')"
printf '%s\n' "$badging"
case "$badging" in
  *"name='com.krmobdev.antigravity'"*"versionCode='19'"*"versionName='2.4.5-kr1'"*) ;;
  *) echo "ERROR: wrong package or version. DO NOT PUBLISH." >&2; exit 1 ;;
esac
sha256sum "$output"
echo "SIGNED AND VERIFIED: $output"
