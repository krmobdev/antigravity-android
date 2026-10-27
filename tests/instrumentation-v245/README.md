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

Native startup mode: -e mode startup. Checks the real authenticated UI-ready
handoff and hidden native splash, then temporarily mounts an isolated controller
over the Dev UI to exercise actual native stage labels, Light/Dark, fatal
timeout and Retry callback. This fixture never restarts Core or changes user
data/themes. Optional pictures draw only the owned native view into a bitmap,
not the screen, chat, other apps or system UI. It does not simulate a successful
cold install or live Core failure; those require separate startup acceptance.

When the actual draft/attachments are empty, startup mode additionally injects
a UI-timeout error in MainActivity and clicks its real Retry button. It requires
a fresh authenticated UI-ready callback with unchanged Core readyGeneration.
With an existing draft this reload test is PENDING, preserving the draft.

Startup mode also verifies the actual WebView loaded the new DOM-proof gate,
protecting this acceptance from stale cached JavaScript after APK update.

Startup mode ends with real Home/background and foreground intents, requiring
a ready UI with hidden splash and unchanged Core generation. It reports whether
Android retained the same Activity. Long unready/background expiration is covered
by the DOM-gate behavior test; it is not asserted as a timed on-device scenario.

Recovery mode: -e mode recover-mobile. Dev-only, synchronously sets the existing
desktop_mode preference false before UI launch. No data/cache/auth reset, no
requests or other settings changed. Launch the Dev app normally after the probe.

Adaptive Desktop mode: -e mode desktop. With an empty real composer, switches
Desktop on, opens General in portrait, rotates with Settings open, clicks
Appearance/General in landscape, uses native Back, rotates back, checks one-level
Back and clicks the actual Desktop switch off. It checks native preference,
fresh boot profile, unchanged Core generation and model-pill midpoint with
48px neighboring controls. Original mode/orientation are restored in finally.
Captures crop settings-page/footer only. Existing drafts/attachments skip this
state-changing test. No prompt, recording or model request is submitted.

Desktop acceptance taps General/Appearance and both switch directions via real
Android MotionEvent injection after DOM hit testing and target-window focus/
package checks. A blocked or covered control fails; JavaScript .click does not
stand in for touch acceptance.


Handoff mode: -e mode handoff. Requires a positively identified empty composer
and no attachments, reloads UI only, and samples content/header geometry,
document/font status, native readiness and splash opacity (never text or
screenshots). It reports observed stability separately from probe completion;
inspect HANDOFF_OBSERVED_STABLE as well as INSTRUMENTATION_CODE. Core generation
must stay unchanged. Native startup fixtures additionally require an opaque
startup view through the actual system-splash exit handler.


System bar mode: -e mode bars resolves the actual packaged SplashThemeLight
and SplashThemeDark under DAY and NIGHT Android configurations, checking status/
navigation colors, background and icon appearance. No user setting is changed.
Handoff mode also samples the native status-bar color/icon flag through UI reload
and fails if either changes. These attributes/geometry checks do not assert the
colors of every SurfaceFlinger frame during an OS transition.


Pixel mode: -e mode pixels starts sampling at the real Connecting stage,
continues through UI-ready plus 1.5 seconds (65-second safety cap), and compares
three status-bar/background bands plus three app-header bands. Full screenshots
are held only in memory and immediately recycled; no screenshot/video/chat text
is stored or exported. A PreDraw listener logs only native color, icon flag,
theme mode and ready-state transitions. Missing UI readiness is PENDING, never
pixel acceptance. Sampling can miss a shorter frame; owner perception remains
part of acceptance. It never changes theme, sends a prompt or restarts Core.



New navigation/attachment regressions (Dev package only):

- Build: bash tests/instrumentation-v245/build.sh
- Install Dev + QA: python tests/instrumentation-v245/run_navigation_regressions.py install
- Real File Picker: python tests/instrumentation-v245/run_navigation_regressions.py picker
- Posted notification intent and Back: python tests/instrumentation-v245/run_navigation_regressions.py route-intent
- Actual shade tap: mode route-reuse (cannot locate the owned notification on this device; not passed).
- Legacy MainActivity notification: mode route-legacy (separate unresolved compatibility probe).
- Parser tests: python -m unittest discover -s tests/instrumentation-v245 -p 'test_*.py'

Picker uses an empty /c/_new fixture and genuine DocumentsUI results, owned
TXT/PNG files, actual finger input, preview Back, repeat, multi-select, computed
equal card dimensions, 48px remove targets, visible type and cancellation.
The monitor pins only this chooser to DocumentsUI and its owned fixture folder.
There is no fake ActivityResult or model request. Existing drafts/attachments
skip setup; only owned fixture data is removed.

route-intent sends the actual contentIntent from a posted quiet notification.
A short shell capability to start a background Activity and sender options
model the foreground permission of a system tap; they are dropped immediately.
This is distinct from tapping the shade. Two background/return cycles verify
the same Activity/WebView, Core generation, document timeOrigin, draft/history,
dismissed splash and exactly one Back to Home. route-legacy adds a third direct
MainActivity PendingIntent; its failure is reported separately.

The runner requires process success, exactly one INSTRUMENTATION_CODE 0, the
scenario PASS marker and no actual FAIL/PENDING status. Generic summary text
mentioning PASS/PENDING does not incorrectly fail an otherwise passing run.

### Real PCM restart probe

Mode mic-pcm-hardware runs twelve immediate start/read/stop/restart cycles
against production NativePcmCapture and real AudioRecord, including idempotent
double Stop. It refuses an existing active dictation, requires current v7 assets,
and neither modifies the Composer draft nor sends audio to Core. Audio frames
are discarded locally; output contains only PASS flags and counts. This proves
hardware lifecycle, not speech recognition or a complete model request.
Check INSTRUMENTATION_CODE and individual flags, not the shell exit code alone.
