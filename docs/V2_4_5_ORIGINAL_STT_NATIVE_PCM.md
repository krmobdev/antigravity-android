# v2.4.5 Android: original speech-to-text transport

## Owner-reported defect
On Realme, Android WebView's getUserMedia fails with UnknownError during the original Antigravity voice input, even though RECORD_AUDIO is allowed and native Android AAC recording works.

## Original upstream contract preserved
The original pinned web/main.js selects streaming speech-to-text if its Core streamAudioTranscription/sendAudioChunk/endAudioSession service and streamingAudioFeature flag are enabled. It sends signed little-endian mono 16-bit PCM at 16000 Hz, receives interim/final transcripts and edits the Lexical Composer. When this service is unavailable, the unchanged original fallback is an audio attachment.

## Targeted Android compatibility
- NativePcmCapture uses Android AudioRecord (VOICE_RECOGNITION, mono 16000 Hz, PCM16), with frame queue, permission/focus checks, bounded buffers and four-minute hard cap.
- AndroidBridge exposes token-guarded startNativePcm/readNativePcm/stopNativePcm and read-only nativePcmReadiness; stops on Activity background through existing cancellation.
- The streaming dO hook chooses native PCM transport on Android; it does **not** change original Core transcription API, its RPC payload or Lexical transcript rendering.
- On browsers without the native bridge the original WebAudio microphone/worklet path is preserved.
- Only user pressing the existing mic button may start capture. No automated live audio recording was done during QA.

## Verification 2026-10-26 on Realme
- Web patch selftest PASS; Python tests PASS including 3 native PCM transport tests; Android Java compiled in local dev APK.
- Debug APK: dist/antigravity-v2.4.5-original-stt-native-pcm-dev.apk
- Android Download: /storage/emulated/0/Download/antigravity-v2.4.5-original-stt-native-pcm-dev.apk
- SHA256: ee09765322f2cd18ba38efc43133e98426986db56d7728d493509545affe4c94
- Package com.agy.dev; debug signing; internal versionName 2.4.0 (not release-ready).
- Shizuku install: Success, production package untouched.
- Native PCM capability test: VOICE_NATIVE_PCM_READY=PASS, readiness READY.
- Mic button routing test with **both microphone paths stubbed**: VOICE_STT_NATIVE_PCM_CALLS=1, VOICE_STT_WEB_CAPTURE_CALLS=0, VOICE_STT_ATTACHMENT_CALLS=0, VOICE_STT_NATIVE_ROUTING=PASS.
- Actual spoken-word transcription with Core service has NOT been tested yet. Must be tested by owner and Core backend compatibility confirmed before release.

## Next actions
1. Owner opens Antigravity Dev, speaks a short test phrase, stops recording; verify text appears in Composer (not a media attachment) and can be edited, no auto-send.
2. If failure, capture sanitized Core RPC errors and app logs; never log private audio, transcript or tokens.
3. Test Pixel 7 (Android 17) and earlier Android emulator before merging/releasing. Do not change main, tags, or owner signing keys.
