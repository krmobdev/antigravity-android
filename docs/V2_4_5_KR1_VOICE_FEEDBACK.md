# Feedback pass for v2.4.5-kr1 (26 Oct 2026)

Base: review/v2.4.5-handoff-20261026, commit 67d5ec5.
Source changes: GitHub commit 4f72ca2. The same source changes were committed locally in the separate Realme worktree as 8e94fc8. These SHAs differ because the remote branch was created via GitHub connector after HTTPS push from Termux failed. Do not cherry-pick both versions of the same patch.

## Owner feedback addressed
- Microphone is a real feature, **do not hide**: legacy MediaRecorder audio attachment and optional live streaming transcription are in the underlying client.
- Fixed silent failures: recorder error was cleared by cleanup and hidden by the outer composer header when not recording.
- Display pending microphone permission/startup, suppress repeated taps while connecting, persist error UI, preserve audio duration before cleanup; show specific errors for empty capture, unavailable API, and recorder failure.
- Theming: send button always circular; muted when disabled, accent when send is available.
- Attachments: compact, horizontally scrollable ChatGPT-like rail with image thumbnails, small document cards and separate remove control.
- Agent progress notifications: silent low-importance channel without sound or vibration; final completion remains separate, subject to Android channel/DND settings.
- System bars: no extra contrast scrim where supported.

## Tests run
- `bash web/tests/selftest.sh`: PASS
- `python -m unittest discover -s web/tests -p 'test_*.py'`: PASS (24 tests)
- `bash app/tests/run.sh`: PASS
- Instrumentation compiled. Debug APK built and signature verified: `dist/antigravity-v2.4.5-voice-fixed-dev.apk` under the Realme review worktree, package `com.agy.dev`, installed with Shizuku. It is not a signed release.

## Open checks — do not claim release ready
- Final on-device instrumentation failed `ANDROID_WEBVIEW_PRESENT=FAIL` before audio-capability probes could execute. Earlier iterations passed basic Composer, attachment, stage notification tests, but failed model sheet Back/reopening due to Activity focus loss. Need diagnose both.
- Real mic capture and transcribed text/audio attachment end-to-end were NOT exercised automatically; no ambient microphone audio was collected.
- Both final unsigned APKs need a clean rebuild from an accepted commit, then owner signing and update testing.
- Never overwrite the original agent checkout, which contains unrelated uncommitted `web/generated/main.js`. Do not touch `main` or Android production signing key.
