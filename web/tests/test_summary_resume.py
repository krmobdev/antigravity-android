import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

WEB=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('stream_patch',WEB/'patch_stream_resume.py')
patcher=importlib.util.module_from_spec(spec);spec.loader.exec_module(patcher)

class SummaryResumeTests(unittest.TestCase):
    def test_real_middleware_reconnects_and_ignores_old_generation(self):
        source=patcher.patch_bundle((WEB/'main.js').read_text())
        fragment=source[source.index('function Vya(){'):source.index(';function nt(',source.index('function Vya(){'))]
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'summary.js';p.write_text(fragment)
            subprocess.run(['node',str(WEB/'tests/summary_resume.behavior.cjs'),str(p)],check=True)

    def test_patch_integrity(self):
        source=(WEB/'main.js').read_text();patched=patcher.patch_bundle(source)
        self.assertEqual(patcher.patch_bundle(patched),patched)
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'main.js';p.write_text(patched)
            subprocess.run(['node','--check',str(p)],check=True)
        with self.assertRaises(ValueError):
            patcher.patch_bundle(source.replace('function Vya(){','function VyaChanged(){',1))
