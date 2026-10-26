# Compact mobile Composer controls

Owner request: separate permissions icon and a shorter model button, following
the provided ChatGPT layout. Work remains solely on dev.

- Toolbar order: +, current permissions glyph, model pill, mic, Send/Stop.
- Native Core cWb/bWb permissions selector is reused. Mobile exposes it for
  non-subagent input when upstream hides the optional UI. Current values and
  allowed presets come from Core's existing settings layer; no new policy enum.
- Selecting a preset calls the original global setPrefValues or project update
  handler according to the existing inheritance rules. Opening/closing the menu
  does not change settings. Save errors retain the original error notification.
- Model trigger remains the original button/controller. Visible mobile labels
  omit redundant Gemini/Claude branding; full model and effort remain accessible
  and in the menus. Unknown/custom names are preserved and can ellipsize.
- Desktop visibility rules, backend request formats, voice, Send/Stop and
  progressive Back are retained. Controls have at least 48px touch targets.

Changed product files: web/src/mobile/composer.js, web/patch_mobile_composer.py,
web/mobile-composer.css. Documentation: web/README.md. Regression tests cover
compact labels, original permission handler persistence and real Android menus.

Validation so far: Web selftest PASS; 35 Python Web tests PASS; actual original
Core permission handlers tested with store boundaries replaced by fixtures:
global/project writes, unrelated settings preserved, placeholder and error paths.
Android toolbar probe PASS: distinct icon, compact model, 48px geometry, actual
Core options, unchanged permission mode/draft, model open/Back and live host.
INSTRUMENTATION_CODE=0. No real permission preset was changed by device probes.
One initial toolbar run reported model Back FAIL; subsequent focused run passed.
The subsequent full four-cycle Back/backdrop regression passed, including Settings subpages (INSTRUMENTATION_CODE=0). The original dictation-button routing probe also passed with capture paths stubbed: native PCM=1, browser capture=0, audio attachment=0. No live microphone capture or real permission change was performed by these UI probes.

Local Gradle-free debug build installed through Shizuku into com.agy.dev:
dist/antigravity-v2.4.5-compact-controls-dev.apk
SHA-256: 352d07d03e9d60ef1d5f5f234555d32f785278d85979f46995bf40c966a43084
This is an isolated QA build, not a signed KR release. Production unchanged.
