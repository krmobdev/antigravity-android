# v2.4.5-kr1: English UI, attachments and notification navigation

Status: development checkpoint, **not release approval**.
Work is confined to dev. Main, stable v2.4.1-kr1, tags and release signing are unchanged.

## Changes and causes

- Add/attachment-preview dialogs no longer push/pop browser history. Their former
  cleanup history.back() could navigate the chat Home and unmount the file input.
  They use the existing native Back overlay stack; clicks do not bubble into the
  chat, and original action-before-close/trusted file-picker activation is retained.
- App-owned startup, notification, menu, download and microphone labels are
  English regardless of phone locale. Conversations and file names remain data.
  A source-policy regression also checks escaped Cyrillic strings.
- Mobile image/document/audio/video cards share 86px square geometry. Documents
  retain icon, name, size and compact type; full name/MIME remain available on
  the open control. Remove targets are uniformly 48px. Core limits/formats/send
  logic are unchanged (maximum five, or a lower advertised Core limit).
- Notifications select the enabled live launcher Activity instead of always
  targeting the different MainActivity class. The route bridge retains the
  current chat without navigating and replaces the current route for a different
  target. It coalesces in-flight duplicates and ignores stale completions.
- A legacy-entry forwarding guard is implemented, but its separate device
  compatibility probe still fails and is **not accepted**.

## Verified on Realme GT 2 Pro / Android 15

Installed Dev artifact:
dist/antigravity-v2.4.5-navigation-english-dev.apk

SHA-256:
67524cd6f2e6971ba9ac3d33b5005a03830e91bd12cc59c6a903b5a1d90da5d6

This is a debug-signed com.agy.dev build, not a production release.

| Scenario | Actual result |
| --- | --- |
| Real DocumentsUI: TXT select, preview, Back, remove, repeat | PASS |
| Real DocumentsUI: select TXT + PNG together, return both in FileList | PASS |
| Equal square mixed-card geometry, visible document type, 48px removal | PASS |
| Outside Add tap, Picker cancel, same route/document/Activity/WebView | PASS |
| Two Home/background -> posted notification contentIntent -> same chat cycles | PASS |
| Same Activity/WebView/Core generation, document timeOrigin, draft/history, no splash restart | PASS |
| One Back after those notification cycles -> Home, no duplicate chat | PASS |
| Real notification shade tap and Back | PASS: owner manually confirmed return Home without previous chat window; automated title lookup remains NOT PASSED |
| Old direct MainActivity notification, third compatibility cycle | FAIL: foreground return assertion; remains unresolved |

Passing current artifact logs:
build/android-picker-1793097727.log
build/android-route-intent-1793097756.log

Each passing run has the required scenario PASS marker and INSTRUMENTATION_CODE 0.
The combined run also includes a failing legacy probe; its overall exit is not
reported as a completely passing suite.

The posted-intent test uses the actual Notification.contentIntent and a scoped
shell foreground-start capability to model SystemUI dispatch. It is distinct
from a real shade tap and does not change user notification/DND settings.
No model request, user file selection, account-content logging or production
installation is performed. The owner independently confirmed two-file
selection and real shade entry followed by Back to Home without the previous chat window. Existing draft/attachment fixtures are preserved by setup guards.

Earlier device Back regression passed four reopen cycles of Model/Effort/Usage
and Settings, preserving the same Activity/WebView and draft. Native English
startup milestones/error/Retry and Home/resume tests passed on the preceding
Dev build. Their original logs remain in ignored build; they are not presented
as new device runs on the artifact above.

## Other regressions

- Web selftest/behaviour suite and 37 Python Web tests: PASS, including actual
  patched route logic, stale/duplicate navigation, safe previews, five-slot
  reservations, repeated file selection, cancellation, failures and English UI.
- Java regressions: PASS, including Core stages, parallel progress coalescing,
  terminal/new-run races, FIFO/read context, origin/token checks, PCM lifecycle
  and shell/startup policies. Operational PCM/STT transport was not rewritten.
- Six strict instrumentation-result parser tests: PASS. A generic summary
  mentioning PASS/PENDING is not confused with a real pending check; process
  success cannot hide a missing/nonzero/duplicated instrumentation result.
- Five owner-signing control-flow tests: PASS with fake files and mocked tools.
  No owner keystore is opened, copied or used.
- Earlier isolated real PTY RPC regressions passed standard and region-bypass
  Core, including two create/read/write/interactive/reconnect/close generations.

## Remaining acceptance and release work

| Original direction | Remaining |
| --- | --- |
| PTY | Real terminal UI/session acceptance; earlier RPC success is not UI acceptance |
| Agent notifications | Legacy entry, actual concurrent Core agents in background/screen off, cold start and terminal sound/DND acceptance; current shade entry/Back accepted by owner |
| Attachments | Real sends to a model across advertised formats, unsupported-format failures, background return and visual acceptance |
| Composer | Owner visual acceptance of uniform tiles; larger-font/TalkBack and long-chat checks |
| Release artifacts | Rebuild both fresh unsigned KR editions after candidate acceptance; verify manifest/Core/APK content, hashes and signing instructions |

Pixel 7 testing is excluded by the owner's decision. Android 8/12 environments
have not been exercised here. No fresh unsigned release APK is claimed ready,
no release signing is performed, and no merge/tag/Release is authorized by this
checkpoint.
