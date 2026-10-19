/* Diagnostic-only: never log chat contents, tool arguments, project paths or tokens. */
(function(){
  var summarySeen = new Map(), sidebarSeen = new Map();
  function write(kind, detail) {
    var line = '[AGY_DIAG] ' + kind + ' ' + detail;
    try { console.info(line); } catch (_) {}
    try { if (window.Android && window.Android.diagnosticEvent)
      window.Android.diagnosticEvent(kind, detail); } catch (_) {}
  }
  function shortId(id) {
    if (typeof id !== 'string') return '-';
    return id.slice(-6).replace(/[^a-zA-Z0-9]/g,'_');
  }
  window.__AGY_DIAG_SUMMARY_UPDATE__ = function(updates, deletes) {
    try {
      for (var item of (updates || [])) {
        var id = shortId(item[0]), s = item[1] || {};
        var state = [Number(s.status)||0, s.notFullyIdle===true ? 1 : 0,
                     (s.waitingSteps||[]).length].join(',');
        if (summarySeen.get(id) !== state) {
          summarySeen.set(id,state);
          write('summary',id+' status,idle_flag,waiting='+state);
        }
      }
      if ((deletes||[]).length) write('summary_delete','count='+deletes.length);
    } catch (_) {}
  };
  window.__AGY_DIAG_SIDEBAR__ = function(id, selected, summaryActive, agentActive, starting) {
    try {
      var token = shortId(id), state = [selected?1:0,summaryActive?1:0,
                                         agentActive?1:0,starting?1:0].join(',');
      if (sidebarSeen.get(token) !== state) {
        sidebarSeen.set(token,state);
        write('sidebar',token+' selected,summary,agent,starting='+state);
      }
    } catch (_) {}
  };
  document.addEventListener('visibilitychange',function(){
    write('visibility','state='+document.visibilityState);
  });
  window.addEventListener('agy:host-resume',function(){
    write('host_resume','received=1');
  });
  window.addEventListener('pageshow',function(event){
    write('pageshow','persisted='+(event.persisted?1:0));
  });
  write('script','loaded=1');
})();
