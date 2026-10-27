import unittest
from acceptance_result import accepted

GOOD = """INSTRUMENTATION_STATUS: stream=ANDROID_CASE=PASS_REAL_DEVICE
INSTRUMENTATION_RESULT: summary=Probe completed; inspect PASS/PENDING flags
INSTRUMENTATION_CODE: 0
"""

class AcceptanceResultTests(unittest.TestCase):
    def test_generic_summary_is_not_an_actual_pending_test(self):
        self.assertTrue(accepted(0, GOOD, "ANDROID_CASE=PASS_REAL_DEVICE"))

    def test_actual_pending_or_failure_never_passes(self):
        for status in ("PENDING", "PENDING_PERMISSION", "FAIL", "FAIL_MISSING_ATTACHMENT"):
            with self.subTest(status=status):
                self.assertFalse(accepted(0, "INSTRUMENTATION_STATUS: stream=CASE=" + status + "\n" + GOOD))

    def test_shell_success_does_not_hide_instrumentation_failure(self):
        self.assertFalse(accepted(0, GOOD.replace("CODE: 0", "CODE: 1")))
        self.assertFalse(accepted(1, GOOD))

    def test_missing_or_duplicated_completion_does_not_pass(self):
        self.assertFalse(accepted(0, GOOD.replace("INSTRUMENTATION_CODE: 0", "")))
        self.assertFalse(accepted(0, GOOD + "INSTRUMENTATION_CODE: 0\n"))

    def test_expected_scenario_must_have_run(self):
        self.assertFalse(accepted(0, GOOD, "OTHER_CASE=PASS_REAL_DEVICE"))
        self.assertFalse(accepted(0, GOOD.replace("stream=ANDROID_CASE=PASS_REAL_DEVICE", "stream=OTHER_CASE=PASS"), "ANDROID_CASE=PASS_REAL_DEVICE"))

    def test_failure_summary_rejects_inconsistent_zero_exit(self):
        self.assertFalse(accepted(0, GOOD.replace("Probe completed; inspect PASS/PENDING flags", "FAIL; inspect flags")))

if __name__ == "__main__":
    unittest.main()
