# Native Core agent observer (experimental Dev build)

## Why

Android may pause the WebView JavaScript event loop when the Activity loses
focus. Until now, `AgentNotifications` received `IDLE` only from the WebView;
a finished task could remain marked "working" until the user opened the chat.

The foreground `CoreServerService` remains running while the WebView is
backgrounded. A service-owned status subscriber can receive independent
notification events directly from the local `language_server`.

## Architecture

- `AndroidBridge.onAgentState` observes a legitimate RUNNING/BUSY task and
  registers its conversation ID with `NativeAgentStateObserver`.
- `CoreServerService` owns the observer for the service lifetime, not the
  Activity. At most **12** concurrent conversation streams; no polling of
  chat text, screen, keyboard, foreground-app state or arbitrary processes.
- The observer persists the active ID set in private preferences to restore
  subscriptions after a sticky service restart.
- The observer connects to local loopback only
  (`127.0.0.1:<CoreRuntimeState.httpsPort>`). It uses the exact
  `StreamAgentStateUpdates` ConnectRPC method from the bundled protobuf
  `LanguageServerService`, and adds the native CSRF token from
  `CoreRuntimeState`. It **never logs** the CSRF token, prompt text, or tool
  arguments.
- TLS does **not** use a trust-all socket: the SHA-256 fingerprint of the
  bundled version 2.3.0 server certificate is pinned, checked on every
  connection. A changed certificate fails closed until reviewed. This is an
  experimental version-specific pin, not a general-purpose trust mechanism.
- Streaming envelope parsing is size-limited to 4 MiB per frame; output
  parsing uses only conversation ID, status, and fullyIdle.
- The same `AgentNotifications` transition tracker handles native and
  WebView events, suppressing duplicate FINISHED notifications.
- On IDLE + fullyIdle, cancel only this observer subscription, not the
  underlying agent task. If native connection fails, existing WebView
  notifications remain functional (fallback).
- Bounded retries for errors and timeouts; disconnect subscriptions and
  stop executor on service teardown.

## Evidence and limitations

The service descriptor is embedded in `web/main.js` and identifies:
`exa.language_server_pb.LanguageServerService/StreamAgentStateUpdates`.
A loopback request to that endpoint with missing or invalid CSRF returns
`application/connect+json` and a framed "unauthenticated" response, matching
the native protocol implementation.

**Outstanding device verification:** an authenticated native stream has NOT
yet been observed receiving a task completion. The new code is an experimental
candidate requiring live log evidence and a 5-10-minute background test.

The native observer only watches tasks it learns about from an initial
WebView RUNNING event (or private persisted IDs). It does not discover tasks
created externally while this app is not running. In cases where the core
itself stops or Android kills the foreground service, immediate notifications
cannot be guaranteed.

If the bundled core certificate changes in future releases the native
observer fails closed by design. Update/test the pin for that release, not
by disabling TLS verification.

## Verification plan

1. Build `bash build.sh --dev --region-bypass`, validate the signed APK.
2. Ensure no regressions in Java frame tests and the WebView/notification
   regression suite.
3. Install **over** existing `com.agy.dev`; no data deletion, official
   `com.agy` remains unchanged.
4. Start an agent task and verify native diagnostic events
   `native_observer watch`, `native_observer subscribe` and that no
   `stream_error` repeats.
5. Minimize Antigravity Dev and wait for the agent to finish without opening
   it. Verify that `native_observer finished` and `notification FINISHED`
   occur BEFORE the next `activity onResume` or `host_resume`.
6. Test with the screen locked, then with two concurrent tasks. Check that
   tapping each notification opens the right conversation.
7. Re-test sidebar reconciliation and old-chat unread dots.

If there are native transport errors, gather the app-private
`logs/agy-diag.log` via the existing `export-diag.sh` from the Dev app's
shell, **do not** install into the official app or submit a production PR.
