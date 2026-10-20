# v2.4 dev UI readiness instrumentation

Build on Android/Termux: `bash tests/instrumentation-v24/build.sh`. Install the resulting separate test APK (package `com.agy.v24readiness`) and run `am instrument -w -r com.agy.v24readiness/.ReadinessProbe` through approved Shizuku instrumentation support. Target is **only** `com.agy.dev`.

The previous installed `com.agy.notificationtests/.NotificationInstrumentation` stopped with `Dev sidebar not ready` after checking the package/notification permission. A second previous diagnostic instrumentation expected legacy `web/main.js` and failed with FileNotFoundException; v2.4 stages `assets/web/generated/main.js`. Old instrumentation source was not available in the workspace, so do not assume a product sidebar failure from these old test errors.

This replacement uses the actual v2.4 launcher (`LauncherDarkActivity` / `LauncherLightActivity`), traverses superclass fields from `MainActivity`, waits for `CoreRuntimeState.serverUrl`, checks `webUiReady` and `appLoaded`, then checks presence of React root, sidebar DOM structure and token-gated bridge **without reading or emitting account tokens, conversation names, messages or user data**. OAuth inspection is a boolean via the existing `OAuthTokenStore.hasToken`.

Actual Android/Shizuku result on 2026-10-20: `CORE_READY=true`, `ON_UI_READY=true`, `WEB_APP_LOADED=true`, `SIDEBAR_CANDIDATE=true`, `BRIDGE_TOKEN_INJECTED=true`, `UI_READINESS=PASS`, `summary=uiReadiness=PASS`. This establishes dev UI readiness, **not** successful completion notification for a live Core agent task. Those remain separate acceptance tests.
