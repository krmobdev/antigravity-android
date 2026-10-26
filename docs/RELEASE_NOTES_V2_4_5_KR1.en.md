# Antigravity Android v2.4.5-kr1

Candidate for owner acceptance. No release signing or publication.

- PTY: executable shell fallback, TERM/TERMINFO and the upstream 67670cf
  binary patch with strict compatibility checks. Real Core terminal creation,
  interactive input, output reconnect and recreation were tested on Android.
- Notifications: per-conversation confirmed stages, coalesced updates,
  fixed public labels and error/cancellation outcomes. Real lock-screen
  scenarios still require acceptance.
- Attachments: consistent cards, explicit removal, image and safe plain-text
  previews, repeated selection and temporary resource cleanup.
  Maximum 5; support for 10 is not claimed without Core/API proof.
- Composer: spacious multiline input, 48 dp touch targets and responsive
  + action sheet, retaining model/reasoning/voice/send/stop controls.
- The v2.4.1 native scrolling fix is retained with regression coverage.
- Package `com.krmobdev.antigravity`, versionCode `19`.

Standard and region-bypass unsigned editions are prepared for later manual
signing with the owner's existing key. Pixel 7, production update and pending
acceptance scenarios are listed in the [handoff](HANDOFF_V2_4_5_KR1.md) and
[status](V2_4_5_KR1_STATUS.md).
