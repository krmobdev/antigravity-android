#!/usr/bin/env bash
# Validate the real packaging phase without needing a 200+ MB core runtime.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WEB_DIR="$ROOT/web"
BUS_DIR="$ROOT/bus"
STAGE_ASSETS="$ROOT/.agy-web-stage-smoke/assets"
if [ -e "$ROOT/.agy-web-stage-smoke" ]; then
  echo "Refusing to overwrite existing staging fixture" >&2
  exit 2
fi
mkdir -p "$STAGE_ASSETS"
trap 'rm -rf "$ROOT/.agy-web-stage-smoke"' EXIT
log() { printf '%s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
source "$ROOT/build/phases/05-web-bus.sh"
python3 - "$ROOT" "$STAGE_ASSETS" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
stage = Path(sys.argv[2]) / "web"
upstream = (root / "web/main.js").read_text()
packaged = (stage / "main.js").read_text()
assert "AGY_RESUME_STREAM_V1" not in upstream, "upstream bundle unexpectedly mutated"
assert packaged.count("AGY_RESUME_STREAM_V1") == 1, "staged bundle lacks resume patch"
assert not (stage / "tests").exists(), "build-only tests accidentally packaged"
assert not (stage / "patch_stream_resume.py").exists(), "patch tool accidentally packaged"
for tool in ["patch_agent_notifications.py", "patch_notification_navigation.py", "patch_sidebar_reconcile.py"]:
    assert not (stage / tool).exists(), "build-only patch tool packaged: " + tool
for marker in ["AGY_NATIVE_NOTIFY_V1", "AGY_VISIBLE_CONVERSATION_V1", "AGY_CONVERSATION_DEEPLINK_V1"]:
    assert packaged.count(marker) == 1, "missing staged hook: " + marker
    assert marker not in upstream, "source bundle changed: " + marker
print("PASS: APK staging patch exists only in assets/web/main.js")
PY
