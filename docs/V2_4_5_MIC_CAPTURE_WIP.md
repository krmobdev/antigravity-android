# Antigravity v2.4.5 — voice input diagnosis (WIP, 2026-10-26)

## Root-cause research
- In upstream `werdio325-png/antigravity-android`, `AgyWebChromeClient.java` at main does NOT implement `onPermissionRequest`. Android WebView's default behavior denies web-content microphone requests. Therefore voice input is not correctly supported by the *original Android wrapper*, irrespective of the browser's built-in voice button.
- Our fork already has a dedicated `WebAudioPermission` and `onPermissionRequest`, restricted to the exact local HTTPS Core origin and `AUDIO_CAPTURE` resource.
- Realme GT 2 Pro Android 15: Android app permission RECORD_AUDIO granted and AppOps=foreground, WebView secure context, getUserMedia and MediaRecorder APIs available, supports WebM/Opus and MP4, trusted Core origin passes, synthetic *non-microphone* MediaRecorder test PASS.
- Actual mic tap still fails: `UnknownError`. The previous code did not track whether failure occurred during hardware acquisition, recorder construction or recorder.start.

## This branch (UNVERIFIED)
- New safe stage-specific error reporting for `getUserMedia`, `MediaRecorder` construction, `start`, recording event.
- Prefer an explicitly WebView-supported `audio/webm;codecs=opus` MIME type, retain fallback.
- Do not print raw browser exception messages; may contain private content.
- The exact source on this WIP branch was validated, built and installed locally after agent restart. Live microphone recording is NOT yet proven; do not treat this as release-ready.

## Validated on Realme after AnimaBridge restart (26 Oct 2026)
- Agent 3.5.0 connected; local Termux job execution restored.
- `bash web/tests/selftest.sh`: PASS.
- `python -m unittest discover -s web/tests -p 'test_*.py'`: PASS (25 tests).
- `bash app/tests/run.sh`: PASS.
- `git diff --check`: PASS.
- `bash modules/builder/build_apk.sh --dev --out dist/antigravity-v2.4.5-audio-capture-dev.apk`: PASS; APK signature verified.
- SHA-256: `46c1c2ebadc595b040fc7c1a2dd8170ca2c746e0aaf998011c4c3126a59a90f5`.
- Size: 103,506,336 bytes. Package: `com.agy.dev`, debug signing, internally `versionName=2.4.0` (release metadata still pending).
- Copied to `/storage/emulated/0/Download/antigravity-v2.4.5-audio-capture-dev.apk` on Realme; copy's SHA-256 matches.
- Installed via Shizuku `pm install -r`: Success, production `com.krmobdev.antigravity` not replaced.
- **Live user-initiated microphone recording has not yet been confirmed.** Ask owner to tap mic in new dev APK and provide exact phase/error text.

## Next tasks
1. When command access resumes: `bash web/tests/selftest.sh`, `python -m unittest discover -s web/tests -p 'test_*.py'`, then full Gradle-free dev APK build. Fix any new patch anchor or bundle syntax failures before installing.
2. Verify `com.agy.dev` package and install **only dev APK**, never production package.
3. With owner consent, ask for ONE manual microphone tap and note the specific stage and safe error class. Avoid automated ambient recording.
4. If hardware acquisition is denied, investigate Android WebChromeClient `onPermissionRequest` callback and permission request lifecycle, on UI thread; never grant non-audio resources or origins.
5. Once live voice has passed, merge selectively. Leave `main`, signing keys and releases untouched.
