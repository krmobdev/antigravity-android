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
script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
input="$downloads/$name-unsigned.apk"
# The owner can sign directly from the prepared checkout; bridge access to
# public Downloads is not required to read the unsigned input.
if [ ! -s "$input" ]; then
    if [ "$edition" = "standard" ]; then
        input="$script_root/dist/antigravity-v2.4.5-kr1-unsigned.apk"
    else
        input="$script_root/dist/antigravity-v2.4.5-kr1-region-bypass-unsigned.apk"
    fi
fi
output="$downloads/$name.apk"
keystore="$HOME/anima-workspace/.antigravity-release-identity/antigravity-kr-release.jks"
expected="23adac86423f7298b0a8ccd48758e6e19be7654d4fbdec184e184b7e16030d95"
[ -s "$input" ] || { echo "Missing unsigned APK: $input" >&2; exit 1; }
[ -s "$keystore" ] || { echo "Release keystore missing. Never generate a replacement." >&2; exit 1; }
[ ! -e "$output" ] || { echo "Refusing to overwrite an existing signed APK: $output" >&2; exit 1; }
command -v apksigner >/dev/null || { echo "apksigner missing" >&2; exit 1; }
command -v aapt >/dev/null || { echo "aapt missing" >&2; exit 1; }
# Require the audited unsigned input, including when Downloads has a stale copy.
if [ "$edition" = "standard" ]; then
    checksum_name="antigravity-v2.4.5-kr1-unsigned.apk"
else
    checksum_name="antigravity-v2.4.5-kr1-region-bypass-unsigned.apk"
fi
checksum_file="$script_root/docs/V2_4_5_KR1_SHA256SUMS.txt"
[ -s "$checksum_file" ] || { echo "Missing audited unsigned SHA256SUMS." >&2; exit 1; }
expected_input="$(awk -v name="$checksum_name" '$2 == name {print $1}' "$checksum_file")"
actual_input="$(sha256sum "$input" | awk '{print $1}')"
[ -n "$expected_input" ] && [ "$actual_input" = "$expected_input" ] || {
    echo "ERROR: unsigned input checksum mismatch. Refusing signing." >&2; exit 1;
}
# Validate the public input before asking apksigner to open private material.
input_badging="$(aapt dump badging "$input" | sed -n '1p')"
case "$input_badging" in
  *"name='com.krmobdev.antigravity'"*"versionCode='19'"*"versionName='2.4.5-kr1'"*) ;;
  *) echo "ERROR: unexpected unsigned input package/version." >&2; exit 1 ;;
esac
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
