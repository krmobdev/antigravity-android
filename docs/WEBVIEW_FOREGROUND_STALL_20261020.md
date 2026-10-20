# Original v2.4.0 foreground stale-state reproduction (2026-10-20)

Observed on Android with the untouched upstream antigravity.apk v2.4.0.

- 16:10:58 local: Gemini 3.8 Flash High started a read-only audit of the local quizapp repository via SSH.
- SSH failed initially with a known_hosts permission error; the agent continued trying to repair SSH configuration.
- 16:13:49, 16:16:24, 16:18:24, 16:20:12: same visible "Configure SSH config" and "Working"; no new rendered actions.
- A short screen lock/unlock did not fix the stale UI.
- 16:29:29: same screen after ~19 minutes.
- 16:29:34: pressing Stop returned: "Failed to stop agent — Executor is not currently running", while the UI still showed "Working".
- 16:29:57: returning to conversation list showed an endless loading spinner for the same conversation.

Inference: UI/executor state divergence reproduced. This does not independently establish the underlying transport failure or confirm the proposed patch resolves it.

Initial fork mitigation:
- Renew only StreamAgentStateUpdates observation on agy:host-resume, without cancelling the agent lifetime.
- While foregrounded, renew the observation if there have been no incoming events for >90 seconds (30-second checks).
- Disconnect the stream's timers and listeners upon cleanup.
- Preserve upstream web/main.js byte-for-byte; apply the patch only to web/generated/main.js as part of web/build.sh.
- Detect minifier-layout mismatch and fail the build, rather than silently shipping an unpatched APK.

Regression: python3 -m unittest discover -s web/tests -p test_stream_resume_patch.py -v

Still required before declaring stable:
- Build and sign a v2.4 APK;
- Reproduce the long foreground chat audit on device;
- Confirm that the executor completion reconciles the UI and sidebar without relaunch;
- Port native completion notifications separately, preserving v2.4's restricted JavaScript bridge;
- Verify native alerts while backgrounded/locked and after multiple completions.
