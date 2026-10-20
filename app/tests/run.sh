#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/app/tests/out"
mkdir -p "$OUT"
javac -d "$OUT" \
    "$ROOT/app/src/main/java/com/agy/bridge/AgentTaskTracker.java" \
    "$ROOT/app/src/main/java/com/agy/bridge/AgentCompletionQueue.java" \
    "$ROOT/app/src/main/java/com/agy/core/ConnectFrameReader.java" \
    "$ROOT/app/tests/AgentTaskTrackerTest.java" \
    "$ROOT/app/tests/AgentCompletionQueueTest.java" \
    "$ROOT/app/tests/ConnectFrameReaderTest.java"
java -cp "$OUT" AgentTaskTrackerTest
java -cp "$OUT" com.agy.bridge.AgentCompletionQueueTest
java -cp "$OUT" ConnectFrameReaderTest


# Isolated security tests run against pure-Java parts of the production
# GatewayAuthToken and file writer, with Android API stubs for FileModeUtil.
javac -cp "$ROOT/tools/android.jar" -d "$OUT" \
    "$ROOT/app/src/main/java/com/agy/util/IoUtil.java" \
    "$ROOT/app/src/main/java/com/agy/util/FileModeUtil.java" \
    "$ROOT/app/src/main/java/com/agy/util/AtomicFileWriter.java" \
    "$ROOT/app/src/main/java/com/agy/gateway/GatewayAuthToken.java" \
    "$ROOT/app/tests/GatewayAuthTokenTest.java"
java -cp "$OUT" com.agy.gateway.GatewayAuthTokenTest
