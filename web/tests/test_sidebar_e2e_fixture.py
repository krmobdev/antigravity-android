"""Fail-closed tests for the optional fixture build. It MUST NOT be in default APKs."""
import importlib.util
import unittest
from pathlib import Path
import subprocess
import tempfile

WEB=Path(__file__).resolve().parents[1]
P=WEB/"patch_sidebar_e2e_fixture.py"
spec=importlib.util.spec_from_file_location("fixture",P)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class E2eFixtureTest(unittest.TestCase):
    def test_patch_is_explicit_optional_and_single(self):
        original=(WEB/"main.js").read_text()
        self.assertNotIn(m.MARK, original)
        patched=m.patch(original)
        self.assertEqual(patched.count(m.MARK),1)
        self.assertEqual(m.patch(patched),patched)
        self.assertIn("window.__AGY_E2E_ENABLED__!==true",patched)
        self.assertIn("AGY_E2E_STREAM_HOLD_V1",patched)
        self.assertIn("c.dispatch(Fo({updates:[[id,copy]],deletes:[]}))",patched)
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/"main.js"
            p.write_text(patched)
            subprocess.run(["node","--check",str(p)],check=True)
    def test_source_mutation_is_scoped_only_to_fixture(self):
        original=(WEB/"main.js").read_text()
        patched=m.patch(original)
        self.assertEqual(patched.replace(m.CODE,m.ANCHOR,1).replace(m.STREAM_CODE,m.STREAM_ANCHOR,1),original)
        with self.assertRaises(ValueError):
            m.patch(original.replace(m.ANCHOR,"noop",1))
    def test_default_stage_does_not_include_fixture(self):
        stage=(WEB.parent/"build/phases/05-web-bus.sh").read_text()
        self.assertIn('AGY_SIDEBAR_E2E:-0',stage)
        self.assertIn('--exclude=patch_sidebar_e2e_fixture.py',stage)
if __name__=="__main__":
    unittest.main()
