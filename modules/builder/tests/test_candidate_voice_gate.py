"""Release verification must reject current-version APKs with stale voice code."""
from pathlib import Path
import importlib.util,sys,unittest
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location("kr_candidate",Path(__file__).with_name("verify_kr_candidate.py"))
candidate=importlib.util.module_from_spec(spec);spec.loader.exec_module(candidate)
sys.path.insert(0,str(ROOT/"web"))
from patch_mobile_composer import transform

class CandidateVoiceGate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.main=transform((ROOT/"web/main.js").read_text()).encode()

    def test_actual_current_bundle_is_accepted(self):
        candidate.verify_voice_payload(self.main)

    def test_each_missing_delivery_fix_rejects_candidate(self):
        for marker in candidate.VOICE_MARKERS:
            with self.subTest(marker=marker),self.assertRaises(AssertionError):
                candidate.verify_voice_payload(self.main.replace(marker,b""))

    def test_large_bundle_credential_scan_is_enforced(self):
        with self.assertRaises(AssertionError):
            candidate.verify_voice_payload(self.main+b"\n"+b"ghp_"+b"A"*36)

if __name__=="__main__":unittest.main()
