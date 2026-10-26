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
