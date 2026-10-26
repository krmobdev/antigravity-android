#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_DATA="${ANDROID_DATA:-/data}"
mkdir -p out/obj out/dex
aapt package -f -M AndroidManifest.xml -S res -I ../../tools/android.jar -F out/resources.ap_
javac -source 8 -target 8 -encoding UTF-8 -cp ../../tools/android.jar -d out/obj src/com/krmobdev/v245qa/MobileCandidateProbe.java
bash ../../modules/builder/04_dex.sh out/dex out/obj ../../tools/r8.jar ../../tools/android.jar 24
cp out/resources.ap_ out/unsigned.apk
python - <<'PY'
import zipfile
with zipfile.ZipFile("out/unsigned.apk","a") as z:z.write("out/dex/classes.dex","classes.dex")
PY
zipalign -f 4 out/unsigned.apk out/aligned.apk
apksigner sign --ks ../../tools/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out out/agy-v245-qa.apk out/aligned.apk
apksigner verify out/agy-v245-qa.apk
