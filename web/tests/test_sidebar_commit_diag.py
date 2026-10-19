"""Diagnostics are observational and record committed XW render values."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

WEB = Path(__file__).resolve().parents[1]
def load(name, file):
    spec = importlib.util.spec_from_file_location(name, WEB/file)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m

agent_diag = load("agent_diag_for_commit", "patch_agent_diag.py")
reconcile = load("reconcile_for_diag", "patch_sidebar_reconcile.py")
commit = load("commit_diag", "patch_sidebar_commit_diag.py")

class CommitDiagTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base = reconcile.patch(agent_diag.patch((WEB/"main.js").read_text(encoding="utf8")))
        cls.actual = commit.patch(cls.base)

    def test_instrumentation_runs_after_reconciliation_and_is_idempotent(self):
        self.assertEqual(commit.patch(self.actual), self.actual)
        self.assertEqual(self.actual.count(commit.MARKER), 1)
        self.assertIn("z.useLayoutEffect", self.actual)
        self.assertIn("(0,z.useId)()", self.actual)
        self.assertNotIn("window.__AGY_DIAG_XW_COMMIT__?.(", self.base)
        with self.assertRaisesRegex(ValueError, "reconciliation patch"):
            commit.patch((WEB/"main.js").read_text(encoding="utf8"))

    def test_original_render_predicates_are_preserved(self):
        # The exact original condition and its single native-get short circuit survive.
        self.assertIn('m=m==="running"&&!window.__AGY_VERIFIED_AGENT_GET__?.(a)',self.actual)
        self.assertIn('h=g===R.CascadeRunStatus.IDLE&&h===!1',self.actual)
        self.assertIn('isLoading:f||m,isBackgroundWorking:h',self.actual)
        self.assertIn('var __agyVWResult=((b?e:c)&&!verifiedIdle)||f;',self.actual)
        self.assertIn('return __agyVWResult}',self.actual)
        self.assertEqual(self.actual.count("window.__AGY_DIAG_XW_COMMIT__?.("),1)
        self.assertNotIn('window.__AGY_DIAG_SIDEBAR__?.(', self.actual)

    def test_anchor_changes_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "anchor changed"):
            commit.patch(self.base.replace(commit.BEFORE_STATUS_OLD, "missing", 1))

    def test_node_syntax(self):
        with tempfile.TemporaryDirectory() as temp:
            p = Path(temp)/"main.js"
            p.write_text(self.actual, encoding="utf8")
            subprocess.run(["node","--check",str(p)],check=True)

    def test_priority_and_commit_dedup_isolation(self):
        subprocess.run(["node",str(WEB/"tests/sidebar_commit_diag.behavior.cjs"),
                        str(WEB/"src/patches/web_optimizations/agent-diag.js")],check=True)

if __name__ == "__main__":
    unittest.main()
