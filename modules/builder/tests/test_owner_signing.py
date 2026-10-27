"""Owner-signing control flow with fake tools/files; never touches a real key/APK."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class OwnerSigningTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="agy-owner-signing-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for directory in ("tools", "docs", "dist", "downloads", "bin"):
            (self.root / directory).mkdir()
        self.downloads = self.root / "downloads"
        script = (ROOT / "tools/sign_release_interactive.sh").read_text()
        self.assertEqual(script.count('downloads="/storage/emulated/0/Download"'), 1)
        # Redirect only this disposable script; production paths stay unchanged.
        script = script.replace('downloads="/storage/emulated/0/Download"',
                                "downloads=" + __import__("shlex").quote(str(self.downloads)))
        self.script = self.root / "tools/sign_release_interactive.sh"
        self.script.write_text(script)
        self.key = self.root / "owner supplied fake key.jks"
        self.key.write_bytes(b"NOT A KEY; CONTROL-FLOW FIXTURE ONLY")
        self.log = self.root / "mock-calls.jsonl"
        self.env = {**os.environ, "PATH": str(self.root / "bin") + os.pathsep + os.environ["PATH"],
                    "AGY_MOCK_LOG": str(self.log)}
        self.tool("aapt", """
import os
print("package: name='com.krmobdev.antigravity' versionCode='19' versionName='2.4.5-kr1'"
      if not os.environ.get("AGY_WRONG_PACKAGE") else "package: name='com.agy.dev' versionCode='16'")
""")
        self.tool("apksigner", """
import json,os,sys
from pathlib import Path
with open(os.environ["AGY_MOCK_LOG"],"a") as f:f.write(json.dumps(sys.argv[1:])+"\\n")
if sys.argv[1]=="sign":
 Path(sys.argv[sys.argv.index("--out")+1]).write_bytes(b"FAKE SIGNED OUTPUT")
else:
 print("Verified using v2 scheme (APK Signature Scheme v2): true")
 print("Signer #1 certificate SHA-256 digest: "+("0"*64 if os.environ.get("AGY_WRONG_CERT") else
       "23adac86423f7298b0a8ccd48758e6e19be7654d4fbdec184e184b7e16030d95"))
""")

    def tool(self, name, source):
        p = self.root / "bin" / name
        p.write_text("#!" + sys.executable + "\n" + source)
        p.chmod(0o700)

    def prepare(self, edition="standard", fallback=False):
        name = "antigravity-v2.4.5-kr1" + ("-region-bypass" if edition == "bypass" else "")
        public = "Antigravity-Android-v2.4.5-kr1" + ("-region-bypass" if edition == "bypass" else "")
        self.input = ((self.root / "dist" / (name + "-unsigned.apk")) if fallback else
                      self.downloads / (public + "-unsigned.apk"))
        self.input.write_bytes(b"FAKE UNSIGNED INPUT")
        self.output = self.downloads / (public + ".apk")
        digest = hashlib.sha256(self.input.read_bytes()).hexdigest()
        (self.root / "docs/V2_4_5_KR1_SHA256SUMS.txt").write_text(
            digest + "  " + name + "-unsigned.apk\n")

    def run_helper(self, edition="standard"):
        return subprocess.run(["bash", str(self.script), edition, str(self.key)],
                              env=self.env, capture_output=True, text=True, timeout=20)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_explicit_key_path_and_both_editions_and_fallback(self):
        for edition, fallback in (("standard", False), ("bypass", True)):
            with self.subTest(edition=edition, fallback=fallback):
                self.prepare(edition, fallback)
                result = self.run_helper(edition)
                self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
                sign = self.calls()[-2]
                self.assertEqual(sign[sign.index("--ks") + 1], str(self.key))
                self.assertEqual(sign[-1], str(self.input))
                self.assertNotIn("--ks-pass", sign)
                self.assertNotIn("--key-pass", sign)
                self.assertEqual(self.output.read_bytes(), b"FAKE SIGNED OUTPUT")
                self.assertIn("SIGNED AND VERIFIED", result.stdout)

    def test_checksum_mismatch_never_invokes_signer(self):
        self.prepare()
        self.input.write_bytes(b"STALE INPUT")
        result = self.run_helper()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("checksum mismatch", result.stderr)
        self.assertEqual(self.calls(), [])
        self.assertFalse(self.output.exists())

    def test_existing_signed_output_is_preserved(self):
        self.prepare()
        self.output.write_bytes(b"OWNER OUTPUT")
        result = self.run_helper()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.calls(), [])
        self.assertEqual(self.output.read_bytes(), b"OWNER OUTPUT")

    def test_wrong_package_never_invokes_signer(self):
        self.prepare()
        self.env["AGY_WRONG_PACKAGE"] = "1"
        result = self.run_helper()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unexpected unsigned input package", result.stderr)
        self.assertEqual(self.calls(), [])

    def test_wrong_certificate_never_reports_success(self):
        self.prepare()
        self.env["AGY_WRONG_CERT"] = "1"
        result = self.run_helper()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unexpected signing certificate", result.stderr)
        self.assertNotIn("SIGNED AND VERIFIED", result.stdout)


if __name__ == "__main__":
    unittest.main()
