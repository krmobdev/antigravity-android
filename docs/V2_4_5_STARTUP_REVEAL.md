# Startup header reveal correction — 2026-10-27

Branch: `dev`; parent: `afe7918ae15366127c9a0b6b3bc1939b03e68ece`.

## Findings and changes

The Android 12+ system-splash exit handler explicitly set the native startup
view alpha to zero and animated it back to one. This exposed the WebView while
it was mounting. Separately, the DOM readiness gate accepted any visible button,
including the mobile Settings header before the main entry content was ready.
A JavaScript animation-frame acknowledgement also did not guarantee that the
WebView compositor had committed that frame.

- `SplashAnim31.java`: fade only the system layer; keep the native surface
  opaque. Exit animation documentation was corrected.
- `MainActivity.java`: wait for `WebView.postVisualStateCallback` before
  marking the UI ready, revealing it or routing a notification. A per-load
  sequence and pending flag reject stale compositor/duplicate ready callbacks;
  UI reload, Desktop mode changes and Core Retry invalidate old requests.
- `startup_ready/detect-ui.js`, `signal.js`: require completed document
  loading and real content/editor when Core chrome is present. The same content
  node, header geometry and palette must survive the existing two paint frames.
  Button-only auth/onboarding fallback is retained.
- Web behavioral regressions and Android native/handoff instrumentation were
  extended. The UI Retry test also positively requires a composer before
  reloading; an empty selector result on auth is not an empty draft.

No extra startup delay, Core restart, scrolling controller or audio change was
introduced. The existing timeout still produces a retryable error; elapsed
time never counts as proof of UI readiness.

## Actual validation on Realme GT 2 Pro / Android 15

All builds/tests ran locally in Termux; installation used Shizuku.

1. Full Web selftests, Java regressions, local Gradle-free Dev build and public
   debug APK signature verification passed.
2. Before updating Dev, the new native fixture invoked the real old
   system-exit handler on an isolated native startup view.
   `STARTUP_SYSTEM_EXIT_NATIVE_OPAQUE=FAIL`, `INSTRUMENTATION_CODE: 1`
   reproduced the transparency defect as expected
   (job `e24b0000000000000000000000000016`).
3. On the new installed APK, the same check passed. Real UI readiness/dismissal,
   all three native stages, Light/Dark fixture palettes, error/Retry, actual
   MainActivity UI Retry and background/foreground return passed. Core generation
   remained unchanged during those UI actions, and Android kept the same Activity
   across the tested Home/foreground return.
4. The handoff probe sampled loading/interactive/complete documents and visible
   composer/header geometry. The native surface stayed opaque before readiness;
   the revealed UI had a composer and a completed document. The sampled header
   position stayed consistent. `HANDOFF_OBSERVED_STABLE=PASS`,
   `HANDOFF_CORE_UNCHANGED=PASS`.
5. Both fixed-version instrumentation runs returned `INSTRUMENTATION_CODE: 0`
   and strict runner assertions passed
   (job `e24b0000000000000000000000000017`).
6. Installed APK SHA matched the local artifact. Core binary and the entire
   generated main.js (including working voice/Composer/Desktop code) matched
   the previous adaptive-Desktop APK byte-for-byte.

The initial warm-reload diagnostic did not reproduce the complete perceived
flicker; the isolated native handler did reproduce its transparency bug.
These geometry/native assertions are not a video or an owner's visual judgment.
Final perception of the transition remains for the owner to assess in the
installed Dev build. No private chat/account content or recording was captured.

## Artifact and scope

`dist/antigravity-v2.4.5-stable-startup-dev.apk`, `com.agy.dev`, public debug
identity. Dev metadata remains versionName 2.4.0 / versionCode 16.

SHA-256: `c2948f355646c6a6daabf5c19631ff3744224f455c41759c1e48e62f3ae263de`

Production app/data, release keys, main, tags and GitHub Releases were untouched.
Final KR unsigned artifacts are a separate later step. Pixel testing was
explicitly excluded by the owner; it is not an outstanding acceptance request.

## Owner feedback after installation

The owner still reports a black flash in the system status bar in Light mode.
The native-view/DOM checks above do not cover system-bar pixels. Overall startup
visual acceptance remains OPEN; status-bar palette transitions need a separate
follow-up fix. Do not report the entire flicker issue as resolved.

## Follow-up with reproduced status pixels

The remaining system-bar flash was reproduced through the actual Connecting
transition and traced to transient Core theme hydration being persisted by
theme_sync. See [V2_4_5_THEME_HYDRATION.md](V2_4_5_THEME_HYDRATION.md) for the
corrected implementation, failing-before/passing-after evidence and current APK.
This report's earlier opaque-reveal APK is superseded for flicker acceptance.
