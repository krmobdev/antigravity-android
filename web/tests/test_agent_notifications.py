"""Regression coverage for the staged native notification status hook."""
from pathlib import Path
import importlib.util
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("patch", ROOT / "patch_agent_notifications.py")
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)


class NotificationPatchTests(unittest.TestCase):
    def test_bundle_patch_is_safe_and_idempotent(self):
        source = (ROOT / "main.js").read_text()
        patched = patcher.patch(source)
        self.assertEqual(patched.count(patcher.MARKER), 1)
        self.assertEqual(patcher.patch(patched), patched)
        self.assertNotIn(patcher.MARKER, source)
        with tempfile.TemporaryDirectory() as d:
            target = Path(d) / "main.js"
            target.write_text(patched)
            subprocess.run(["node", "--check", str(target)], check=True)

    def test_fails_closed_if_upstream_anchor_moves(self):
        source = (ROOT / "main.js").read_text()
        changed = source.replace(patcher.ANCHOR, "", 1)
        with self.assertRaisesRegex(ValueError, "anchor changed"):
            patcher.patch(changed)

    def test_browser_notifier_with_synthetic_states(self):
        subprocess.run(
            ["node", str(ROOT / "tests/agent_notifications.behavior.cjs"),
             str(ROOT / "src/patches/web_optimizations/agent-notifications.js")],
            check=True
        )


if __name__ == "__main__":
    unittest.main()
