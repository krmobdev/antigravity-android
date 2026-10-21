# Theme transition: verified duplicate native update

Date: 2026-10-21. Isolated branch `fix/theme-single-native-20261021`.
**Status: concrete theme-bridge defects corrected; scroll-jank fix NOT yet proven on device.**

## Reproduced by the user (do not re-request these experiments)

- On fresh installs (both original v2.4.0 and KR fork), normal chat fling at first.
- Switching from dark to light **without restarting** can trigger post-release scroll stutter.
- Catppuccin and Gruvbox Material also triggered it on separate phones.
- Returning to Default Dark and force-stopping did not restore smooth fling.
- Earlier custom JavaScript momentum bypassed the symptom but is not the desired fix.

## Confirmed implementation defects

Before this change, a single `notify()` call from `web/src/patches/theme_sync/notify.js` invoked BOTH `Android.setThemeByUser(theme)` and `Android.onVisualThemeChanged(theme)`, where both methods independently persisted native theme mode and posted `ActivityPainter.apply` on the main thread. In addition, `setThemeByUser` redundantly called `LauncherIconSwitcher.scheduleSwitch` twice and `onVisualThemeChanged` persisted user settings despite a visual-only API contract.

This change reduces the update to a **single bridge call**, suppresses a repaint for a same-mode event, schedules the launcher only once and prevents the legacy visual-only callback from writing preferences. The duplicate Android window invalidations were real, measurable work; the effect on Chromium inertial scrolling is **still unverified**.

## Automated regression

```sh
bash web/tests/selftest.sh
```

New tests execute actual JS `notify` and compile/run the actual Java `ThemeController` with minimal Android test doubles. Both tests **failed** against unchanged v2.4.0 source and **passed** against this change. Existing chat/notification tests passed. Signed test APK was built and signature-verified; production code, UI and DOM scroll handlers outside this narrow change are unchanged.

## Device acceptance criterion (only when developer is ready to test)

Switch dark → light without relaunching, open chat, execute several short flings, then switch back. Test on a device where bug has already been observed. No need to repeat the prior diagnostic history or reinstall the app. Do not merge to stable unless the same scenario is confirmed smooth and the rest of the UI/notification regressions remain green.

The signed APK uses the normal KR application ID and signing key and **must not** be installed without authorizing overwriting the current package. It preserves app data if installed with `adb install -r`.
