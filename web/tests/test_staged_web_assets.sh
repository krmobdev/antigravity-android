#!/usr/bin/env bash
# Exercise current 05_stage_assets.py using a stub Core and real web/build.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import hashlib, importlib.util, shutil, subprocess, sys, tempfile, types
root = Path(sys.argv[1])
with tempfile.TemporaryDirectory(prefix="agy-staging-regression-") as d:
    tmp = Path(d)
    project = tmp / "project"
    shutil.copytree(root / "web", project / "web", ignore=shutil.ignore_patterns("__pycache__"))
    (project / "env/dist/env").mkdir(parents=True)
    (project / "env/dist/env/manifest.json").write_text("{}\n")
    upstream = (project / "web/main.js").read_bytes()
    generated_before = (project / "web/generated/main.js").read_bytes()
    calls = []
    fake = types.ModuleType("runner")
    def prepare(dest_runtime_dir, no_bypass):
        dest = Path(dest_runtime_dir)
        dest.mkdir(parents=True)
        (dest / "build-variant.txt").write_text("bypass" if not no_bypass else "standard")
        calls.append(no_bypass)
        return True
    fake.prepare_runtime = types.SimpleNamespace(prepare=prepare)
    sys.modules["runner"] = fake
    spec = importlib.util.spec_from_file_location("stage_assets_under_test", root / "modules/builder/05_stage_assets.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.PROJECT_ROOT = str(project)
    data = []
    for variant, no_bypass in (("standard", True), ("bypass", False), ("repeated-standard", True)):
        output = tmp / variant
        mod.stage_assets(str(output), no_bypass=no_bypass)
        assets = output / "assets"
        web = assets / "web"
        js = (web / "generated/main.js").read_text()
        # Test the exact JS packed into each edition; not stale checkout generated files.
        subprocess.run(["node", str(root / "web/tests/notification_back_flow.behavior.cjs"),
                        str(web / "generated/main.js")], check=True)
        assert js.count("AGY_SCROLL_TIMELINE_ONLY_AB") == 1, "ScrollTimeline patch missing"
        assert "function G$a(){var a=globalThis.ScrollTimeline" not in js
        for marker in ("AGY_V24_TRANSPORT_RECONNECT", "AGY_V24_SUMMARY_RECONNECT",
                       "AGY_V24_DEEPLINK", "AGY_SIDEBAR_RECONCILE_V24",
                       "AGY_SIDEBAR_RUNNING_RECONCILE_V24", "AGY_ATTACHMENTS_KR1", "AGY_COMPOSER_KR1"):
            assert js.count(marker) == 1, ("web hook missing or duplicated", marker)
        assert (web / "index.html").exists()
        assert (web / "compiled_tailwind.css").exists()
        assert (web / "generated/main.js").exists()
        assert not (web / "main.js").exists(), "raw upstream source packaged"
        assert not (web / "src").exists() and not (web / "tests").exists()
        assert not (web / "src.manifest").exists()
        forbidden = [p for p in web.rglob("*") if p.is_file() and p.suffix in (".py", ".sh", ".cjs")]
        assert not forbidden, "Build tools packed into APK: " + repr(forbidden)
        assert (assets / "runtime/build-variant.txt").read_text() == ("standard" if no_bypass else "bypass")
        data.append(hashlib.sha256((web / "generated/main.js").read_bytes()).hexdigest())
    assert (project / "web/generated/main.js").read_bytes() == generated_before, "pre-existing generated work overwritten"
    assert calls == [True, False, True], "variant dispatch altered"
    assert len(set(data)) == 1, "staged WebView code differs between variants / repeat builds"
    assert (project / "web/main.js").read_bytes() == upstream, "upstream web/main.js modified"
    print("PASS: real web/build.sh + 05_stage_assets.py (standard / region-bypass / rebuild)")
    print("PASS: only generated web code staged, no Python/shell test/build helpers")
    print("PASS: native notification, stream recovery, navigation and scrollbar hooks retained")
PY
