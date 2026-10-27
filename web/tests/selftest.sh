#!/usr/bin/env bash
# Build web assets on a disposable copy and prove upstream main.js stays intact.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="${TMPDIR:-${HOME:-.}/.tmp}"
mkdir -p "$TMP_DIR"
TMP="$(mktemp -d "$TMP_DIR/agy-web-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
cp -a "$ROOT/web" "$TMP/web"
before="$(sha256sum "$TMP/web/main.js" | cut -d' ' -f1)"
bash "$TMP/web/build.sh"
after="$(sha256sum "$TMP/web/main.js" | cut -d' ' -f1)"
[ "$before" = "$after" ] || { echo "web/build.sh modified source main.js" >&2; exit 1; }
[ -s "$TMP/web/generated/main.js" ] || { echo "generated/main.js missing" >&2; exit 1; }
grep -Fq 'src="./generated/main.js"' "$TMP/web/index.html"

# Compare the generated wrapper with every source fragment instead of assuming
# that an internal, intentionally scoped fragment IIFE is an extra bundle wrapper.
python3 - "$TMP/web" <<'PY'
from pathlib import Path
import re
import sys
web=Path(sys.argv[1])
bundles=[]
current=None
for raw in (web/"src.manifest").read_text().splitlines():
    line=raw.strip()
    if not line or line.startswith(("#",";")):continue
    fields=line.split()
    if fields[0]=="bundle":
        target=fields[-1]
        current=[target,fields[1]=="iife",[]]
        bundles.append(current)
    elif line.startswith("src/"):
        assert current is not None
        current[2].append(line)
    else:raise AssertionError(f"Invalid manifest entry {line}")
starts=re.compile(r"(?m)^\(function\(")
for name,wrapped,sources in bundles:
    generated=(web/"generated"/name).read_text()
    inside=sum(len(starts.findall((web/fragment).read_text())) for fragment in sources)
    found=len(starts.findall(generated))
    assert inside <= found <= inside+int(wrapped), (
        f"Unexpected IIFE wrapper count in {name}: source={inside} generated={found}")
    if wrapped and found==inside+1:
        assert generated.startswith("(function(){\n"), name
        assert generated.rstrip().endswith("})();"), name
print("PASS bundle wrappers match manifest; intentionally scoped fragment IIFEs permitted")
PY

node "$ROOT/web/tests/scroll_timeline_only_ab.test.cjs" "$TMP/web/generated/main.js"
node "$ROOT/web/tests/sidebar_react_v24.behavior.cjs" "$TMP/web/generated/main.js"
node "$ROOT/web/tests/runtime_v24.behavior.cjs" "$TMP/web/generated/main.js"
node "$ROOT/web/tests/sidebar_reconcile.behavior.cjs" \
    "$ROOT/web/src/patches/web_optimizations/sidebar-reconcile.js" \
    "$ROOT/web/src/patches/web_optimizations/agent-notifications.js"
node "$ROOT/web/tests/mobile_attachments.behavior.cjs"
node "$ROOT/web/tests/mobile_composer.behavior.cjs"
node "$ROOT/web/tests/adaptive_desktop.behavior.cjs"
node "$ROOT/web/tests/mobile_settings.behavior.cjs"
node "$ROOT/web/tests/mobile_permissions.behavior.cjs"
node "$ROOT/web/tests/mobile_back.behavior.cjs"
node "$ROOT/web/tests/mobile_audio.behavior.cjs"
node "$ROOT/web/tests/startup_ready.behavior.cjs"
node "$ROOT/web/tests/theme_startup.behavior.cjs"
python3 "$ROOT/web/tests/test_mobile_ui_patch.py"
python3 "$ROOT/web/tests/test_ui_english.py"
echo "web build selftest: OK"
