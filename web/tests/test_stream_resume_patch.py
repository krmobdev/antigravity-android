"""Regression tests for Android WebView stream resume patch."""
import importlib.util
from pathlib import Path
import tempfile
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("stream_resume_patch", ROOT / "patch_stream_resume.py")
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)


class ResumePatchTests(unittest.TestCase):
    def test_upstream_bundle_is_patchable_and_source_is_unchanged(self):
        before = (ROOT / "main.js").read_text(encoding="utf-8")
        after = patcher.patch_bundle(before)
        self.assertNotEqual(before, after)
        self.assertEqual(after.count(patcher.MARKER), 1)
        self.assertEqual(before, (ROOT / "main.js").read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "main.js"
            target.write_text(after, encoding="utf-8")
            subprocess.run(["node", "--check", str(target)], check=True)

    def test_idempotence(self):
        before = (ROOT / "main.js").read_text(encoding="utf-8")
        patched = patcher.patch_bundle(before)
        self.assertEqual(patcher.patch_bundle(patched), patched)

    def test_fails_closed_if_upstream_changes(self):
        before = (ROOT / "main.js").read_text(encoding="utf-8")
        modified = before.replace(patcher.REPLACEMENTS[1][0], "/* upstream changed */", 1)
        with self.assertRaisesRegex(ValueError, "upstream stream code changed"):
            patcher.patch_bundle(modified)

    def test_resume_reconnects_without_stopping_agent(self):
        before = (ROOT / "main.js").read_text(encoding="utf-8")
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "main.js"
            target.write_text(patcher.patch_bundle(before), encoding="utf-8")
            subprocess.run(
                ["node", str(ROOT / "tests/resume_stream.behavior.test.cjs"), str(target)],
                check=True, timeout=20,
            )


if __name__ == "__main__":
    unittest.main()
