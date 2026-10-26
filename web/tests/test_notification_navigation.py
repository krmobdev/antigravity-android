"""Verify committed route reporting and notification navigation fail closed."""
from pathlib import Path
import importlib.util
import subprocess
import unittest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("navigation",ROOT/"patch_runtime_v24.py")
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class NavigationTests(unittest.TestCase):
    def test_real_bundle_and_idempotence(self):
        source=(ROOT/"main.js").read_text();patched=p.patch(source)
        self.assertEqual(p.patch(patched),patched)
        self.assertEqual(patched.count(p.ROUTE_MARKER),1)
        self.assertNotIn(p.ROUTE_MARKER,source)
    def test_changed_route_anchor_rejected(self):
        with self.assertRaises(ValueError):
            p.patch((ROOT/"main.js").read_text().replace(p.ROUTE_ANCHOR,"missing"))
    def test_committed_routes(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            target=Path(temp)/"main.js"
            target.write_text(p.patch((ROOT/"main.js").read_text()))
            subprocess.run(["node",str(ROOT/"tests/runtime_v24.behavior.cjs"),str(target)],check=True)
