/* Agent-state reconciliation for a stale sidebar summary (diagnostic WIP).
 * The backend can leave CascadeTrajectorySummary.notFullyIdle=true even after
 * the actual agent state stream reports IDLE+fullyIdle=true.
 * Only override that single stale summary flag; never hide local foreground
 * activity/starting spinners or suppress a new RUNNING/BUSY event.
 */
(function(){
  var status = new Map(), listeners = new Set();
  var MAX = 128;
  function valid(id){ return typeof id === 'string' &&
    id.length > 0 && id.length <= 128 && /^[A-Za-z0-9_+:-]+$/.test(id); }
  window.__AGY_VERIFIED_AGENT_GET__ = function(id){
    return status.get(id) === 1;
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
      if(prev === next) return;
      status.delete(id);status.set(id,next);
      if(status.size > MAX) status.delete(status.keys().next().value);
      for(var cb of listeners) { try { cb(); } catch (_) {} }
      if(next === 1){
        window.Android?.diagnosticEvent?.('reconcile_idle', 'id='+id.slice(-6).replace(/[^A-Za-z0-9]/g,'_'));
      }
    }catch(_){}
  };
})();
