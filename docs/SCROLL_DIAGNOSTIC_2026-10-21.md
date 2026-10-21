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

## Session 1 findings (numerical telemetry only)

- 76 logcat lines, 472 decoded events, 19 gestures; logcat included earlier buffered entries.
- During active gestures scrollHeight=3701px and clientHeight=642px; no content resize coincided with a delayed position change. A resize to zero marks background/detach between two active intervals.
- After touchend, observed position differences without intermediate JS-visible samples: -536px/791ms, -1732px/921ms, +1090px/914ms, +2659px/896ms, -851px/499ms, -1854px/894ms.
- The single tracked programmatic scrollTo was at 70.246s, to the bottom, corresponding to the working scroll-to-bottom button. Exclude it from suspected failures.
- No tracked scrollTop assignment, scrollBy, or nested scrollIntoView was recorded during those delayed positions.
- Position sample gaps alone do NOT prove UI-frame freezes: Chromium's compositor may scroll while the JS-visible coordinate stays stale. Next: correlate Perfetto compositor/renderer/UI scheduling and frame timeline during short flicks. No production fix yet.
