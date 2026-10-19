# Agent task notifications — native Android implementation (WIP)

## User goal

Long-running Antigravity tasks (20–60 minutes) should be visible from the
notification shade while the user uses another app or locks the screen:

- Quiet ongoing notification: task still running, elapsed time and latest
  available tool/step summary.
- High-importance completion notification: distinct alert/vibration if allowed
  by Android notification channel settings, tap to foreground Antigravity.
- No duplicate completed notifications, no premature "finished" from an old
  idle conversation or transient partial idle state.
- Several simultaneous conversations should not overwrite each other.
- No Shizuku, SSH, foreground refresh or extra network permissions required.

## Current stage: native notification core + decoded WebView stream

- `AgentTaskTracker.java` is an Android-independent transition filter for
  `CascadeRunStatus`: 2 RUNNING / 4 BUSY / 1 IDLE with `fullyIdle == true`.
- `AgentNotifications.java` uses `NotificationManager` directly. Working
  updates are quiet, chronometer-based and ongoing; completion notifications
  use a separate user-configurable high-importance channel.
- Android's pending intent returns to `MainActivity`. The conversation ID
  is included for future navigation support; automatic selection of that
  conversation is **not yet implemented**.
- `web/src/patches/web_optimizations/agent-notifications.js` extracts a
  sanitized stage label from decoded agent state (not DOM parsing).
- `web/patch_agent_notifications.py` adds a small, version-checked callback
  to a **staged copy** of `web/main.js`. The upstream bundle remains unchanged.
- `AndroidBridge.onAgentState` receives transitions on the WebView bridge
  thread and feeds the native notification manager.

## Important current limitation: background completion

Android-native notifications persist while the app is backgrounded, but
**the event source is still the WebView JavaScript stream**. If Android
suspends the JS thread entirely, the notification may continue to display
"working" until the WebView wakes and reconnects. The feature is **not
yet ready** for users who require an immediate background completion alert.

Before shipping / submitting a PR:

1. Build a background task observer in `CoreServerService` that directly
   subscribes to the native `language_server`'s conversation summaries or
   checks status over its authenticated loopback interface. Reuse the service
   lifecycle, protect credentials, and use bounded/reconnecting I/O. Do not
   disable TLS validation or poll the UI DOM.
2. Handle completion, errors, interrupted tasks, waiting-for-user-action,
   and multiple concurrent conversations from the backend. Avoid phantom
   completions after reconnect/restart.
3. Implement reliable opening of the targeted conversation (instead of only
   foregrounding the app).
4. Verify Android 13+ notification permission and manufacturer/DND behavior;
   heads-up alerts are subject to user notification settings.
5. Verify integration with the independent WebView resume fix PR #15 and
   both standard and bypass build variants.
6. Build and test a dev APK on the actual device for 30–60 min background,
   lock/unlock, mobile network interruption and core restarts.

## Automated checks

```sh
javac -d build/notify-check app/src/main/java/com/agy/bridge/AgentTaskTracker.java app/tests/AgentTaskTrackerTest.java
java -cp build/notify-check AgentTaskTrackerTest
python3 -m unittest discover -s web/tests -p 'test_*.py' -v
bash web/tests/test_staged_notification.sh
```

**Do not push this feature as a production fix or merge to `main` until
backend-independent completion is verified.** Current implementation is an
isolated work-in-progress branch, not installed into the user's Antigravity.
