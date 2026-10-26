"""Isolated release variant regression; never invokes hosted CI or signing."""
import os, subprocess, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
class KrRelease(unittest.TestCase):
    def generate(self, dev=False, fork=False):
        with tempfile.TemporaryDirectory() as d:
            env=dict(os.environ, DEV_MODE="1" if dev else "0",KR_RELEASE_MODE="1" if fork else "0")
            r=subprocess.run(["python3",str(ROOT/"modules/builder/01_gen_config.py"),d],
                             cwd=ROOT,env=env,text=True,capture_output=True)
            if dev and fork:
                self.assertNotEqual(r.returncode,0)
                return
            self.assertEqual(r.returncode,0,r.stderr)
            return [(Path(d)/x).read_text() for x in
                ("AndroidManifest.xml","gen/com/agy/BuildConfig.java","res/values/gen.xml")]
    def test_fork_release(self):
        m,j,r=self.generate(fork=True)
        for value in ('package="com.krmobdev.antigravity"','android:debuggable="false"',
                      'android:versionName="2.4.5-kr1"','android:versionCode="19"',
                      'com.krmobdev.antigravity.permission.INTERNAL_BROADCAST',
                      'com.krmobdev.antigravity.fileprovider'):
            self.assertIn(value,m)
        for value in ('package com.agy;','APPLICATION_ID = "com.krmobdev.antigravity"',
                      'PORT = 45159','GATEWAY_PORT = 45162'):
            self.assertIn(value,j)
        self.assertGreater(int(__import__("re").search(r'android:versionCode="([0-9]+)"',m).group(1)),18)
        self.assertIn("Antigravity",r)
        self.assertNotIn("Antigravity KR",r)
    def test_old_modes_unchanged(self):
        official,j,_=self.generate()
        dev,d,_=self.generate(dev=True)
        self.assertIn('package="com.agy"',official)
        self.assertIn("PORT = 45157",j)
        self.assertNotIn('debuggable="true"',official)
        self.assertIn('package="com.agy.dev"',dev)
        self.assertIn('debuggable="true"',dev)
        self.assertIn("PORT = 45158",d)
    def test_invalid_combination(self):self.generate(dev=True,fork=True)
    def test_workflow_and_release_signing_guards(self):
        workflow=(ROOT/".github/workflows/build-and-release.yml").read_text()
        triggers=workflow.split("\non:\n",1)[1].split("\npermissions:",1)[0]
        self.assertIn("workflow_dispatch:",triggers)
        self.assertNotIn("push:",triggers)
        self.assertNotIn("tags:",triggers)
        self.assertIn("if: ${{ false }}",workflow)
        builder=(ROOT/"modules/builder/build_apk.sh").read_text()
        for k in ("AGY_RELEASE_KEYSTORE_PATH","AGY_RELEASE_KEY_ALIAS",
                  "AGY_RELEASE_STORE_PASSWORD_FILE","AGY_RELEASE_KEY_PASSWORD_FILE",
                  "--kr-release","--unsigned","UNSIGNED_KR_RELEASE_MODE","Public debug key forbidden"):
            self.assertIn(k,builder)
if __name__=="__main__":unittest.main()
