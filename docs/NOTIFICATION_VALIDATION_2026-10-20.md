# Device validation — 2026-10-20

Base: PR #15, commit `9fcb1b180548bfc7144b8f0269e7f7bfa7cdf532`.
Target: `com.agy.dev`; the official `com.agy` installation was not changed.

Final Shizuku instrumentation result:

```
RESULT passed=true checks=25 controlledNativeTransitions=true realAgentRequests=0
INSTRUMENTATION_CODE: -1
RETURN originalActivity=true originalWebView=true
```

The test used real physical input events in the sidebar and SystemUI. It exited
the chat to the list before Home, waited for a controlled native completion,
tapped the actual heads-up notification, and returned using Android Back.
The original Activity and WebView were retained; replacement is a test failure.

Verified on screen and through NotificationManager:

* The open foreground chat receives the DEFAULT channel and no heads-up banner.
* Another chat and a background app receive the HIGH channel and actual SystemUI
  heads-up banners. The device was unlocked and interactive, with DND off.
* The notification tap opens the matching conversation, then Back returns to the
  retained list.
* The second queued completion is initially absent and later appears; actual
  Android notification post times are separated by the five-second interval.
* Opening a queued conversation changes its completion to the non-banner channel.

The UI test controls native transitions. It sends no agent prompts and does not
claim live Core execution or another real-agent duration regression. Native
observer Core execution was not re-run for these notification policy checks.

Additional checks passed:

* 17 Python/Node web patch and behavior tests.
* APK web staging, JavaScript syntax, and preservation of upstream web/main.js.
* AgentTaskTracker: 13 assertions; ConnectFrameReader: 10 assertions.
* Deterministic queue scenarios: FIFO spacing, changed visibility, cancellation,
  duplicate entries, restored queue/cooldown, bounded overflow without loss.
* Dev APK build and signature verification.

Tested Dev APK SHA-256:
`2092a5d3de39c5e6c4bc32e4d3d6ecc7875688f256521e27c7cd04654258aa18`

Final instrumentation APK SHA-256:
`721751fb914c88afb4a5f19c56b90dfeeae612e6684824a5766fb52d07bf5015`

Raw phone job: `b82001000000000000000000000000b6`. Its stdout and stderr must
both be read: this device splits instrumentation status records between them.
Raw logs remain outside the public source tree; they contain conversation IDs.
