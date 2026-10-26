# v2.4.5-kr1 preparation — in progress

All commands run on the owner's Android in Termux via AnimaBridge.
No cloud builds, CI, signing, release tags or publication.

## Starting point

- Authoritative origin: https://github.com/krmobdev/antigravity-android.git
- Remote default branch: main.
- Fetched main and ls-remote both confirmed
  1cc88bd40667f60e819c5ca415dc7cd69294632d; local divergence 0/0.
- Working branch: feature/v2.4.5-kr1, explicitly requested by owner.
- Pre-existing uncommitted web/generated/main.js preserved; SHA-256:
  4345a61da96e68aa6b0f1e358f0e2109c5d3dca2bc79feabda629e7ced553ebb.
  It is excluded from preparation commits. Web build must not overwrite it.
- Stable v2.4.1-kr1 sources remain available unchanged in main/history;
  stable APKs, tags and history must not be modified.

## PTY source adaptation

Derived from upstream 67670cffdec8f31adfa7036dc561c97e30e3a06a.
The local pristine Core is 181358800 bytes, SHA-256
3971986e8bad5a26f087fc2c81ad28bd6cf19bee4492df98f3f7e0e4f2dc224d.
It contains exactly one /bin/bash at 0x428fe3f, in Go's packed string table
between send-pack and --config=. The patch pins this context and the total
clean/replacement count. Missing or ambiguous signatures fail closed even in
legacy allow-missing mode. No fixed write offset is used.

On the attached device /bin resolves to /system/bin; /bin///sh resolves to
executable /system/bin/sh. This path assumption needs confirmation on other
supported devices, including Pixel 7.

Shell environment prefers executable prefix bash, runtime bash, then system sh.
TERM is xterm-256color; readable prefix terminfo is used when present and an
inherited foreign TERMINFO is removed. Missing executable shells fail explicitly.

Runtime hashes were independently reproduced locally, not merely copied:
- bypass: f25135e4b4a2c4a8e4cb319dc36b13eb98fa4718fa80aaae5a7819935370ff27
- standard: abf5881f9983fbad00c1cdf903c81f84cf36b8a21364b3476f6d19568b528527

aapt version works without ANDROID_DATA on this phone, but upstream's conservative
default /data is retained in both aapt entrypoints for stripped Android shells;
an explicitly configured value remains unchanged.

## Actual checks

Baseline and adapted tests both ran locally:
- bash app/tests/run.sh: PASS, including completion FIFO/persistence/cancellation,
  tracker, Connect framing, auth, and 13 shell-policy assertions.
- bash modules/patches/tests/run_all.sh: PASS (14 tests after adaptation).
  Includes real-Core apply/verify/reapply, both editions' hashes, byte size,
  dry-run, bad/multiple/missing/path-context signatures and unchanged-on-failure.
- git diff --check: PASS.
- Existing generated/main.js hash: unchanged.

Real isolated Core RPC smoke PASS on the attached Realme RMX3301:
python modules/runner/test_pty_rpc.py (exit 0). Two terminal generations verified
CreateTerminal, TTY stdin/stdout, interactive read and input, output stream
reconnection, ListTerminals retention, CloseTerminal and recreation. The test
uses temporary HOME/runtime, a random port and packaged TLS certificate; no
production data, APK installation or model requests.

PTY UI and Pixel 7 acceptance remain PENDING.

## Remaining work

1. Real isolated Core PTY smoke and Android UI acceptance.
2. Native Core stage schema audit, notifications implementation and tests.
3. Attachment/API audit and manager implementation/tests.
4. Mobile composer implementation/tests, preserving all settings and scroll fix.
5. Full regression and sequential standard/bypass unsigned KR builds.
6. Version metadata, APK manifests, security checks, SHA-256, bilingual release
   notes/changelog and owner signing handoff.
7. Pixel 7 and any device/user-only acceptance must be listed as pending until
   actually performed.

No release-ready claim is made by this checkpoint.

## Native progress implementation checkpoint

Protocol source: bundled protobuf descriptors in web/main.js (Core client).
AgentStateUpdate.mainTrajectoryUpdate contains lastStepType, stepsUpdate.totalLength,
lastStepError and killed; status CANCELING is value 3. The stage reducer consumes
only these typed values, never descriptions/arguments/commands/file contents.
There is no global event sequence number in this schema. Lower step counts,
equal-index type conflicts, retired trajectories and finished-run RUNNING
replays are filtered conservatively. Per-conversation watermarks survive
native stream reconnects in-process; cold start waits for fresh Core data.

Notifications retain their task tag/ID and coalesce rapid observed stages.
Terminal outcomes persist with existing completion queue entries; UI-first
completion can be enriched silently without a duplicate FIFO alert. Native
subscriptions continue through UI-first IDLE until Core confirms completion.

Automated reducer/coalescer/tracker/FIFO tests and complete Android source
compilation ran locally. Real background/screen-off notifications, native/UI
race acceptance and Pixel 7 checks remain PENDING until separately recorded.

## Attachment implementation checkpoint

Audit confirmed existing multiple selection, max-media-attachments default 5,
format/size validation, Core saveMediaAsArtifact/deleteMediaArtifact and send
error handling. Ten files are not proven compatible; cap 5 is retained and a
lower server flag remains effective. No server acceptance is fabricated.

Added consistent 200x96 cards, explicit remove, raster image/fullscreen viewing,
safe UTF-8 plain-text/code/Markdown/JSON preview up to 1 MiB, and readable format
limitations with existing openFile fallback where supported. Audio/video
preview uses the original payload. Object URLs are cleaned on all viewer exits.

Picker batch reservations prevent concurrent additions exceeding the cap;
same-file re-selection works. Chat picker no longer persists blanket grants;
project tree permissions are unchanged. Builder stages web from a disposable
snapshot to preserve the initial uncommitted generated/main.js exactly.

Source-level behaviour tests passed for viewing vs removal, unsafe text,
binary/size limits, object URL cleanup/late reads, concurrent five-file cap,
cancelled selection, failed read and repeated selection. Web selftest and
complete Java compilation passed. Android File Picker, sending against the
account's model, background return and visual theme checks remain PENDING.
