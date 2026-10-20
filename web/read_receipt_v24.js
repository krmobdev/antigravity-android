/**
 * v2.4 read receipt reconciliation for a finished response in an already-open
 * ordinary conversation. Unlike upstream's route/focus-only middleware, this
 * reacts to the authoritative trajectory summary update.
 *
 * Called after the reducer committed a Core stream batch. No native bridge.
 * Does not mark other conversations, a running agent, or a hidden/locked chat.
 */
function agyV24ReadReceipt(store, action, selectedId, focused, deps, journal) {
  if (!selectedId || !focused || !rm.match(action) ||
      typeof document === "undefined" || document.visibilityState !== "visible" ||
      !document.hasFocus() || !deps?.updateConversationAnnotations) return;
  const updated = action.payload?.updates;
  if (!Array.isArray(updated) || !updated.some(entry => Array.isArray(entry) && entry[0] === selectedId)) return;
  const state = store.getState();
  const summary = state.trajectorySummaries?.summaries?.[selectedId];
  if (!summary || summary.notFullyIdle || summary.waitingSteps?.length ||
      summary.status === T.CascadeRunStatus.RUNNING ||
      summary.status === T.CascadeRunStatus.BUSY) return;
  const modified = summary.lastModifiedTime;
  const stamp = Number(modified?.seconds || 0) + Number(modified?.nanos || 0)/1e9;
  const viewed = Math.max(
      Number(state.conversation?.localLastViewedTimes?.[selectedId] || 0),
      Number(summary.annotations?.lastUserViewTime?.seconds || 0) +
          Number(summary.annotations?.lastUserViewTime?.nanos || 0)/1e9);
  if (!(stamp > viewed)) return;
  const key = selectedId + ":" + modified.seconds + ":" + (modified.nanos || 0);
  if (journal.seen.has(key) || journal.scheduled.has(key)) return;
  journal.scheduled.add(key);
  // After Redux and React have rendered the fresh Core snapshot, and only if
  // the exact conversation is still visibly on screen.
  requestAnimationFrame(() => {
    journal.scheduled.delete(key);
    if (document.visibilityState !== "visible" || !document.hasFocus()) return;
    const current = store.getState();
    if (current.conversation?.convoState?.type !== "active" ||
        current.conversation.convoState.cascadeId !== selectedId) return;
    const latest = current.trajectorySummaries?.summaries?.[selectedId];
    if (!latest || latest.notFullyIdle || latest.waitingSteps?.length ||
        latest.status === T.CascadeRunStatus.RUNNING ||
        latest.status === T.CascadeRunStatus.BUSY) return;
    if (Number(latest.lastModifiedTime?.seconds || 0) !== Number(modified.seconds) ||
        Number(latest.lastModifiedTime?.nanos || 0) !== Number(modified.nanos || 0)) return;
    const displayed = Array.from(document.querySelectorAll('[data-testid="conversation-view"]'))
      .find(el => el.dataset.cascadeId === selectedId && el.isConnected);
    if (!displayed) return;
    const rect = displayed.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0 ||
        rect.bottom <= 0 || rect.top >= window.innerHeight) return;
    // Do not pretend that a completely empty conversation shell was seen.
    if (!displayed.querySelector('[data-testid], .prose, [class*="message"], [class*="markdown"]')) return;
    const nowViewed = Number(current.conversation?.localLastViewedTimes?.[selectedId] || 0);
    if (nowViewed >= stamp) return;
    journal.seen.add(key);
    if (journal.seen.size > 128) journal.seen.delete(journal.seen.values().next().value);
    store.dispatch(Op.actions.markViewedLocally({cascadeId:selectedId}));
    store.dispatch(Cpb({cascadeId:selectedId,
      agentService:{updateConversationAnnotations:deps.updateConversationAnnotations}}));
  });
}
