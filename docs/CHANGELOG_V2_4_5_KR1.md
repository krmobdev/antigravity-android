> RELEASE HOLD — 2026-10-27: repeated dictation exposed a microphone lifecycle
> regression. The unsigned APKs listed here were built from 03b5cf6 and are
> superseded by source fixes. Do not sign or publish them. New device acceptance
> and freshly rebuilt candidates are required.

# Changelog — v2.4.5-kr1

Baseline: v2.4.1-kr1 / main 1cc88bd40667f60e819c5ca415dc7cd69294632d.
Stable main, tags, history and APKs remain unchanged.

- fix(pty): strict upstream-derived shell patch, executable shell selection,
  TERM/TERMINFO, verified Core cache and local Android build environment handling.
- test: isolated real Core PTY create/stdin/stdout/read/reconnect/close/recreate;
  both Standard and Region-bypass editions supported by the same test CLI.
- feat(notifications): native Core stages per task, stale/duplicate protection,
  quiet progress channel, separate terminal outcomes and existing completion FIFO.
- feat(attachments): real Core cap <=5, preview cards, safe UTF-8 content,
  distinct view/delete, repeat/multiple picker handling and lifecycle cleanup.
- feat(composer): compact mobile Composer, adaptive Add menu, model/effort,
  Core-backed permission shield, IME layout and retained Send/Stop/queue behavior.
- feat(composer): one-level Back and backdrop/Escape behavior for nested sheets;
  responsive portrait Desktop Mode and centered model control.
- feat(composer): shared themed controls, readable settings switches, themed
  selects, Light/Dark/custom presets and retained native scroll behavior.
- feat(composer): original Core speech-to-text with native 16kHz PCM capture;
  bounded native capture queue and ownership/error/background cleanup.
- feat(composer): resource cache freshness after APK update without auth/storage
  deletion; real native startup stages, healthy-Core UI Retry and painted-frame
  handoff.
- feat(composer): block transient startup theme writes to Android native bars/
  preferences until compositor confirmation; preserve later theme changes.
- fix(attachments): overlay-only dismissal preserves the current chat and real Picker delivery; uniform square tiles, compact type labels and 48px removal targets.
- fix(notifications): enabled live launcher target, same-chat preservation, replacement navigation and in-flight duplicate/stale guards. Legacy direct entry remains an unresolved probe.
- feat(composer): English app-owned UI/error/notification labels and regression policy.
- test: genuine DocumentsUI multi-select/repeat/cancel/preview and computed card geometry; posted notification round-trip and one Back Home; strict Android-result parser.
- Release preparation: com.krmobdev.antigravity, 2.4.5-kr1 / versionCode 19,
  unsigned aligned artifacts and owner-only interactive signing.
- docs: current bilingual candidate notes, checksums and a requirement/evidence
  matrix. Unverified live/manual scenarios are not marked as passed.

No new read/unread model, release signing, main integration, tag, GitHub Release
or cloud CI execution is implied by this changelog.

### Исправление пропадающего результата диктовки

Stop сохраняет короткую запись до готовности Core и ожидает доставку
аудиофрагментов перед завершением сессии. Убран молчаливый обрыв ожидания
через три секунды. Пустой ответ, сбой доставки и тайм-аут показываются
явно на английском. Лимит записи остаётся 3:30. Добавлена проверка настоящего
Core с появлением распознанной синтетической фразы в настоящем Composer.
