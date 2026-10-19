/**
 * Native task status bridge. Invoked from decoded StreamAgentStateUpdates,
 * not via DOM text scraping or a timer looking for "Working...".
 */
(function () {
  function compactStage(state) {
    try {
      var steps = state.trajectorySlice && state.trajectorySlice.stepsInSlice;
      var last = steps && steps.length ? steps[steps.length - 1] : null;
      var step = last && last.step;
      var metadata = step && step.metadata;
      var value = metadata && (metadata.toolSummary || metadata.description);
      if (typeof value === 'string' && value.trim()) {
        return value.replace(/\s+/g, ' ').slice(0, 120);
      }
    } catch (_) {}
    return 'Агент выполняет задачу';
  }

  window.__AGY_NOTIFY_AGENT_STATE__ = function (state) {
    try {
      var bridge = window.Android;
      if (!bridge || typeof bridge.onAgentState !== 'function') return;
      if (!state || typeof state.conversationId !== 'string') return;
      // CascadeRunStatus.IDLE=1, RUNNING=2, BUSY=4.
      if (state.status !== 1 && state.status !== 2 && state.status !== 4) return;
      bridge.onAgentState(
        state.conversationId, state.status, state.fullyIdle === true, compactStage(state));
    } catch (_) {
      // Never interrupt core agent state updates because notifications failed.
    }
  };
})();
