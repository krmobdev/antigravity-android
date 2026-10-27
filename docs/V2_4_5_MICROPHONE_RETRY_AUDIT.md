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
- Rebuilt and installed Dev APK through Shizuku, package com.agy.dev, from
  source 1e83b60daf7b4e971b778de1aeb7ac1b8094406c. SHA-256:
  a730d0a904de35a7f2d0430efb5f28d20b4503ff0349ed772d6b01ba6449195d.
- Final five native/STT Python tests passed after the final-text gate adjustment,
  including all eleven actual-hook cases with React effect cleanup.
- Real device hardware probe PASSED: each of twelve immediate starts received
  PCM and completed double Stop; all individual flags PASS,
  INSTRUMENTATION_CODE: 0. Current v7 Web asset was verified on device.
- Pending: owner speech recognition/repeated-dictation and visible countdown
  acceptance in the updated Dev APK. Hardware lifecycle is not Core speech
  recognition, nor a real 3:30 dictation end-to-end test.


Source fixes were pushed separately: 09a60a2 (native release completion barrier)
and 1e83b60 (client ownership, Core drain, 210-second cap and tests).
The old unsigned APKs remain preserved, marked on hold in release documents
and Download/DO-NOT-SIGN-v2.4.5-kr1.txt. No production package was installed or
removed, no main/tag/release changed, and no release signing key was accessed.


## v8 follow-up: microphone starts but field remains empty

Owner acceptance of v7 FAILED: after Stop, processing sometimes ended with an
empty field and no error. Release hold remains active.

The six new behavior cases were rerun against the unchanged 4879828/v7 source,
including its own JS helper and stable controllable RPC mocks: all six failed.
The former three-second finalization timeout discarded a valid four-second
response. Stop before ready canceled buffered speech. endAudioSession did not
wait for sendAudioChunk acknowledgement, send failures only reached console,
empty completion looked successful, and queued PCM between polls was discarded.

v8 removes the silent cutoff, retains Stop-before-ready audio, drains native tail,
waits for chunk acknowledgement and browser worklet completion, and distinguishes
Cancel from Stop. Empty/missing/error results show explicit English errors.
Recording remains limited to 210 seconds. Processing alone may take up to
30 seconds with bounded 20-second ready/chunk/end waits. No raw audio, transcript
or arbitrary RPC message is logged.

Validation and new Dev artifact/device results are recorded below after completion.
Prior v7 hardware success proves capture only and does not approve v8 transcription.

v8 regression checkpoint: 39 Python tests, Java suite and generated Web selftest
passed. The actual streaming hook plus delivery/decoder helpers pass 23 behavior
cases. The six empty-result cases fail against unchanged v7 and pass against v8;
browser worklet tail ordering was independently reproduced before its fix.
Rebuilt device/real-transcription acceptance is still pending at this checkpoint.
