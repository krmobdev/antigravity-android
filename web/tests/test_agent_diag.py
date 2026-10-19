"""Diagnostic-only main.js hook regression tests."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("diagpatch", ROOT / "patch_agent_diag.py")
diagpatch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diagpatch)

class DiagnosticPatchTests(unittest.TestCase):
    def test_patch_is_valid_idempotent_and_nonmutating(self):
        src = (ROOT / "main.js").read_text(encoding="utf8")
        patched = diagpatch.patch(src)
        self.assertEqual(patched.count(diagpatch.MARKER), 1)
        self.assertEqual(patched.count("__AGY_DIAG_SIDEBAR__"), 1)
        self.assertEqual(diagpatch.patch(patched), patched)
        self.assertEqual(src, (ROOT / "main.js").read_text(encoding="utf8"))
        with tempfile.TemporaryDirectory() as d:
            filename = Path(d) / "main.js"
            filename.write_text(patched, encoding="utf8")
            subprocess.run(["node", "--check", str(filename)], check=True)

    def test_break_on_upstream_change(self):
        src = (ROOT / "main.js").read_text(encoding="utf8")
        changed = src.replace(diagpatch.ANCHORS[1][0], "upstream changed", 1)
        with self.assertRaisesRegex(ValueError, "anchor mismatch"):
            diagpatch.patch(changed)

if __name__ == "__main__":
    unittest.main()
