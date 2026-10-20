(function(){
/**
 * patches/web_optimizations/taps.js - kill the 300ms double-tap delay.
 */
'use strict';

function installTaps(doc) {
  try {
    var style = doc.createElement('style');
    style.setAttribute('data-agy', 'web-optimizations');
    style.textContent =
      'button,a,[role="button"],input,select,textarea{touch-action:manipulation;}';
    (doc.head || doc.documentElement).appendChild(style);
  } catch (e) {}
}

/**
 * patches/web_optimizations/viewport.js - keep --agy-vvh honest while the
 * soft keyboard opens.
 */
function installViewport(global, doc) {
  try {
    if (global.visualViewport) {
      global.visualViewport.addEventListener('resize', function () {
        var vv = global.visualViewport;
        if (vv && vv.height && doc.documentElement) {
          doc.documentElement.style.setProperty(
            '--agy-vvh', Math.round(vv.height) + 'px');
        }
      }, { passive: true });
    }
  } catch (e) {}
}

/**
 * patches/web_optimizations/gpu.js - GPU-accelerate scroll/animation surfaces
 * and isolate layout for lists.
 */
function installGpu(doc) {
  try {
    if (!doc.querySelector('style[data-agy="gpu-acceleration"]')) {
      var gpu = doc.createElement('style');
      gpu.setAttribute('data-agy', 'gpu-acceleration');
      // Promote ONLY the real scroll containers. Never inline <code>/<pre>:
      // per-element layers explode on Mali and saturate RenderThread.
      gpu.textContent =
        'main, [data-testid="chat-container"], .scrollable {' +
        ' transform: translateZ(0);' +
        ' -webkit-overflow-scrolling: touch; }' +
        '[role="listitem"], .message-row, .artifact-viewer {' +
        ' contain: layout style; }' +
        '*{ -webkit-tap-highlight-color: transparent; }';
      (doc.head || doc.documentElement).appendChild(gpu);
    }
  } catch (e) {}
}

/**
 * patches/web_optimizations/public-api.js - install and expose.
 */
var global = window;

if (global.__AG_WEB_OPTIMIZATIONS__) return;
global.__AG_WEB_OPTIMIZATIONS__ = true;

var doc = global.document;
if (!doc) return;

installTaps(doc);
installViewport(global, doc);
installGpu(doc);

global.__agWebOptimizations = { installed: true };

/* Agent-state reconciliation for stale sidebar summaries.
 * The backend can leave CascadeTrajectorySummary.notFullyIdle=true even after
 * the actual agent state stream reports IDLE+fullyIdle=true.
 * Hydrate native-confirmed completion across Android Activity/WebView recreations.
 * The proof expires and is cleared on a genuine native RUNNING event.
 * Do not trust the stale UI RUNNING summary over native completion proof.
 */
(function(){
  var status = new Map(), listeners = new Set(), effective = new Map();
  var MAX = 128;
  function valid(id){ return typeof id === 'string' &&
    id.length > 0 && id.length <= 128 && /^[A-Za-z0-9_+:-]+$/.test(id); }
  window.__AGY_VERIFIED_AGENT_GET__ = function(id){
    if (!valid(id)) return false;
    if (status.get(id) === 1) return true;
    try { return window.Android?.wasAgentRecentlyFinished?.(window.__AG_NATIVE_BRIDGE_TOKEN__, id) === true; }
    catch (_) { return false; }
  };
  window.__AGY_VERIFIED_AGENT_SUBSCRIBE__ = function(fn){
    listeners.add(fn);
    return function(){ listeners.delete(fn); };
  };
  // Native completion can change the synchronous Android snapshot without a
  // UI agent-stream event in this window. Recheck mounted React subscribers
  // when this particular WebView returns; React skips unchanged snapshots.
  function recheck(){
    for(var cb of listeners) { try { cb(); } catch (_) {} }
  }
  window.addEventListener?.('agy:host-resume', recheck);
  window.addEventListener?.('pageshow', recheck);
  if(typeof document !== 'undefined') document.addEventListener?.('visibilitychange', function(){
    if(document.visibilityState === 'visible') recheck();
  });
  window.__AGY_VERIFIED_AGENT_OBSERVE__ = function(state){
    try {
      if(!state || !valid(state.conversationId)) return;
      var next;
      if (state.status === 1 && state.fullyIdle === true) next=1;
      else if(state.status === 2 || state.status === 4) next=2;
      else return;
      var id=state.conversationId, prev=status.get(id);
      if(prev !== next){
        status.delete(id);status.set(id,next);
        if(status.size > MAX) status.delete(status.keys().next().value);
      }
      // Native service may clear its finished proof after receiving the first
      // genuine RUNNING. Recheck even when WebView sends repeated RUNNING.
      var snapshot=window.__AGY_VERIFIED_AGENT_GET__(id), before=effective.get(id);
      effective.set(id,snapshot);
      if(effective.size > MAX) effective.delete(effective.keys().next().value);
      if(prev !== next || (before !== undefined && before !== snapshot)){
        for(var cb of listeners) { try { cb(); } catch (_) {} }
      }
      if(prev === next) return;
      if(next === 1){
        window.Android?.diagnosticEvent?.(window.__AG_NATIVE_BRIDGE_TOKEN__, 'reconcile_idle', 'id='+id.slice(-6).replace(/[^A-Za-z0-9]/g,'_'));
      }
    }catch(_){}
  };
})();

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


})();
