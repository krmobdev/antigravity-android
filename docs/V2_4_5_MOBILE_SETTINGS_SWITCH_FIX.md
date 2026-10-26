# Mobile settings toggle dimensions (Realme, 2026-10-26)

The stock bottom-sheet includes Custom Flags and Models & Usage settings.
Screenshots showed settings switches expanded into near-square blue or grey controls.
Root cause: web/mobile-composer.css added `[data-testid=bottom-sheet] button { min-height:48px; min-width:48px }`, unintentionally overriding upstream `dX` role=switch's `h-6 w-11` dimensions.

## Fix
Scope 48px touch-target rules to `button:not([role="switch"])`. The default switch remains 44×24 CSS pixels, retaining upstream thumb geometry, colors, state and callbacks. Ordinary action buttons keep the 48px minimum. No microphone, transcription, Android Java, RPC, or JS changes.

## Tests
- `python -m unittest discover -s web/tests -p 'test_*.py'`: 35 tests PASS.
- `bash web/tests/selftest.sh`: PASS.
- Android QA instrumentation `mic-visual`: `SETTINGS_SWITCH_SIZE=44.0x24.0`, `SETTINGS_SWITCH_NATIVE_SIZE=PASS`, `SETTINGS_ACTION_TOUCH_SIZE=PASS`, mic glyph/stop styles PASS, `INSTRUMENTATION_CODE:0`.
- APK: `dist/antigravity-v2.4.5-settings-switch-fix-dev.apk`, SHA-256 `63fdb40465e1bb31c6dd9f6da77459a59da0393b0c1a7d94f1b97640345d2eb1`, 103514528 bytes, `com.agy.dev`, debug signing.
- Download copy: `/storage/emulated/0/Download/antigravity-v2.4.5-settings-switch-fix-dev.apk`; installed on Realme via Shizuku (`Success`).
- Compared previous working mic UI APK: `assets/web/generated/main.js` and `classes.dex` CRC identical. Only `assets/web/mobile-composer.css` changed besides signature/runtime manifest metadata.

## Safety
Production package and main branch untouched. Pixel 7 and other devices not yet tested. Note that standard upstream switch track is 44×24px; its surrounding settings row remains touch-friendly.
