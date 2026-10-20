#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROJECT="$ROOT"
export PROJECT
source "$ROOT/build/lib/paths.sh"
setup_paths
HERE="$ROOT/tests/instrumentation"
OUT="$HERE/out"
mkdir -p "$OUT/classes" "$OUT/dex"
javac -encoding UTF-8 -d "$OUT/classes" -cp "$ANDROID_JAR" "$HERE/SidebarInstrumentation.java" "$HERE/LifecycleInstrumentation.java"
mapfile -d '' CLASS_FILES < <(find "$OUT/classes" -name '*.class' -print0)
java -cp "$R8_JAR" com.android.tools.r8.D8 --output "$OUT/dex" --lib "$ANDROID_JAR" --min-api 24 "${CLASS_FILES[@]}"
aapt package -f -M "$HERE/AndroidManifest.xml" -I "$ANDROID_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$STORE_PASS" --key-pass "pass:$KEY_PASS" --out "$OUT/agy-sidebar-tests.apk" "$OUT/aligned.apk"
apksigner verify --verbose "$OUT/agy-sidebar-tests.apk" | grep 'Verified using v2'
aapt dump badging "$OUT/agy-sidebar-tests.apk" | grep "^package:"
echo "TEST_APK=$OUT/agy-sidebar-tests.apk"
