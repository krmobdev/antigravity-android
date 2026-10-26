# Dev-only Android candidate probe

Build locally: bash tests/instrumentation-v245/build.sh.
Target is exclusively com.agy.dev. Package com.krmobdev.v245qa is separate from
existing readiness tests. Both use the project's public debug identity.

The probe checks actual WebView control geometry, local text/image preview,
separate removal and Android Back on the + sheet. It never clicks Send or starts
a model request. If onboarding/auth prevents a composer, it reports PENDING.
It posts synthetic native stages for two fixed test-only IDs while the Activity
is in the background and checks actual NotificationManager output/FIFO/outcomes.
These are platform fixtures, not proof of real Core agent delivery. It requests
notification permission only for the dev app, and removes only its own fixtures.

Run: am instrument -w -r com.krmobdev.v245qa/.MobileCandidateProbe.
Flags distinguish PASS, FAIL and PENDING; a completed probe is not automatically
full release acceptance. No private account/chat contents are logged.

Optional -e capture yes exports a small screenshot of the focused dev composer into the private instrumentation log for visual inspection. It is disabled by default; keep screenshot logs private. Empty composer geometry checks a maximum height and a single toolbar row as well as 48 px touch targets.

Focused regression: am instrument -w -r -e mode back com.krmobdev.v245qa/.MobileCandidateProbe
Uses real Android Back keys and backdrop touches for four model/effort/Usage
return/reopen cycles and Settings child screens. Checks Activity/WebView survival
and draft preservation; no Send or microphone capture. Startup dismissal sends
Back only when Android Settings is the foreground package, never merely while
the target window is waiting for focus. Inspect INSTRUMENTATION_CODE and flags.


Compact controls mode: -e mode toolbar. Checks real permissions/model menus,
48px geometry, ordering, a single permission selector, Back and unchanged current
mode/draft. No preset is changed by this probe. With -e capture yes, exports
cropped toolbar and permissions-menu regions only; no prompt or chat screenshot.


Settings polish mode: -e mode polish. Exercises actual Light/Dark settings,
themed preset lists, selected option, one-level Back, painted 20% reduction
with retained 48px targets, and switch geometry. Preset selection is exercised
only if no custom color overrides would be lost; original colors/preset/theme
are restored in finally. Optional captures contain only toolbar/Add/settings
regions. No audio, model request or permission preset is changed.

Grey surfaces mode: -e mode surfaces. Checks the model grey surface and opens
Add/Appearance without changing settings. Captures require both Activity focus
and the target package active in UiAutomation; otherwise report PENDING.

Unified interface mode: -e mode unified. Checks real Add card bounds above the
footer, two reopen/Back cycles, shared model/permission card surfaces, original
draft/permissions unchanged, and Android IME layout. Optional crops contain only
menus. No setting, audio recording or model request is submitted.

Settings polish also requires selected-theme and switch-track backgrounds to
contrast with the containing settings group in both Light and Dark modes.
