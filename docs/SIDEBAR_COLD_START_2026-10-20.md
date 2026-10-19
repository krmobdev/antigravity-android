# Sidebar spinner on cold-open: confirmed diagnosis and candidate fix

Date: 2026-10-20 (device local UTC+03:00).

## Reproduction

No new task was started. On reopening Antigravity Dev at 00:22,
the completed chat showed a rotating spinner. Opening that chat and
returning to the conversation list changed the indicator to a timestamp.

This was captured by screenshots at 00:22:40 and 00:22:50 and by
private `com.agy.dev/files/logs/agy-diag.log` inspected using Shizuku
`run-as`. Relevant log transitions (converted from Unix epoch ms):

- **00:22:31.847:** activity onDestroy during prior lifecycle.
- **00:22:31.876:** activity onResume.
- **00:22:43.815:** sidebar selected=1, summary=1, agent=0, starting=0.
- **00:22:44.235:** reconcile_idle for the same conversation ID.
- **00:22:44.237:** agent_state status=1, fullyIdle=true.
- **00:22:47.090:** sidebar selected=0, summary=1, agent=0, starting=0.

## Root causes in bundled sidebar code

1. `VW(a)` used `(selected ? localAgentState : backendNotFullyIdle) || starting`.
   Our previous reconciliation incorrectly guarded only the unselected
   backend summary branch (`selected ? e : c&&!verifiedIdle`); if the
   selected state remained stale, the spinner stayed even after verified IDLE.
2. `XW(a)` separately used `yT(a)==="running"` as a second `isLoading`
   input; it did not respect verified idle at all.

## Candidate change

- Reconcile the selected and unselected branches against confirmed
  `IDLE + fullyIdle`, retaining the independent `starting` flag.
- Reconcile the secondary `yT(a)==="running"` indicator as well.
- On a subsequent actual `RUNNING/BUSY` event, the stored verified-idle
  flag clears and normal activity indicators return.

The patch affects only the staged web bundle; pristine upstream main.js
is kept intact. The upstream minified anchors are checked and the build
fails closed if they change. 12 Python/Node web tests plus staged asset
validation passed. Dev APK installed via Shizuku with Android `Success`;
official `com.agy` untouched.

**Remaining limitation:** the fix requires an agent IDLE event after
(re)subscription, as demonstrated at 00:22:44. If no state event arrives
at all after cold start, an older stale backend summary may still be
displayed until a subscription updates it. This must be tested separately.
The on-device fix is a candidate until another real cold-open test passes.
