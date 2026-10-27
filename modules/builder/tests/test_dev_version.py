"""APK identity must describe the current KR candidate in both Dev and release."""
from pathlib import Path
import os,re,subprocess,sys,tempfile,unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[3]
ANDROID='{http://schemas.android.com/apk/res/android}'

class DevVersionMetadata(unittest.TestCase):
    def generate(self,dev=True,bypass=False,no_git=False):
        with tempfile.TemporaryDirectory() as folder:
            env=dict(os.environ,DEV_MODE='1' if dev else '0',
                     KR_RELEASE_MODE='0' if dev else '1',NO_BYPASS='0' if bypass else '1')
            if no_git:env['PATH']=folder
            result=subprocess.run([sys.executable,str(ROOT/'modules/builder/01_gen_config.py'),folder],
                cwd=ROOT,env=env,text=True,capture_output=True)
            self.assertEqual(result.returncode,0,result.stderr)
            manifest=ET.parse(Path(folder)/'AndroidManifest.xml').getroot()
            java=(Path(folder)/'gen/com/agy/BuildConfig.java').read_text()
            return manifest,java

    def test_dev_reports_candidate_and_real_source_revision(self):
        manifest,java=self.generate()
        version=manifest.attrib[ANDROID+'versionName']
        self.assertRegex(version,r'^2\.4\.5-kr1-dev\+[0-9a-f]{12}(?:-dirty)?$')
        revision=subprocess.check_output(['git','rev-parse','--short=12','HEAD'],cwd=ROOT,text=True).strip()
        self.assertIn('+'+revision,version)
        self.assertEqual(manifest.attrib[ANDROID+'versionCode'],'19')
        self.assertEqual(manifest.attrib['package'],'com.agy.dev')
        self.assertEqual(manifest.find('application').attrib[ANDROID+'debuggable'],'true')
        self.assertIn('APP_VERSION_NAME = "'+version+'"',java)
        self.assertIn('APP_VERSION_CODE = 19',java)

    def test_archive_dev_discloses_unknown_revision(self):
        manifest,_=self.generate(no_git=True)
        self.assertEqual(manifest.attrib[ANDROID+'versionName'],'2.4.5-kr1-dev+unknown')
        self.assertEqual(manifest.attrib[ANDROID+'versionCode'],'19')

    def test_both_kr_editions_keep_plain_release_version(self):
        for bypass in (False,True):
            with self.subTest(bypass=bypass):
                manifest,java=self.generate(dev=False,bypass=bypass)
                self.assertEqual(manifest.attrib[ANDROID+'versionName'],'2.4.5-kr1')
                self.assertEqual(manifest.attrib[ANDROID+'versionCode'],'19')
                self.assertEqual(manifest.attrib['package'],'com.krmobdev.antigravity')
                self.assertEqual(manifest.find('application').attrib[ANDROID+'debuggable'],'false')
                self.assertNotIn('-dev+',java)

if __name__=='__main__':unittest.main()
