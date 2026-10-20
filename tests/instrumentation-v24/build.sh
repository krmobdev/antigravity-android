#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JAR="../../tools/android.jar"
KEY="../../tools/debug.keystore"
mkdir -p out/classes out/dex
aapt package -f -M AndroidManifest.xml -S res -I "$JAR" -F out/resources.ap_
javac -source 8 -target 8 -encoding UTF-8 -cp "$JAR" -d out/classes src/com/agy/v24readiness/*.java
java -cp ../../tools/r8.jar com.android.tools.r8.D8 --min-api 24 --lib "$JAR" --output out/dex out/classes/com/agy/v24readiness/ReadinessProbe.class out/classes/com/agy/v24readiness/LiveCoreSmoke.class out/classes/com/agy/v24readiness/SafeEvidence.class out/classes/com/agy/v24readiness/HistoricalCompletionEvidence.class
cp out/resources.ap_ out/unsigned.apk
(cd out/dex && zip -q -u ../unsigned.apk classes.dex)
zipalign -f 4 out/unsigned.apk out/aligned.apk
apksigner sign --ks "$KEY" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out out/agy-v24-readiness.apk out/aligned.apk
apksigner verify out/agy-v24-readiness.apk
echo "Ready: out/agy-v24-readiness.apk"
