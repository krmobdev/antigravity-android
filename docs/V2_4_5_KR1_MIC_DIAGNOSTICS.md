# Android microphone diagnosis — Realme GT 2 Pro, 26 October 2026

### Reproduction
Owner reported: Antigravity Dev shows “Не удалось начать запись. Проверьте доступ к микрофону и доступность голосового ввода.” even with Android RECORD_AUDIO permission granted.

### Confirmed on the actual device
- Android permission: RECORD_AUDIO granted=true.
- AppOps: RECORD_AUDIO=foreground (normal).
- Inside dev WebView: `window.isSecureContext=true`.
- `navigator.mediaDevices.getUserMedia`: available.
- `MediaRecorder`: available.
- MediaRecorder supports `audio/webm;codecs=opus` and `audio/mp4`.
- WebView origin matches trusted Core server URL according to `WebAudioOrigin.allowed`.
- Probe finished `INSTRUMENTATION_CODE: 0`. It did **not** acquire mic audio or record the user.

### Root cause found for the useless error text
`agyAudioError(error)` first converted the underlying exception to a localized error string. The outer UI called `agyAudioError` a second time with that string, which previously replaced all but the permission-specific message with a generic fallback. That masked the real failure reason.

### Changes
- Make known microphone error messages idempotent.
- Preserve safe recorder exception class for unexpected DOM errors; never display arbitrary exception messages containing potentially private data.
- Add Node regression test covering `NotReadableError`, `NotAllowedError`, `NotSupportedError`, and safe unknown class.
- Add `mode=mic` instrumentation checks for WebView audio API capabilities and exact Core origin match without touching the microphone.

### Verification
- 25 Python tests PASS; WebView patch selftest PASS.
- Local dev APK `dist/antigravity-v2.4.5-mic-diagnostics-dev.apk` built and debug signature verified; installed on Realme as `com.agy.dev`, without replacing production.
- Capability instrumentation PASS.
- **Synthetic MediaRecorder end-to-end test on the actual WebView: PASS.** A generated audio oscillator was recorded and produced data. No microphone was activated and no ambient sound was collected.
- Therefore the remaining failure is more likely during physical `getUserMedia({audio:true})` acquisition, native WebChromeClient permission handling, or a real-device-specific recorder initialization. The precise point still needs a user-initiated tap with the updated diagnostic APK.

### Still open
The exact **cause of the recording failure** is not yet proven. The diagnostic APK must be tested by the owner: open Antigravity Dev and tap the microphone once, then capture the **new specific error message**. Do not claim live recording works until a user-initiated recording succeeds. No ambient mic audio was recorded during these tests.

Do not tag or sign 2.4.5 release yet; Android Back UI regression remains open. `main` and production signing key untouched.
