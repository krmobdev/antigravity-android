/* Diagnostic-only: never log chat contents, tool arguments, project paths or tokens. */
(function(){
  var summarySeen = new Map(), sidebarSeen = new Map();
  var pageId = Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8), seq = 0;
  window.__AGY_DIAG_PAGE_ID__ = pageId;
  function write(kind, detail) {
    detail = 'page='+pageId+' seq='+(++seq)+' '+detail;
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
        var fullId = item[0], id = shortId(fullId), s = item[1] || {};
        try {
          if (typeof fullId === 'string' && typeof s.summary === 'string'
              && window.Android?.onConversationSummary) {
            window.Android.onConversationSummary(fullId, s.summary.slice(0,72));
          }
        } catch (_) {}
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
  // Capture during VW's render, immediately consume in its calling XW render.
  // This never emits a render-time log. React commit logging happens below.
  var vwCaptured = null, commitSeen = new Map(), agentSeen = new Map();
  window.__AGY_DIAG_VW_CAPTURE__ = function(id,selected,summaryActive,agentActive,starting,verifiedIdle,result){
    vwCaptured={id:id,selected:!!selected,summaryActive:!!summaryActive,
                agentActive:!!agentActive,starting:!!starting,
                verifiedIdle:!!verifiedIdle,result:!!result};
  };
  window.__AGY_DIAG_VW_TAKE__ = function(id){
    var value=vwCaptured;
    vwCaptured=null;
    return value && value.id===id ? value : null;
  };
  // Mirrors the priority in ZU exactly; fallback timestamp itself may be absent.
  // Do not claim "time" unless a screenshot or DOM observation confirms it.
  window.__AGY_DIAG_RESOLVE_INDICATOR__ = function(s){
    return s.isArchived ? 'archive' :
           s.pending ? 'permission' :
           s.isBackgroundWorking ? 'background_dots' :
           s.isLoading ? 'spinner' :
           s.isUnread ? 'unread' : 'time_or_empty';
  };
  window.__AGY_DIAG_XW_COMMIT__ = function(d){
    try {
      if (!d || !d.id || !d.component) return;
      var key=d.component+'|'+d.id;
      // Include the derived branch without recalculating native cache or Redux.
      var values=[d.selected,d.summaryStatus,d.notFullyIdle,d.agentActive,
        d.starting,d.verifiedIdle,d.vwResult,d.yTResult,d.childRunning,
        d.isLoading,d.isBackgroundWorking,d.isArchived,d.pending,d.isUnread,
        d.timestampMode,d.renderedIndicator];
      var state=JSON.stringify(values);
      if (commitSeen.get(key)===state) return;
      commitSeen.delete(key);commitSeen.set(key,state);
      if (commitSeen.size>256) commitSeen.delete(commitSeen.keys().next().value);
      write('xw_commit',
        'instance='+String(d.component).replace(/[^A-Za-z0-9:_-]/g,'_').slice(0,36)+
        ' id='+shortId(d.id)+
        ' selected='+Number(!!d.selected)+
        ' summaryStatus='+String(d.summaryStatus)+
        ' notFullyIdle='+Number(!!d.notFullyIdle)+
        ' agentActive='+Number(!!d.agentActive)+
        ' starting='+Number(!!d.starting)+
        ' verifiedIdle='+Number(!!d.verifiedIdle)+
        ' VW='+Number(!!d.vwResult)+
        ' yT='+String(d.yTResult)+
        ' childRunning='+Number(!!d.childRunning)+
        ' isLoading='+Number(!!d.isLoading)+
        ' isBackgroundWorking='+Number(!!d.isBackgroundWorking)+
        ' archived='+Number(!!d.isArchived)+
        ' pending='+Number(!!d.pending)+
        ' unread='+Number(!!d.isUnread)+
        ' branch='+String(d.renderedIndicator)+
        ' timestampMode='+String(d.timestampMode));
    } catch(_) {}
  };
  window.__AGY_DIAG_AGENT_OBSERVE__ = function(s){
    try {
      if (!s || typeof s.conversationId!=='string') return;
      var id=shortId(s.conversationId), state=String(s.status)+'/'+Number(s.fullyIdle===true);
      if (agentSeen.get(id)===state) return;
      agentSeen.set(id,state);
      write('js_agent','id='+id+' status/fullyIdle='+state);
    } catch(_) {}
  };
  window.__AGY_DIAG_JS_SUBSCRIBERS__ = function(id,status,count){
    try { write('reconcile_subscribers','id='+shortId(id)+' status='+status+' listeners='+count); }
    catch(_) {}
  };
  write('diagnostic_version','version=AGY_XW_COMMIT_V1;expect_VW=AGY_SIDEBAR_RECONCILE_V1;expect_XW=AGY_XW_RUNNING_RECONCILE_V1');
})();
