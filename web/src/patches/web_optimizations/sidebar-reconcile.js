/* Agent-state reconciliation for a stale sidebar summary (diagnostic WIP).
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
    try { return window.Android?.wasAgentRecentlyFinished?.(id) === true; }
    catch (_) { return false; }
  };
  window.__AGY_VERIFIED_AGENT_SUBSCRIBE__ = function(fn){
    listeners.add(fn);
    return function(){ listeners.delete(fn); };
  };
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
        window.Android?.diagnosticEvent?.('reconcile_idle', 'id='+id.slice(-6).replace(/[^A-Za-z0-9]/g,'_'));
      }
    }catch(_){}
  };
})();
