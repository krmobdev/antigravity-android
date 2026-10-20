# v2.4 dev UI readiness instrumentation

Build on Android/Termux: `bash tests/instrumentation-v24/build.sh`. Install the resulting separate test APK (package `com.agy.v24readiness`) and run `am instrument -w -r com.agy.v24readiness/.ReadinessProbe` through approved Shizuku instrumentation support. Target is **only** `com.agy.dev`.

The previous installed `com.agy.notificationtests/.NotificationInstrumentation` stopped with `Dev sidebar not ready` after checking the package/notification permission. A second previous diagnostic instrumentation expected legacy `web/main.js` and failed with FileNotFoundException; v2.4 stages `assets/web/generated/main.js`. Old instrumentation source was not available in the workspace, so do not assume a product sidebar failure from these old test errors.

This replacement uses the actual v2.4 launcher (`LauncherDarkActivity` / `LauncherLightActivity`), traverses superclass fields from `MainActivity`, waits for `CoreRuntimeState.serverUrl`, checks `webUiReady` and `appLoaded`, then checks presence of React root, sidebar DOM structure and token-gated bridge **without reading or emitting account tokens, conversation names, messages or user data**. OAuth inspection is a boolean via the existing `OAuthTokenStore.hasToken`.

Actual Android/Shizuku result on 2026-10-20: `CORE_READY=true`, `ON_UI_READY=true`, `WEB_APP_LOADED=true`, `SIDEBAR_CANDIDATE=true`, `BRIDGE_TOKEN_INJECTED=true`, `UI_READINESS=PASS`, `summary=uiReadiness=PASS`. This establishes dev UI readiness, **not** successful completion notification for a live Core agent task. Those remain separate acceptance tests.

An optional separate `LiveCoreSmoke` instrumentation performs **one harmless real Core request only**: it refuses to send unless exactly one visible "New Conversation" button is available, confirms the new composer is not under an existing chat route, and attempts a short no-tools response. It reads only counts of native observer events and its own conversation terminal flag, never exports conversation IDs, auth tokens or response text. A click is *not* proof of Core work; `liveCore=PASS` requires a native observer terminal event. If a button is ambiguous or the UI workflow differs, the test stops without sending. Usage: `am instrument -w -r com.agy.v24readiness/.LiveCoreSmoke` via the approved Shizuku instrument action.

### Actual live Core smoke attempt and limits

Using Shizuku instrumented dev app on 2026-10-20:
- Version-aware startup, `onUiReady`, dedicated New Conversation click, fresh route and composer all succeeded.
- First smoke stopped **before any model request** because a scripted synthetic `input` event was not recognized as a sendable message.
- Second smoke used native Android keystrokes; the send control became enabled and Enter was issued, but a new `/c/<id>` route was not observed. This **does not prove** the model received a request; do not retry blindly.
- Separate `SafeEvidence` instrument observed, over a ten-minute window, `NATIVE_WATCH_ADDED=0`, `NATIVE_CORE_STATES=0`, `NATIVE_CORE_COMPLETIONS=0`, `UI_AGENT_STATE_REPORTS=0`, `CURRENT_NOTIFICATION_COUNT=0`. It inspects only counts and never prints IDs or chat content.
- **Real completion notification is NOT VERIFIED.** New UI route/agent launch must be performed manually on the dev app or via a future verified app-specific automation; no user secrets are moved from `com.agy`.

`SafeEvidence` can be re-run through `am instrument -w -r com.agy.v24readiness/.SafeEvidence` after the user executes a real task. A passing `ReadinessProbe` is not a substitute for live Core task completion proof.
