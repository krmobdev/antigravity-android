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
- This code was copied from the separate Realme worktree, but **AnimaBridge command execution began returning 403 Forbidden before we could run build/tests**. Do NOT call this branch tested or release ready.

## Next tasks
1. When command access resumes: `bash web/tests/selftest.sh`, `python -m unittest discover -s web/tests -p 'test_*.py'`, then full Gradle-free dev APK build. Fix any new patch anchor or bundle syntax failures before installing.
2. Verify `com.agy.dev` package and install **only dev APK**, never production package.
3. With owner consent, ask for ONE manual microphone tap and note the specific stage and safe error class. Avoid automated ambient recording.
4. If hardware acquisition is denied, investigate Android WebChromeClient `onPermissionRequest` callback and permission request lifecycle, on UI thread; never grant non-audio resources or origins.
5. Once live voice has passed, merge selectively. Leave `main`, signing keys and releases untouched.
