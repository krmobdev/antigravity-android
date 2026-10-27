# Adaptive Desktop / centered model control — 2026-10-27

Implementation: `7b0cadeadfbb6fdd009268d8462925f81d9efcf9`, branch `dev`.

## Problem and correction

Core had eight independent mobileLayout consumers, while the injected Composer
used a separate viewport/touch rule. Native Desktop preference could therefore
leave Core panels and injected sheets in different layouts. Reloading the old
document also retained its previous injected boot preference.

All eight consumers now use one guarded hook. Native Desktop uses mobile
panels at widths up to 720 CSS px and the original desktop layout above that
width. Ordinary Android mobile mode also uses mobile panels on coarse-pointer
screens. Non-Android Core feature behavior is retained. Switching the preference
loads a newly injected document; Core service is not restarted. In wide Desktop,
native Back sends Escape to a visible original Core popup after the mobile
dispatcher declines it. Root Back/minimization behavior is unchanged.

The model wrapper centers its pill in the space between the permissions shield
and microphone, accounting for the existing toolbar gap. Other control centers
and 48px touch targets are retained.

## Local validation on Realme GT 2 Pro / Android 15

- Full Web selftests and Java regression suite passed locally in Termux.
- Gradle-free Dev build and public debug signature verification passed.
- Actual Android instrumentation: native MotionEvent taps enabled Desktop,
  opened General/Appearance in portrait and landscape, and disabled Desktop.
- Rotation with Settings open selected the expected Core layout in each
  orientation. Native Back closed the wide dialog; portrait Back returned one
  settings level. The saved native preference and fresh boot profile agreed.
- Core readyGeneration was unchanged across this flow.
- Toolbar midpoint error was at most one CSS pixel; neighboring targets were
  at least 48px. A cropped footer screenshot was inspected and centered.
- Acceptance job `e24a0000000000000000000000000017` succeeded with
  `INSTRUMENTATION_CODE: 0` and
  `ANDROID_ADAPTIVE_DESKTOP=PASS_PORTRAIT_LANDSCAPE_BACK_REAL_TOGGLE_AND_CENTER`.
- An earlier immediate-tap test failed while the stock panel was transitioning.
  The probe now waits for the transition and a successful DOM hit test before
  injecting touch; target focus/package are also required. This is test
  synchronization, not a forced click through an overlay.
- Repeated mobile Back/backdrop regression also passed: four model/effort/Usage
  return/reopen cycles, Settings child navigation, Activity/WebView survival and
  draft preservation; job `e24a0000000000000000000000000020`,
  `ANDROID_PROGRESSIVE_BACK=PASS_REAL_KEYS_AND_BACKDROP_NO_SEND`,
  `INSTRUMENTATION_CODE: 0`.
- Mode and requested orientation are restored in finally. Existing drafts or
  attachments skip the state-changing flow. No prompt/model request or audio
  capture was submitted.

A landscape screenshot was not obtained in the initial run because the target
was not focused; it is not reported as visual acceptance. Functional native
touch/Back assertions did pass. Pixel 7 acceptance was explicitly excluded by the owner on 2026-10-27. Other
Android versions, large text/TalkBack and owner visual acceptance remain pending. These checks do
not replace live voice, attachment-send, agent-background or PTY acceptance.

## Installed artifact

`dist/antigravity-v2.4.5-adaptive-desktop-dev.apk`, package `com.agy.dev`.

SHA-256:
`4b6fd7b7ddfec1567e6f78d43ae0bcadafcea504c5a98ae0a3be9c039f9e7c5a`

The Dev manifest retains the existing test identity versionName 2.4.0 /
versionCode 16; this is not release metadata for v2.4.5-kr1.

Installed locally through Shizuku. Production app was not removed/replaced.
Core binary and the generated native audio helper block are byte-for-byte
unchanged from `antigravity-v2.4.5-native-startup-final-dev.apk`.
The owner's working speech-to-text route remains unchanged.

This is a debug visual-test build, not either final unsigned KR artifact.
Release signing, main integration, tags and publication are not authorized here.
