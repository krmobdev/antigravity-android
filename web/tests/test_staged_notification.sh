#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WEB_DIR="$ROOT/web"
BUS_DIR="$ROOT/bus"
TEST_DIR="$ROOT/build/notify-stage-test"
STAGE_ASSETS="$TEST_DIR/assets"
[ ! -e "$TEST_DIR" ] || { echo "Refusing existing test dir"; exit 2; }
mkdir -p "$STAGE_ASSETS"
trap 'rm -rf "$TEST_DIR"' EXIT
log() { printf '%s\n' "$*"; }
die() { printf '%s\n' "$*" >&2; exit 1; }
source "$ROOT/build/phases/05-web-bus.sh"
python3 - "$WEB_DIR" "$STAGE_ASSETS/web" <<'PY'
from pathlib import Path
import sys
original, stage = (Path(p) for p in sys.argv[1:])
assert "AGY_NATIVE_NOTIFY_V1" not in (original / "main.js").read_text()
assert (stage / "main.js").read_text().count("AGY_NATIVE_NOTIFY_V1") == 1
assert (stage / "main.js").read_text().count("AGY_CONVERSATION_DEEPLINK_V1") == 1
assert "AGY_NOTIFY_AGENT_STATE" in (stage / "generated/patches/web_optimizations.js").read_text()
assert not (stage / "patch_agent_notifications.py").exists()
assert not (stage / "patch_notification_navigation.py").exists()
assert not (stage / "tests").exists()
print("PASS: packaged JavaScript hook in staged assets only")
PY
