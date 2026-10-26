import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

WEB=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('stream_patch',WEB/'patch_runtime_v24.py')
patcher=importlib.util.module_from_spec(spec);spec.loader.exec_module(patcher)

class SummaryResumeTests(unittest.TestCase):
    def test_real_middleware_reconnects_and_ignores_old_generation(self):
        source=patcher.patch((WEB/'main.js').read_text())
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'main.js';p.write_text(source)
            subprocess.run(['node',str(WEB/'tests/runtime_v24.behavior.cjs'),str(p)],check=True)

    def test_patch_integrity(self):
        source=(WEB/'main.js').read_text();patched=patcher.patch(source)
        self.assertEqual(patcher.patch(patched),patched)
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'main.js';p.write_text(patched)
            subprocess.run(['node','--check',str(p)],check=True)
        with self.assertRaises(ValueError):
            patcher.patch(source.replace(patcher.SUM_HEAD,'function NDaChanged(){',1))
