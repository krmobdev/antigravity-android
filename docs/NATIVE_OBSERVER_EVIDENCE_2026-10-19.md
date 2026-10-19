# Antigravity Dev native observer — first independent completion evidence

**Test:** 2026-10-19, user-local timezone UTC+03, `com.agy.dev`
(package last updated at 23:27:09). Source: the app-private
`files/logs/agy-diag.log` accessed through authenticated Shizuku
`run-as com.agy.dev`. The screenshots at 23:29:39, 23:29:44 and
23:29:48 independently corroborate the user-visible result.

| Time | Diagnostic event |
|---|---|
| 23:28:00.109 | WebView reports new RUNNING conversation |
| 23:28:00.134 | Native service subscribes to that conversation |
| 23:28:00.203 | **Native Core state RUNNING** (2, false) |
| 23:28:11.386 | Activity onStop (background) |
| 23:29:18.564 | Activity onResume (user briefly foregrounded) |
| 23:29:36.626 | Activity onStop again |
| 23:29:37.789 | **Native Core state IDLE, fullyIdle=true** |
| 23:29:37.794 | **Android FINISHED notification** |
| 23:29:37.805 | Race: stale UI RUNNING caused a false second STARTED |
| 23:29:37.821 | Race: a duplicate FINISHED was emitted |
| 23:29:41.791 | Activity onCreate from tap on notification |
| 23:29:43.193 | Deep link navigation accepted |
| 23:29:48 | Sidebar shows finished conversation with normal timestamp |

**Verified:** The native stream stayed alive while the Activity was
backgrounded and delivered a FINISHED transition **before the next app
opening**. This proves the basic background path can work. This test
does not prove reliable completion during prolonged suspension or with the
screen locked; the Activity was foregrounded shortly before completion.

**Regression found:** Race between native Core's IDLE and a queued
WebView RUNNING/IDLE update caused a 27ms STARTED/FINISHED pair. The
candidate fix adds a four-second guard for stale *UI-originated* RUNNING
events after a completion; an authoritative *native* RUNNING event is
permitted to start a genuine new task immediately. The service also
refuses to resurrect a watcher from a stale UI RUNNING update for four
seconds following native completion, and suppresses spurious
`SocketException` logging from intentional cancellation.

**Status:** candidate code was regression-tested and built but is NOT
installed by this change. Do not claim production-ready notifications
until a device run confirms correct deduplication, including back-to-back
tasks, prolonged background operation and screen lock.
