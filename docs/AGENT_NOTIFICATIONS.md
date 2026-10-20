# Native agent completion notifications

This change is based on PR #15 (`9fcb1b1`). The resume fix remains a separate
commit; this follow-up adds completion delivery while the WebView is suspended.

A foreground-service-owned, read-only ConnectRPC subscriber observes known
running conversations. Only IDLE **and** fullyIdle completes a tracked task.
A partial IDLE, duplicate snapshot, or old UI RUNNING snapshot must not generate
another completion. The client pins the bundled Core certificate and connects
only to its loopback HTTPS endpoint. It does not start agents or cancel work.

Completion policy:

* The foreground, focused Activity's committed conversation receives a shade
  notification through an IMPORTANCE_DEFAULT channel, without requesting a
  heads-up banner. Sound remains controlled by Android and the user's channel
  settings.
* Another conversation, the list, or a background app receives the existing
  IMPORTANCE_HIGH completion channel. Android/DND/user settings ultimately
  decide whether a banner or sound appears.
* Alerting completions are FIFO, at least five seconds apart. Context is checked
  at delivery. Opening a queued conversation delivers its completion through
  the non-banner channel immediately. Starting a new task cancels an older
  queued completion for that conversation.
* The bounded pending queue and remaining alert cooldown survive process
  recreation. At capacity, the oldest completion is preserved in the shade
  rather than producing an unbounded banner backlog.

The React route reporter runs in useLayoutEffect, after commit, and is bound to
its own Activity/WebView bridge. A hidden older WebView cannot mark the current
Activity's conversation visible. Notification PendingIntents contain validated,
exact conversation IDs; navigation waits for the client router to be ready.

Native completion also invalidates the sidebar proof store. A retained page
rechecks that store on host resume and renews its summary observation stream.
Fresh summary snapshots replace old cached summaries; callbacks from an older
stream generation are rejected. These changes prevent a completed task from
retaining the spinner/background dots after notification navigation. Genuine
RUNNING and starting states retain their existing rendering rules.

## Validation

Run browser/patch checks:

```
python3 -m unittest discover -s web/tests -p 'test_*.py'
bash web/tests/test_staged_web_assets.sh
```

Run Android-free native checks with a JDK:

```
mkdir -p app/tests/out
javac -d app/tests/out app/src/main/java/com/agy/bridge/AgentTaskTracker.java app/src/main/java/com/agy/bridge/AgentCompletionQueue.java app/src/main/java/com/agy/core/ConnectFrameReader.java app/tests/*.java
java -cp app/tests/out AgentTaskTrackerTest
java -cp app/tests/out com.agy.bridge.AgentCompletionQueueTest
java -cp app/tests/out ConnectFrameReaderTest
```

On an authorized Android device, build `bash build.sh --dev`, then build the
separate `bash tests/notifications/build.sh` instrumentation APK. Install only
`com.agy.dev` and the test APK with the matching debug signature. Run:

```
am instrument -w -r com.agy.notificationtests/com.agy.notificationtests.NotificationInstrumentation
```

Instrumentation exercises real NotificationManager delivery, SystemUI banners,
a real SystemUI tap and its PendingIntent, navigation, five-second spacing and changed chat
context using controlled native transitions in two existing conversations. It
sends no prompts and does not claim to test a real Core task. The report is saved
inside the Dev app at `files/logs/notification-policy-results.txt`. Disable DND
and permit heads-up for the alert channel to validate actual banners; do not
silently reconfigure the user's channels to make a test pass.

The UI test taps the visible sidebar link and SystemUI notification using actual
input events. A JavaScript `element.click()` is unsuitable here: on this device
it created a history entry while WebView.canGoBack() remained false. Physical
taps produced normal Back history. The test rejects a return that replaces the
original Activity or WebView. SystemUI on this device exposes the banner title
without a heads-up ancestor resource ID, so detection also checks its visible
position in the top third of the unlocked screen.
