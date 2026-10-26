# Changelog — v2.4.5-kr1

Baseline: v2.4.1-kr1 / main 1cc88bd. Stable main/tags/APKs remain untouched.

- fix(pty): upstream-derived strict PTY patch, shell fallback and terminal environment.
- test: real isolated Core PTY RPC lifecycle on Android.
- feat(notifications): native per-task stage/outcome reducer and coalescing;
  preserve completion FIFO, navigation and independent read/unread.
- feat(attachments): fixed-size previews, safe text, explicit removal,
  repeat/multiple picker handling, transient grants and resource cleanup.
- feat(composer): mobile multiline layout and supported-action bottom sheet;
  original model/reasoning/voice/send/stop logic retained.
- build preparation: versionCode 19, new unsigned output names and isolated web
  staging that preserves pre-existing checkout changes.
- docs: bilingual candidate notes, SHA-256 handoff and owner-only signing steps.

This changelog describes candidate source changes. Actual test results and
unverified device scenarios are recorded separately in V2_4_5_KR1_STATUS.md.
