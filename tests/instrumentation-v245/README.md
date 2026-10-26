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
