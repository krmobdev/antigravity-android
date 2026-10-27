# Dev QA: completion-notification return and Android Back (2026-10-27)

Scope: owner-requested A/B scenarios on the single `dev` branch. No production APK,
release, tag, signing identity, account or live Core task was changed.

## Scenarios

1. **Same chat A**: while A is visible, press Home; open its completion
   notification; retain A, its mounted UI/draft, and its route. A single Back
   returns to chat list; two repeats must not create a duplicate history entry.
2. **Other chat B**: after starting in A, navigate to B and press Home; open a
   completion notification for A. A replaces B in the router history
   (`replace:true`); single Back returns to the list, not B or A again.
3. Stale/in-flight completion, invalid ID, duplicate notification and
   resume events must not create phantom Back layers.

## Added regression coverage

- `web/tests/notification_back_flow.behavior.cjs` executes the **actual**
  `AGY_V24_DEEPLINK` route bridge taken from an input bundle, using a
  deterministic mock router/history and native acknowledgment callbacks.
  Three scenario groups pass on freshly built WebView assets.
- `web/tests/selftest.sh` runs the A/B tests on its **fresh disposable**
  WebView build, alongside the existing route/Back tests.
- `web/tests/test_staged_web_assets.sh` runs the same checks for the exact
  staged WebView `main.js` in Standard, Region Bypass and a repeat build.
  All three passed. The source and existing generated checkout files remain
  unchanged.

A direct test against **checked-in** `web/generated/main.js` fails: it is
an older generated snapshot whose notification handler unconditionally
navigates. The current `web/patch_runtime_v24.py` instead checks whether
the target chat is already visible and uses replacement navigation for a
different chat. The release builder stages a new disposable build, so stale
checked-in `generated/main.js` is **not** evidence of a shipping regression.
Always test the staged APK payload, not stale generated checkout artifacts.

## Actual Android Dev fixture

Command: `python3 tests/instrumentation-v245/run_navigation_regressions.py route-intent`.

Result: `STRICT_ACCEPTANCE route-intent PASS`,
`INSTRUMENTATION_CODE: 0`, two returns via the actual posted notification
PendingIntent into `com.agy.dev`. Same Activity/WebView, Core generation,
document timeOrigin, draft and history were preserved, the splash did not
restart, and one native Back returned to Home. Test log (ignored build output):
`build/android-route-intent-1793124891.log`.

This **device** probe covers the same-chat `/c/_new` fixture and does not
execute a real agent task. The other-chat A/B history replacement was tested
against the actual patched JS with a mock router. Genuine A/B existing
conversations, a real SystemUI shade tap, and screen-locked completion still
require manual on-device acceptance; they are not marked PASS by this report.
No private chat IDs/text or audio were collected.
