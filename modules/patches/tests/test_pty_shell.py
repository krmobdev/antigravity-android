#!/usr/bin/env python3
"""KR PTY patch: pinned string context, count, no-write failure and idempotence."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "groups" / "pty_shell"))
import pty_shell as patch

def fixture(shell=patch.TARGET):
    return bytearray(b"header" + patch.PREFIX + shell + patch.SUFFIX + b"trailer")

class TestPtyShell(unittest.TestCase):
    def test_apply_size_verify_and_idempotence(self):
        data = fixture()
        original = bytes(data)
        self.assertEqual(len(patch.TARGET), len(patch.REPLACEMENT))
        self.assertFalse(patch.apply(data, verify=True)[0])
        self.assertEqual(data, original)
        self.assertTrue(patch.apply(data, dry_run=True)[0])
        self.assertEqual(data, original)
        self.assertTrue(patch.apply(data)[0])
        self.assertEqual(len(data), len(original))
        expected = original.replace(patch.TARGET, patch.REPLACEMENT)
        self.assertEqual(data, expected)
        self.assertTrue(patch.apply(data, verify=True)[0])
        self.assertTrue(patch.apply(data)[0])
        self.assertEqual(data, expected)

    def test_incompatible_inputs_fail_without_mutation(self):
        inputs = [
            bytearray(b"no shell"), fixture() + fixture(),
            fixture() + fixture(patch.REPLACEMENT),
            fixture(patch.REPLACEMENT) + fixture(patch.REPLACEMENT),
            bytearray(b"/tmp/bin/bash"), bytearray(b"/bin/bash/evil"),
            bytearray(b"/bin/bashrc"), bytearray(b"/bin/../bin/bash"),
            bytearray(b"send-pack/bin/bash/evil--config="),
            bytearray(b"send-packx/bin/bash--config="),
            bytearray(b"send-pack/bin///sh/evil--config="),
        ]
        for data in inputs:
            for options in ({}, {"allow_missing": True}, {"verify": True}, {"dry_run": True}):
                with self.subTest(data=bytes(data), options=options):
                    original = bytes(data)
                    self.assertFalse(patch.apply(data, **options)[0])
                    self.assertEqual(data, original)

if __name__ == "__main__":
    unittest.main()
