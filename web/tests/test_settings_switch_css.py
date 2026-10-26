"""Regression: settings switch tracks must not inherit mobile 48px action sizing."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]

class MobileSettingsSwitchSizeTest(unittest.TestCase):
    def test_only_action_buttons_gain_mobile_touch_minimum(self):
        css=(ROOT/'web/mobile-composer.css').read_text()
        rule=css.split("/* Mobile settings: keep the stock 44×24px role=switch track.",1)[1].split("[data-testid=bottom-sheet] [data-testid=model-selector-panel]",1)[0]
        self.assertIn('button:not([role="switch"])',rule)
        self.assertNotIn('[data-testid=bottom-sheet] button{min-width:48px}',css)
        self.assertNotIn('[data-testid=bottom-sheet] button,[data-testid=bottom-sheet]',css)
        self.assertIn('min-height:48px',rule)
        self.assertIn('min-width:48px',rule)
        # The upstream toggle component owns its own track dimensions.
        upstream=(ROOT/'web/main.js').read_text()
        self.assertIn('f?"h-4 w-7":"h-6 w-11"',upstream)

if __name__=="__main__":
    unittest.main()
