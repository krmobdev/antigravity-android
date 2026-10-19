"""Safety checks and behavior test for Android -> exact conversation routing."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sp=importlib.util.spec_from_file_location("routepatch",ROOT/"patch_notification_navigation.py")
route=importlib.util.module_from_spec(sp)
sp.loader.exec_module(route)


class NotificationRouteTests(unittest.TestCase):
    def test_patch_integrity(self):
        src=(ROOT/"main.js").read_text(encoding="utf8")
        patched=route.patch(src)
        self.assertEqual(patched.count(route.MARKER),1)
        self.assertEqual(route.patch(patched),patched)
        self.assertNotIn(route.MARKER,src)
        with tempfile.TemporaryDirectory() as d:
            staged=Path(d)/"main.js"
            staged.write_text(patched,encoding="utf8")
            subprocess.run(["node","--check",str(staged)],check=True)

    def test_fails_closed_after_upstream_router_changes(self):
        src=(ROOT/"main.js").read_text(encoding="utf8")
        with self.assertRaisesRegex(ValueError,"navigation function changed"):
            route.patch(src.replace(route.ANCHOR,"function other(){}",1))

    def test_exact_router_navigation_and_early_router(self):
        subprocess.run(
            ["node",str(ROOT/"tests/notification_navigation.behavior.cjs"),
             str(ROOT/"patch_notification_navigation.py")],
            check=True,timeout=20)


if __name__=="__main__":
    unittest.main()
