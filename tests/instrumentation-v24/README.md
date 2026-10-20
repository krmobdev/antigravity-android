# v2.4 dev UI readiness instrumentation

Build on Android/Termux: `bash tests/instrumentation-v24/build.sh`. Install the resulting separate test APK (package `com.agy.v24readiness`) and run `am instrument -w -r com.agy.v24readiness/.ReadinessProbe` through approved Shizuku instrumentation support. Target is **only** `com.agy.dev`.

The previous installed `com.agy.notificationtests/.NotificationInstrumentation` stopped with `Dev sidebar not ready` after checking the package/notification permission. A second previous diagnostic instrumentation expected legacy `web/main.js` and failed with FileNotFoundException; v2.4 stages `assets/web/generated/main.js`. Old instrumentation source was not available in the workspace, so do not assume a product sidebar failure from these old test errors.

This replacement uses the actual v2.4 launcher (`LauncherDarkActivity` / `LauncherLightActivity`), traverses superclass fields from `MainActivity`, waits for `CoreRuntimeState.serverUrl`, checks `webUiReady` and `appLoaded`, then checks presence of React root, sidebar DOM structure and token-gated bridge **without reading or emitting account tokens, conversation names, messages or user data**. OAuth inspection is a boolean via the existing `OAuthTokenStore.hasToken`.

Actual Android/Shizuku result on 2026-10-20: `CORE_READY=true`, `ON_UI_READY=true`, `WEB_APP_LOADED=true`, `SIDEBAR_CANDIDATE=true`, `BRIDGE_TOKEN_INJECTED=true`, `UI_READINESS=PASS`, `summary=uiReadiness=PASS`. This establishes dev UI readiness, **not** successful completion notification for a live Core agent task. Those remain separate acceptance tests.

An optional separate `LiveCoreSmoke` instrumentation performs **one harmless real Core request only**: it refuses to send unless exactly one visible "New Conversation" button is available, confirms the new composer is not under an existing chat route, and attempts a short no-tools response. It reads only counts of native observer events and its own conversation terminal flag, never exports conversation IDs, auth tokens or response text. A click is *not* proof of Core work; `liveCore=PASS` requires a native observer terminal event. If a button is ambiguous or the UI workflow differs, the test stops without sending. Default safe dry-run: `am instrument -w -r com.agy.v24readiness/.LiveCoreSmoke`. This launches the v2.4 dev UI and opens a fresh conversation, **without sending any model request**.
Explicit one-time real execution (only after deciding to spend quota): `am instrument -w -r -e execute yes com.agy.v24readiness/.LiveCoreSmoke`. The test types one minimal prompt, finds exactly one enabled Send control and calls its actual React button `.click()` once; if the route does not change, it does not re-send. The model submission branch is not claimed verified until an authorized single live execution proves Core completion.


### Actual live Core smoke attempt and limits

Using Shizuku instrumented dev app on 2026-10-20:
- Version-aware startup, `onUiReady`, dedicated New Conversation click, fresh route and composer all succeeded.
- First smoke stopped **before any model request** because a scripted synthetic `input` event was not recognized as a sendable message.
- Second smoke used native Android keystrokes; the send control became enabled and Enter was issued, but a new `/c/<id>` route was not observed. This **does not prove** the model received a request; do not retry blindly.
- Separate `SafeEvidence` instrument observed, over a ten-minute window, `NATIVE_WATCH_ADDED=0`, `NATIVE_CORE_STATES=0`, `NATIVE_CORE_COMPLETIONS=0`, `UI_AGENT_STATE_REPORTS=0`, `CURRENT_NOTIFICATION_COUNT=0`. It inspects only counts and never prints IDs or chat content.
- **Real completion notification is NOT VERIFIED.** New UI route/agent launch must be performed manually on the dev app or via a future verified app-specific automation; no user secrets are moved from `com.agy`.

`SafeEvidence` can be re-run through `am instrument -w -r com.agy.v24readiness/.SafeEvidence` after the user executes a real task. A passing `ReadinessProbe` is not a substitute for live Core task completion proof.


### Native evidence: first successful real task, 2026-10-20

User-provided visual evidence (separate ChatGPT conversation): a real Core v2.4 task finished, the expected short answer appeared, and Android displayed a completion notification at 20:33:10. The screenshots were **not** retrieved through AnimaBridge.

The `HistoricalCompletionEvidence` test read only the private device log for 20:31:00–20:35:59 local, mapped the conversation identifier to an ephemeral `dialog_1`, and emitted neither IDs nor text:
- 20:33:07.274 native observer WATCH; 20:33:07.349 Core state RUNNING.
- 20:33:10.711 Core IDLE/fullyIdle; 20:33:10.712 notification tracker FINISHED source=native.
- 20:33:10.714 native observer FINISHED; 20:33:10.785 NotificationManager.notify() returned without exception (notification_posted recorded).
- Exactly one finished tracker transition and one posted completion in that window, no native stream errors.
- Completion Android channel `agy_agent_finished_in_chat_v1` (open chat, no heads-up); FIFO pending=0.
- A separate progress notification remained active, but Android notification tags prove it is **not** the completed conversation.
- No `navigation accepted` event: deep-link tap was not verified. Activity paused at 20:33:23, *after* completion, so independent native observation while WebView is stopped or device locked is still unproven.
- Other open-chat/sidebar and multi-agent Hca/NDa real-world acceptance scenarios remain open.

This is a **positive real Core completion/notification result**, not complete user acceptance. Never post real conversation IDs, OAuth tokens or chat content to public issue logs.

Run `am instrument -w -r com.agy.v24readiness/.HistoricalCompletionEvidence` on the Android device to re-evaluate the exact historical event window without starting any model.

Historical observer window may be set with Android instrumentation extras:
`am instrument -w -r -e start 21:15 -e end 21:20 com.agy.v24readiness/.HistoricalCompletionEvidence`
(default is 21:15–21:20 local on 2026-10-20). Never returns raw IDs or message text.
