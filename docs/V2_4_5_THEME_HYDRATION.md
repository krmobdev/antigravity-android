# Light startup status-bar flicker: reproduced and corrected

Date: 2026-10-27. Branch: dev. Parent: f9da2f1ecf6b3ab66530fa1012c46c24dc302f45.

## Actual cause

The resource-only correction was insufficient. The owner was right that the
black flash remained after installing the light-bars APK. Earlier window-color
and warm-reload checks did not cover the real Connecting-to-ready transition.

The phase-aware on-device pixel probe reproduced it on
`antigravity-v2.4.5-light-bars-dev.apk`:

- 32 sampled frames through Connecting and UI-ready: one fully black status-band
  frame; app-header bands remained light.
- Native PreDraw history switched from Light (mode 2, ffffffff, dark icons) to
  Dark (mode 1, ff101010, light icons) around 8574ms, back to Light around 8691ms.
- The UI-ready native frame followed around 9772ms. LauncherLightActivity was
  active. This was not an accidental launch of the wrong installed app.
- `LIGHT_STARTUP_STATUS_PIXEL_BANDS=FAIL`, `INSTRUMENTATION_CODE: 1`
  in job `e24b0000000000000000000000000030`.

Core briefly applies its default theme while loading settings. Our existing
theme_sync observer treated every body-class observation as a user choice:
setThemeByUser and onVisualThemeChanged both reached native, repainted bars and
persisted that temporary Dark value. Native splash opacity does not cover the
system status bar, so hiding WebView content alone could not fix this.

A separate real resource mismatch was also found: SplashThemeLight inherited
dark status/navigation bars from AppTheme. Android DAY/NIGHT resource-resolution
tests reproduced that mismatch before correction and passed after explicit
Light overrides. This correction is retained but was not the complete fix.

## Implementation

- `app/src/main/java/com/agy/ui/webview/AppHtmlInjector.java` marks the trusted
  newly loaded document as still starting.
- `web/src/patches/theme_sync/notify.js` continues tracking actual Core classes
  but suppresses native preference/bar writes while that startup flag is set.
- `theme_sync/public-api.js` releases and flushes the final observed theme once.
  Duplicate release is idempotent.
- `app/src/main/java/com/agy/ui/MainActivity.java` releases sync from the existing
  guarded compositor-ready callback, immediately before revealing the UI.
- `app/src/main/res/values/themes.xml` and `values-v31/themes.xml` explicitly
  define Light starting-window status/navigation colors and dark icon flags.
  Dark resource entries are unchanged.
- Web/native module READMEs and regression probes were updated.

No startup timer, artificial delay or replacement Core theme controller was
added. Later real Settings theme changes retain their existing controllers and
native synchronization. Core binary, generated main.js (Composer/voice/Desktop),
and the DOM readiness bundle are unchanged from the preceding candidate.
Agent services, notifications, PTY and production app data were not changed.

## Validation on Realme GT 2 Pro / Android 15

Local Termux build/tests only, installation via Shizuku.

- Full Web selftests and Java regression suite: PASS.
- New behavioral test executes the actual theme_sync fragments: Light-Dark-Light
  hydration causes zero native writes; compositor release flushes Light once;
  duplicate release/install, later Light/Dark choices and legacy path pass.
- Local Gradle-free Dev build and public debug APK signature verification: PASS.
- Two real Connecting-to-ready pixel runs: 32 and 35 sampled frames, respectively;
  **zero black status-band frames and zero black header-band frames**. Both
  reached actual UI-ready. Native color stayed ffffffff with mode 2.
- Native startup probe: release only after commit, actual UI readiness, three
  native stages, opaque system exit, error/Retry, UI-only Retry with unchanged
  Core generation, Home/foreground resume with the same Activity: PASS.
- Actual Settings probe: Light/Dark and native system bars, themed lists,
  one-level Back, preset selection/restoration and original draft preservation:
  PASS. The startup guard does not freeze later theme choices.
- All four fixed-candidate instrumentation invocations returned
  `INSTRUMENTATION_CODE: 0`; strict per-test assertions passed in job
  `e24b0000000000000000000000000036`.
- Installed Dev APK SHA matched the local artifact.

The acceptance wrapper's final dumpsys enabledComponents parser failed *after*
the four tests and installed-hash verification. Relaunch was handled separately
through Android resolve-activity; the instrumentation results are not inferred
from the wrapper exit status.

Pixel observations are sampled screenshot bands, not a claim that every
SurfaceFlinger frame on every device was captured. No full screenshots/video
or private chat/account content were saved or exported. The owner can assess
the perceived transition in the installed build; final visual acceptance is
still the owner's decision. Google Pixel 7 device testing was explicitly excluded by the owner.

## Artifact

`dist/antigravity-v2.4.5-theme-hydration-dev.apk`, package `com.agy.dev`.
This remains an isolated debug build with existing Dev metadata 2.4.0 / 16,
not a final KR release.

SHA-256:
`7cc35f49526f45372efd10c8df0771ae098d87b6c3bd066caae45666b78e16f4`

The preceding light-bars build
`f0c4747a089ec55790bd44040f7d1c1f6f06150a949b8a4441efad60deb768aa`
and opaque-reveal build
`c2948f355646c6a6daabf5c19631ff3744224f455c41759c1e48e62f3ae263de`
are superseded for this issue and must not be described as having solved it.

main, production, stable tags and release signing were untouched.
Final unsigned KR artifacts and publication remain separate owner-directed steps.
