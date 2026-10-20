"""Verify committed route reporting and notification navigation fail closed."""
from pathlib import Path
import importlib.util
import subprocess
import unittest
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("navigation",ROOT/"patch_notification_navigation.py")
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class NavigationTests(unittest.TestCase):
    def test_real_bundle_and_idempotence(self):
        source=(ROOT/"main.js").read_text();patched=p.patch(source)
        self.assertEqual(p.patch(patched),patched)
        self.assertEqual(patched.count(p.CONTEXT_MARKER),1)
        self.assertNotIn(p.CONTEXT_MARKER,source)
    def test_changed_route_anchor_rejected(self):
        with self.assertRaisesRegex(ValueError,"committed route"):
            p.patch((ROOT/"main.js").read_text().replace(p.CONTEXT_ANCHOR,"missing"))
    def test_committed_routes(self):
        subprocess.run(["node",str(ROOT/"tests/notification_navigation.behavior.cjs"),str(ROOT/"patch_notification_navigation.py")],check=True)
