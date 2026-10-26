/**
 * Native task status bridge. Invoked from decoded StreamAgentStateUpdates,
 * not via DOM text scraping or a timer looking for "Working...".
 */
(function () {
  function compactStage() {
    // Public stages come from the native Core enum reducer. Never forward
    // potentially private toolSummary/description from the WebView.
    return 'Агент выполняет задачу';
  }

  window.__AGY_NOTIFY_SUMMARIES__ = function (updates) {
    try {
      var bridge = window.Android;
      if (!bridge || typeof bridge.onConversationSummary !== 'function') return;
      for (var pair of updates || []) {
        var id = pair[0], summary = pair[1];
        if (typeof id === 'string' && summary && typeof summary.summary === 'string') {
          bridge.onConversationSummary(window.__AG_NATIVE_BRIDGE_TOKEN__, id, summary.summary);
        }
      }
    } catch (_) {}
  };

  window.__AGY_NOTIFY_AGENT_STATE__ = function (state) {
    try {
      var bridge = window.Android;
      if (!bridge || typeof bridge.onAgentState !== 'function') return;
      if (!state || typeof state.conversationId !== 'string') return;
      // CascadeRunStatus.IDLE=1, RUNNING=2, BUSY=4.
      if (state.status !== 1 && state.status !== 2 && state.status !== 4) return;
      window.__AGY_DIAG_AGENT_OBSERVE__?.(state);
      window.__AGY_VERIFIED_AGENT_OBSERVE__?.(state);
      bridge.onAgentState(
        window.__AG_NATIVE_BRIDGE_TOKEN__, state.conversationId, state.status, state.fullyIdle === true, compactStage(state));
    } catch (_) {
      // Never interrupt core agent state updates because notifications failed.
    }
  };
})();
