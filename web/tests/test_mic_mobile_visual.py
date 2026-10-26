"""Purely visual microphone polish must not alter successful STT behavior."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]

class MicrophoneVisualTest(unittest.TestCase):
    def test_chat_style_mic_and_sober_recording_indicator(self):
        css=(ROOT/'web/mobile-composer.css').read_text()
        block=css.split('v2.4.5: compact ChatGPT-like mobile microphone controls.',1)[1]
        self.assertIn('button[data-tooltip-id="input-send-button-record-tooltip"]:not([class*="bg-red-500"])',block)
        self.assertIn('button[data-tooltip-id="input-send-button-record-tooltip"][class*="bg-red-500"]',block)
        self.assertIn('viewBox',block)
        self.assertIn('stroke-width',block)
        self.assertIn('border-radius:4px;background:#dc454b;',block)
        self.assertIn('background:var(--color-muted,var(--color-secondary))!important;',block)
        self.assertIn('background:transparent!important;',block)
        self.assertNotIn('getUserMedia',block)
        self.assertNotIn('startNativePcm(',block)

    def test_original_streaming_protocol_untouched(self):
        patch=(ROOT/'web/patch_mobile_composer.py').read_text()
        self.assertIn('agyNativePcmStart(agyPcmChunk',patch)
        self.assertIn('isStreamingTranscriptionEnabled:Yf', (ROOT/'web/main.js').read_text())
        self.assertIn('streamAudioTranscription', (ROOT/'web/main.js').read_text())

if __name__=='__main__':
    unittest.main()
