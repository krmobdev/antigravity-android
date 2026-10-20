# AGY Dev — real-device sidebar instrumentation (2026-10-20)

Target: `com.agy.dev`, test package `com.agy.diagtests`.
Independent APK; no app code, backend state, prompts, or settings are modified.

## Test coverage

- Launch the **real Android Activity and WebView**, verify diagnostic page ID.
- Read actual rendered sidebar relative timestamps / loading spinners / unread dots from DOM.
- Wait **70 seconds** without interaction, read the *same WebView page*, and fail if a minute-scale timestamp does not progress.
- Move the Android task to background and foreground; confirm timestamps and diagnostics.
- Click an existing conversation row through React's real click handler, use Android Back and compare timestamp before and after. This step is explicitly *inconclusive* if no safe clickable row can be identified.
- Preserve a textual report in target app private files: `files/logs/sidebar-instrumentation.txt`. Capture a failure screenshot if available.

## Build and run (Termux)

```sh
bash tests/instrumentation/build.sh
# Install tests/instrumentation/out/agy-sidebar-tests.apk via authorized Shizuku.
# Invoke instrumentation with:
am instrument -w -r com.agy.diagtests/com.agy.diagtests.SidebarInstrumentation
```

This test proves only the observed timestamp refresh behavior. It cannot independently reproduce an actual agent `RUNNING → IDLE` race because there is not yet a deterministic controlled summary-stream fixture, so it must not be reported as a spinner regression test. A future gate must explicitly control agent state, summary order, and React commits, checking loading/unread DOM elements without real agent tasks.
