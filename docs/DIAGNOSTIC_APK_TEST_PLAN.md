# Diagnostic Android APK — WebView resume, sidebar, notifications

**Status:** test-only build in `test/instrumented-webview-notifications`. Never ship as production.

## Contents

- App package `com.agy.dev`, port `45158`; standard `com.agy` remains intact.
- `--region-bypass` applies the same region gate patch to the separate dev core.
- WebView resume patch (from PR #15) and native agent notification prototype.
- Notification deep link: a per-conversation immutable PendingIntent launches
  the client router at `/c/$cascadeId`, even after a cold start, subject to
  device test confirmation. Cached conversation titles appear in notification
  headers when available.
- Explicit sidebar summary instrumentation, without hiding the spinner or changing backend state.
- Bounded Android logcat `AGY_DIAG` events and private rolling files
  `/data/user/0/com.agy.dev/files/logs/agy-diag.log`,
  `agy-diag.prev.log`.
- No chat text, prompts, command arguments, tokens or full conversation IDs.

## Observation map

| Event | Meaning |
|---|---|
| `activity onCreate/onResume/onPause/onStop/onDestroy` | Android lifecycle |
| `host_resume_dispatch_attempt` | Java scheduled the JS resume event |
| `host_resume received=1` | JS actually handled the event |
| `summary <suffix> status,idle_flag,waiting=X,Y,Z` | Backend summary arriving |
| `sidebar <suffix> selected,summary,agent,starting=X,Y,Z,W` | Flags controlling the spinner |
| `agent_state id=<suffix> status=N idle=true/false` | Open-chat agent stream state |
| `notification type=STARTED/PROGRESS/FINISHED` | Native notification dispatched |
| `navigation requested/dispatch/accepted` | PendingIntent tapped and native/JS router handshake |

If `summary` stops after resuming but `agent_state` updates, the problem is
likely the separate summary subscription. If summary reports idle but sidebar
still loading, inspect local optimistic/start flags and active agent provider.
If native notification says FINISHED but there is no heads-up, inspect Android
notification permission/channel/DND settings. If WebView JS freezes, a native
background observer is still necessary for fully reliable completion alerts.

## Tests on the user's phone

1. **Install DEV APK alongside the official app** (do not uninstall Bypass).
   It has a separate app data folder: sign in if requested.
2. Grant notifications for `Antigravity Dev`. Enable alerts on its "Agent completed"
   channel and choose preferred sound/vibration. DND and OEM settings may still
   suppress floating banners.
3. Run a safe 5–10 minute agent task. Check one quiet ongoing notification.
4. While running, go to another app, return once, and capture whether progress updates.
5. Repeat with screen lock/unlock, then let the task complete while another app is foreground.
6. Open the sidebar, confirm the spinner cleared. Exit and reopen the Dev app
   without force-stop; photograph any stale spinner and note the time.
7. Tap a **working** notification: must open its own chat, not home screen.
8. Tap a **completed** notification after going elsewhere: must open that specific
   finished chat. Test two conversations (distinct PendingIntents), then repeat
   after Android has killed the activity and after a regular app restart.
9. **Do not force-stop until logs are captured.**

## Collect diagnostics

If Shizuku is running, from the Android shell:

```sh
rish -c 'run-as com.agy.dev cat /data/user/0/com.agy.dev/files/logs/agy-diag.log'
rish -c 'logcat -d -v threadtime -s AGY_DIAG:I AGY_JS:I WebViewHost:I MainActivity:I'
```

Alternatively the Antigravity Dev agent's **own shell** can read its private log:

```sh
cat /data/user/0/com.agy.dev/files/logs/agy-diag.log
```

If the dev app cannot run normally, inspect `adb logcat` via connected ADB
or Shizuku and share only the AGY_DIAG / crash sections. Do not share
full unfiltered logcat: it may include secrets from other applications.

## Known limitations

The first notification prototype receives agent status from WebView JS.
Therefore it **cannot guarantee** prompt completion while WebView is suspended.
This is a diagnostic experiment to distinguish notification delivery from
source starvation. A service-side core observer and verified multi-chat deep linking
are required before a production notifications PR. No files in the real
`com.agy` package are changed by building or installing `com.agy.dev`.
