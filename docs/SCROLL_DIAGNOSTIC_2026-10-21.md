# Antigravity scroll inertia diagnostics (21 Oct 2026)

Local-only diagnostic branch: `diag/scroll-inertia-20261021`. No production fixes.
Base: stable `v2.4.0-kr1` (`com.krmobdev.antigravity`, versionCode 17).
Installed baseline APK SHA-256 before testing: `cc0b0c450f88fc2d87a2a9d257a3135290eb18c0581c6c7a3a1b2bb36457ce2c` (standard edition).

## Changes
- `web/index.html` includes the temporary `web/scroll_diagnostics.js` before bundled React.
- The script observes only `[data-testid="autoscroll-viewport"]`, with throttled numerical events.
- Captures touchstart/move/end, sampled scroll offsets, viewport/content resize, scrollend,
  and JS-driven scrollTo/scrollBy/scrollIntoView and scrollTop assignments.
- No message content, identifiers, URLs or DOM text are recorded; optional callsites are
  restricted to `main.js:line:column`. Android's existing WebChromeClient writes
  `console.info` to logcat tag `AGY_JS`.

## Test and capture
- Test syntax and behavior: `node web/tests/scroll_diagnostics.test.cjs`
- Install only after user consent: `adb install -r dist/Antigravity-scroll-diagnostics-v2.4.0-kr1.apk`.
  The build is the **standard** KR edition, matching the user's installed baseline.
- Wait for the app to start and a normal conversation screen to open; capture logs with
  `adb logcat -v epoch -s AGY_JS:I`, filter ONLY lines containing `AGY_SCROLL_DIAG_V1`.
  Store in a local private text file rather than posting logs publicly.
- Reproduce (1) continuous finger drag, (2) short repeated flicks,
  (3) smooth drag then release and watch for a delayed jump. Compare the
  `touchend` timestamp to `js-scrollTo` / `js-setScrollTop` / `resize` /
  `frame` records. Timestamps are monotonic milliseconds since browser boot.
- Restore stable APK with `adb install -r` using the existing signed standard stable
  APK. Do not uninstall or clear storage (would destroy app data).

## Caveats
- JavaScript sampling can perturb performance slightly. Do not extrapolate FPS from it.
- Browser compositor/inertial scroll can continue independently of JavaScript.
- Absence of JS writes is not proof that Chromium did not move the visual viewport.
- Script logs only once a chat viewport is discovered. Review logcat marker before any
  timed capture.
- Builds must use the *existing* KR release keystore, never a new key. Do not distribute
  this diagnostic artifact as a normal public release.
