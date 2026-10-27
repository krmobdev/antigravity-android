> RELEASE HOLD — 2026-10-27: repeated dictation exposed a microphone lifecycle
> regression. The unsigned APKs listed here were built from 03b5cf6 and are
> superseded by source fixes. Do not sign or publish them. New device acceptance
> and freshly rebuilt candidates are required.

# Antigravity Android v2.4.5-kr1

> Fresh unsigned owner-signing candidates were rebuilt from dev 03b5cf6. Attachments, English UI and current notification shade/Back entry are accepted; the separate legacy notification probe remains unresolved. Publication requires a separate owner instruction.
Draft notes for a future release after owner acceptance and manual signing.
This document does not mean the release has been published or fully accepted.

Changes since v2.4.1-kr1:

- PTY: Android/Termux shell fallback, TERM/TERMINFO handling and the upstream-derived
  binary patch with compatibility, size, signature-count and idempotence checks.
- Agent stages: the last confirmed stage per conversation, silent updates of
  the same notification, completion/error/cancellation handling and safe fixed
  public labels. Unknown stages retain a neutral fallback.
- Attachments: multiple selection up to the actual Core limit, capped at five;
  preview cards, separate viewing/removal, safe text preview and temporary-resource
  cleanup.
- Unified mobile Composer and menus: compact model/effort controls, real permission
  settings behind the shield, adaptive + menu, Send/Stop, IME support, Light/Dark
  and custom presets.
- Progressive Back: nested menus return one level; root Back minimizes the app
  while Core keeps running.
- Responsive portrait Desktop Mode, restored settings/Back interaction and a
  model button centered between the shield and microphone.
- Original Core speech-to-text retained, using native Android capture to work
  around WebView limitations, with lifecycle/error cleanup.
- WebView resource updates preserve accounts and web storage.
- Native startup milestones and Retry; transient Core theme hydration no longer
  repaints Android system bars.
- The v2.4.1-kr1 native scrolling correction is retained.

- All app-owned UI labels are English; conversation text and file names remain unchanged.
- Add/preview overlays retain chat history; photos and documents use equal square cards with separate 48px removal targets.
- Current notifications reuse the live Activity/WebView and preserve drafts; Back does not expose a duplicate previous chat window.

Package: com.krmobdev.antigravity. VersionName: 2.4.5-kr1.
VersionCode: 19, up from stable versionCode 18.

Editions: Standard preserves the original local Core access gates; Region-bypass
changes those local gates. Both share the same package/signing identity and
cannot be installed side by side.

Current acceptance reports distinguish local automated checks, isolated live
PTY RPC and Android UI tests from remaining manual scenarios. Live agents with
the screen off, different attachment sends to a model, terminal UI, accessibility/
long chats and the signed production update still require separate confirmation.
Google Pixel 7 testing was explicitly excluded by the owner.
