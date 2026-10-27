# Dictation restart audit — 2026-10-27

Release remains on hold. Work is only in dev. The owner reproduced an intermittent
UnknownError after roughly three record/stop cycles; old unsigned candidates
built from 03b5cf6 are not approved for signing.

## Findings reproduced before fixes

- Java: the worker atomically claimed close before AudioRecord.release finished.
  Concurrent stop returned early. A latch-controlled test failed with
  'stop must wait for concurrent hardware release completion'.
- Actual transformed upstream dO hook: cancellation before Core ready reported
  an error; a restart ignored pending endAudioSession; an old final transcript
  could enter a replacement generation.
- The outer formatter hid bounded native error strings as UnknownError.
- Existing tests covered adapter wiring and single capture, not repeated dO/Core
  lifecycle. Android mic-pcm-ui substitutes a recorder error; it is not a real
  microphone or transcription success test.

The exact cause of the owner's earlier UnknownError cannot be proven retrospectively
without its error code. These independently reproduced defects match the retry
scenario and now have regression coverage.

## Changes

09a60a2 serializes the complete native release operation and passed Java lifecycle
tests before push. Follow-up source work guards generation callbacks, aborts
canceled ready waits, coalesces Stop, preserves the old final transcript and waits
for both end acknowledgement and finalization before reopening capture. The
existing 3-second finalization fallback is generation-bound; the acknowledgement
wait fails explicitly after 5 seconds. Errors expose only bounded codes, never
raw RPC messages, transcript, audio, account data or paths.

A monotonic 3:30 countdown and a native 210-second cap protect both ordinary and
continuous dictation. Auto-stop finalizes via the original Core RPC. PCM format,
sample rate, streaming-versus-attachment selection and transcript/editor handling
are preserved. Audio attachment recording is a separate original mode.

## Validation

- Native release regression: failed before fix, passed after 09a60a2.
- Web Python suite: 39 tests passed.
- Java regression suite passed, including exact native 210-second boundary.
- Generated Web selftest passed, including scroll, Back, settings, attachments,
  notifications and voice.
- Extended actual dO behavior harness uses React dependencies/effect cleanup and
  controlled Core streams: eleven cases, including ten repeated cycles,
  cancellation, delayed ack, final text, stale responses, double Stop, timeout,
  native limit and countdown. Final follow-up rerun recorded separately below.
- Real hardware probe added: twelve immediate AudioRecord restart cycles,
  locally discarded PCM, no Core transmission or draft mutation.
- Pending: install rebuilt Dev APK, run the real hardware probe, owner speech
  recognition and repeated dictation acceptance. Compilation is not this proof.
