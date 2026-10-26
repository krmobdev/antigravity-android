"""Regression contracts for the v2.4.5 mobile acceptance adjustments."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class MobileAcceptanceTest(unittest.TestCase):
    def test_send_circle_and_theme_states(self):
        css = (ROOT / "web/mobile-composer.css").read_text()
        self.assertIn('button[data-testid="send-button"]:not(:disabled)', css)
        self.assertIn('button[data-testid="send-button"]:disabled', css)
        self.assertIn('border-radius:9999px!important', css)
        self.assertIn('var(--color-primary', css)
        self.assertIn('var(--color-muted', css)

    def test_voice_button_visible_and_errors_not_suppressed(self):
        css = (ROOT / "web/mobile-composer.css").read_text()
        self.assertNotIn('button[data-tooltip-id="input-send-button-record-tooltip"]{display:none!important}', css)
        patcher = (ROOT / 'web/patch_mobile_composer.py').read_text()
        self.assertIn('agyPending', patcher)
        self.assertIn('!!oe.error||Ya.length>0', patcher)
        self.assertIn('@media (max-width:720px),(pointer:coarse)', css)

    def test_compact_attachments_are_scrollable_and_safe(self):
        css = (ROOT / "web/mobile-attachments.css").read_text()
        js = (ROOT / "web/src/mobile/attachments.js").read_text()
        self.assertIn('overflow-x:auto', css)
        self.assertIn('agy-attachment-image', css)
        self.assertIn('agy-attachment-remove', css)
        self.assertIn('onClick:e=>{e.stopPropagation();onRemove();}', js)
        self.assertIn('agyAttachmentSizeLabel', js)
        self.assertIn('TextDecoder("utf-8",{fatal:true})', js)

    def test_progress_is_silent_but_finish_keeps_user_channels(self):
        java = (ROOT / "app/src/main/java/com/agy/bridge/AgentNotifications.java").read_text()
        self.assertIn('agy_agent_working_silent_v2', java)
        self.assertIn('progress.setSound(null, null)', java)
        self.assertIn('progress.enableVibration(false)', java)
        self.assertIn('getMethod("setSilent", boolean.class)', java)
        self.assertIn('b.setOnlyAlertOnce', java) if False else None
        self.assertIn('.setOnlyAlertOnce(true)', java)
        self.assertIn('CHANNEL_FINISHED', java)
        self.assertIn('CHANNEL_IN_CHAT', java)


if __name__ == "__main__":
    unittest.main()
