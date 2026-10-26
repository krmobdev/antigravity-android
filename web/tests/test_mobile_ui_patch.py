import sys
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import patch_mobile_attachments as attachments
import patch_mobile_composer as composer

class MobilePatchTest(unittest.TestCase):
    def test_pinned_integration_and_send_logic_preserved(self):
        original=(ROOT/"main.js").read_text()
        result=composer.transform(attachments.transform(original))
        self.assertEqual(result,composer.transform(result))
        self.assertEqual(result,attachments.transform(result))
        def send_region(source):
            return source[source.index("var DYb="):source.index("function OYb",source.index("var DYb="))]
        self.assertEqual(send_region(original).replace('onClick:D,disabled:H,className:aj(t===', 'onClick:D,disabled:H||t==="requesting",className:aj(t==='),send_region(result))
        self.assertIn('Подключение микрофона',result)
        self.assertIn('starting:agyPending',result)
        self.assertIn('F=agySeconds',result)
        self.assertIn('modelSelector:q,environmentSelector:r,currentModelConfig:x',result)
        self.assertIn('handleMediaPaste:pj',result)
        self.assertIn('Math.min(5,WN("max-media-attachments",5))',result)
    def test_changed_composer_fails_closed(self):
        source=(ROOT/"main.js").read_text().replace('var zYb=({items:a,','var changed=({items:a,',1)
        with self.assertRaises(ValueError):composer.transform(source)
    def test_changed_attachment_fails_closed(self):
        source=(ROOT/"main.js").read_text().replace('var uYb=({selectedMedia:','var changed=({selectedMedia:',1)
        with self.assertRaises(ValueError):attachments.transform(source)
if __name__=="__main__":unittest.main()
