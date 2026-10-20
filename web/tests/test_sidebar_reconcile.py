"""Regression for stale summary after confirmed agent completion."""
import importlib.util
from pathlib import Path
import subprocess, tempfile, unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("sidebar_patch",ROOT/"patch_sidebar_reconcile.py")
patcher=importlib.util.module_from_spec(spec);spec.loader.exec_module(patcher)

class SidebarReconcileTests(unittest.TestCase):
    def test_native_completion_reaches_retained_page_on_first_resume(self):
        subprocess.run(["node",str(ROOT/"tests/sidebar_resume.behavior.cjs"),
                        str(ROOT/"src/patches/web_optimizations/sidebar-reconcile.js")],check=True)

    def test_bundle_is_safe_and_idempotent(self):
        orig=(ROOT/"main.js").read_text()
        result=patcher.patch(orig)
        self.assertEqual(result.count(patcher.MARKER),1)
        self.assertEqual(result.count(patcher.XW_MARKER),1)
        self.assertIn('return((b?e:c)&&!verifiedIdle)||f}',result)
        self.assertIn(patcher.XW_REPLACEMENT,result)
        self.assertEqual(patcher.patch(result),result)
        self.assertNotIn(patcher.MARKER,orig)
        with tempfile.TemporaryDirectory() as d:
            file=Path(d)/"main.js";file.write_text(result)
            subprocess.run(["node","--check",str(file)],check=True)

    def test_upstream_changes_fail_closed(self):
        s=(ROOT/"main.js").read_text()
        with self.assertRaisesRegex(ValueError,"helper changed"):
            patcher.patch(s.replace(patcher.ANCHOR,"function VW_CHANGED(){}",1))

    def test_secondary_running_predicate_change_is_detected(self):
        s=(ROOT/"main.js").read_text()
        with self.assertRaisesRegex(ValueError,"secondary running predicate"):
            patcher.patch(s.replace(patcher.XW_ANCHOR,"m=m===\"running\";",1))

    def test_stale_summary_vs_live_activity(self):
        subprocess.run(["node",str(ROOT/"tests/sidebar_reconcile.behavior.cjs"),
                        str(ROOT/"src/patches/web_optimizations/sidebar-reconcile.js"),
                        str(ROOT/"src/patches/web_optimizations/agent-notifications.js")],
                        check=True)

if __name__ == "__main__":unittest.main()
