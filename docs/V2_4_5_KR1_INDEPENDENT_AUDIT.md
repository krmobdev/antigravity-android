# Independent dev audit

Baseline: fe0b7200fc254516f8ca20a40a9d20ca868a4814.
GitHub main remains 1cc88bd40667f60e819c5ca415dc7cd69294632d.
Only dev is writable. Previous checkout changes preserved in stash and
.git/v245-pre-dev-wip; not applied over current dev.

Original implementation through d8d0bbb9: PTY, real native Core stages,
notification FIFO, safe attachments, compact composer and audio permissions.
Later work: cache invalidation, silent progress channel, native AAC fallback,
native 16kHz PCM transport into original Core dictation, mic styling and switch CSS.

Fresh baseline: Java tests PASS; Web selftest PASS; Python Web tests 35 PASS.
These tests do not exercise native capture errors or late worker races.

| Area | Audit result / remaining evidence |
|---|---|
| PTY | Fail-closed patch and shell policy retained; fresh RPC and UI/Pixel7 checks needed |
| Notifications | Silent LOW progress channel and builder, separate completion channels; no DND bypass found. Real agents/background/screen-off pending |
| Attachments | Cap 5 and safe previews retained. New CSS hides file type/image metadata and reduces delete targets below 48dp. Real model send pending |
| Composer | Upstream settings/send/stop retained. Repeated stock-sheet Back/reopen still unaccepted |
| Switches | role=switch excluded from sheet button minimums. Actual Light/Dark/settings visual check pending |
| Voice | Upstream streaming/attachment selection and Core RPC preserved; owner personally confirmed speech-to-text. Fresh edit/send pending |
| PCM | Defects found: read failure does not release AudioRecord; old worker can overwrite new-session failure/state/queue; overflow silently drops audio |
| PCM transport | Native queue bounded; JS pre-session queue and outstanding sends lack local bounds. Needs targeted lifecycle/backpressure tests |
| Bridge | Token required for capture methods; token getter native-only; foreground/permission checked. Activity detach needs explicit cleanup |
| Cache | Resource cache cleared only after APK update timestamp changes; no cookie/storage clearing or extra reload. Upgrade/custom-resource check pending |
| Final APKs | Existing unsigned files stale; rebuild after owner accepts dev |

No live audio captured by this audit. Owner-reported functional dictation is
preserved as evidence, separate from fresh automated checks. No release signing,
main merge, release tag or publishing authorized.


## Completed follow-up milestones

- 809200eac8b63e371106c11bbd325866e1216623: PCM session ownership, exactly-once
  AudioRecord cleanup on read/overflow/limit failures, stale worker isolation,
  Activity detach cleanup. New fake-source fault/race tests PASS; no microphone
  acquired by tests. Upstream sample format and Core STT/editor routing retained.
- 3c3abff28f834a93fd40b9b713030b7dcd9408ef: Android Back, Escape and physical
  backdrop taps pop one active menu level. Drawer-local Settings path and
  disposed-overlay cleanup prevent root closure and stale callbacks.
- Realme device probe: four repeated model/effort/Usage cycles using alternating
  real Android Back keys and backdrop touches PASS; Developer and Models return
  to Settings root PASS; Activity/WebView survive; draft preserved.
  INSTRUMENTATION_CODE=0 and ANDROID_PROGRESSIVE_BACK=PASS_REAL_KEYS_AND_BACKDROP_NO_SEND.
- Initial device attempt FAILED before WebView was available. The test itself
  sent Back while the launch window was waiting for focus. Corrected to dismiss
  only a verified external Android Settings window; subsequent device run PASS.
- Current Android dictation routing probe with both capture paths stubbed PASS:
  native PCM start=1, browser capture=0, AAC attachment=0, INSTRUMENTATION_CODE=0.
  This is routing evidence, not a fresh spoken-word/transcript/send acceptance.
- Fresh local regression PASS: Java plus PCM fault tests; Web selftest and actual
  patched-drawer behavior; 35 Python Web tests; modules core 17, patches 14,
  io 13, env 25, builder 8. Real isolated Core PTY two generations PASS:
  create, tty stdin/stdout, interactive read/input, stream reconnect, close/reopen.
- Dev APK compiled locally (238 Java classes), verified and installed with Shizuku
  directly into com.agy.dev. Production untouched. This is a public-debug-key QA
  build with internal dev version metadata, not an unsigned KR release artifact.

Still open: native PCM network/pre-session bounds review, fresh full voice
speech/edit/send acceptance, attachment metadata/delete-target CSS review and
real model/picker sends, visual settings Light/Dark/presets, real agent background
and screen-off notifications, long-chat fling/TalkBack, Pixel 7 Android 17 and
Android 8/12 availability. Existing unsigned release hashes remain stale.
No final release acceptance or release APK handoff is claimed by these milestones.

QA APK SHA-256: e35f732ccac46ee3bfd3df00ac948d73404d7610a7038ecdab7c6519d1b1b45e
