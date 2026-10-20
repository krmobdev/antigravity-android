# Read/unread research — deferred after v2.4 integration

Decision 2026-10-20: Do **not** introduce a new read-receipt implementation in the v2.4.0 integration. Existing upstream behavior is retained. Commit `432228d` was intentionally reverted in `f33e724`, without changing history. Do not re-enable `AGY_V24_VISIBLE_READ_RECEIPT`.

## Observed behavior (test app only)
- A reply completed while its conversation was already open and reportedly visible. On the main screen, its blue dot persisted.
- A second conversation completed while the test was backgrounded; opening from the Android notification and later returning to the main screen was followed by the second conversation's blue dot disappearing.
- No user confirmation exists that the first conversation's dot disappeared after its subsequent reopen. Do not infer it.

## v2.4 upstream data and code
- Blue unread indicator is not the running agent indicator. The unread calculation derives from `trajectorySummaries` and `annotations.markedAsUnread`; when not manually marked unread, the comparison is `summary.lastModifiedTime` versus server `annotations.lastUserViewTime` and Redux `conversation.localLastViewedTimes`.
- `web/main.js` upstream `FDa` conversationViewing middleware sends `updateConversationAnnotations({lastUserViewTime})` on route changes and focus restoration, rather than each arrived response. `Cpb` thunk also persists manual mark-as-read using Core API. A special battle-conversation effect handles some fresh modifications while opened.
- These facts explain a plausible missed update when the answer appears without a new route/focus event. They do **not** establish a future product specification.
- Visibility of an open chat alone is **not sufficient evidence of an actual human reading the reply**. A future design must define explicit user acknowledgement interactions, lock-screen lifecycle, UI fully rendered, server round trip, manual unread override, multi-chat states, and restart consistency.

## Deferred tests
The postponed proof-of-concept had behavioral tests of eight scenarios; they belonged to the reverted implementation and intentionally no longer run. Future acceptance should test source-of-truth Core annotations and real Android user acknowledgement, not just disappearance of the CSS blue dot.

## Current integration scope
Only Native Notifications, Hca resume and NDa/sidebar recovery. Do not change `NativeAgentStateObserver`, unread Core annotations, Redux read state or UI badges as part of this release.
