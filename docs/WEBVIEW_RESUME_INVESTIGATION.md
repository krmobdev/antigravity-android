# Android WebView: stalled agent output after background/resume

Status: candidate fix, tested in isolation and in the web asset staging pipeline.
Device reproduction and installed-APK verification are still required.

## User-visible symptom

An agent response appears permanently active in the Antigravity Android chat
after losing focus or returning from background. Reopening the app and briefly
selecting another conversation before returning can restore updates.

## Source analysis (upstream main at 1a59f1b)

- `app/src/main/java/com/agy/ui/webview/WebViewLifecycle.java`: on Android
  `onResume()`, the host resumes WebView timers and emits `agy:host-resume`.
- Before this change, no JS code listened to that host-specific event.
- `web/main.js` contains `consumeAgentStateStream()` (minified `sba`),
  which subscribes to `StreamAgentStateUpdates` and retries after stream
  termination/errors. If background suspension leaves an existing transport
  pending without a final event, the retry loop cannot run.
- Android's `MainActivity.onPause()` also calls
  `AppLifecycle.pauseTimers()`, which calls `resumeTimers()`. This is
  unusual but may be intentional to keep the background stream alive. It
  is **not changed** by this patch.

These findings explain one plausible failure mode. They do **not** establish
that all observed foreground/renderer hangs have the same cause.

## Candidate patch

During the APK packaging step, patch only staged `assets/web/main.js`:
1. Give each chat observation stream its own AbortController.
2. On `agy:host-resume`, abort that observation stream, *not* the
   agent's overall lifetime signal or its backend task.
3. Dispose event listeners after the stream ends.
4. Immediately resubscribe with rehydration, skipping the error banner for
   the intentional resume abort.
5. Preserve ordinary stream failure handling and disposal.

`web/main.js` is not modified: synchronization with upstream stays simple.
The patcher fails explicitly if the expected upstream minified code changes.
It must be reviewed/retested after any upstream update that alters this code.

## Regression tests (Termux / Python 3 / Node)

```bash
python3 -m unittest discover -s web/tests -p 'test_*.py' -v
bash web/tests/test_staged_web_assets.sh
```

Tests check unique anchors, idempotence, fail-closed behavior on a new upstream,
syntactic validity of the complete patched bundle, a never-ending mock stream
reconnection on Android resume, and cancellation propagation on disposal.
The staging test checks patch placement and exclusion of build-only source.

## Next steps before declaring the bug fixed

- Reproduce the issue on a real device with a long-running agent reply.
- Install a parallel `--dev` APK so the official `com.agy` data stays intact.
- Background/foreground while streaming, lock/unlock and run repeated cycles.
- Check that history and backend task survive, and loading stops when the agent
  finishes; test no regressions with true network disconnects.
- If freezes happen while the app *stays foregrounded*, investigate DOM
  performance / React render and WebView renderer ANR separately.
