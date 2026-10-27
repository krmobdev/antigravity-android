#!/usr/bin/env python3
"""Fail-closed v2.4.0-only reconnect + sidebar + notification route patches.

Only modifies generated/main.js. Never touches checked-in upstream web/main.js.
Host resume cancels the observation transport, never the parent UI controller
or the Core agent itself. Fresh subscribe rehydrates state from Core.
"""
from pathlib import Path
import sys

HEAD = "async function Hca("
TAIL = ";var Ke=class"
SUM_HEAD = "function NDa()"
SUM_TAIL = ";function Ft("
ROUTE_ANCHOR = 'let L5=null;function t8b(a){L5&&L5.navigate({to:"/c/$cascadeId",params:{cascadeId:a}})}'
ROUTE_INIT = 'L5=q=new xcb({routeTree:s8b,history:r,InnerWrap:n,context:d8b,basepath:q.basepath})'
MARKER = "/* AGY_V24_TRANSPORT_RECONNECT */"
SUMMARY_MARKER = "/* AGY_V24_SUMMARY_RECONNECT */"
ROUTE_MARKER = "/* AGY_V24_DEEPLINK */"

def one(s, old, new):
    count=s.count(old)
    if count!=1:
        raise ValueError(f"expected one v2.4.0 anchor, found {count}: {old[:135]}")
    return s.replace(old,new,1)

def patch(s):
    if MARKER in s or SUMMARY_MARKER in s or ROUTE_MARKER in s:
        if all(s.count(x)==1 for x in (MARKER,SUMMARY_MARKER,ROUTE_MARKER)):
            return s
        raise ValueError("partial/inconsistent v2.4 patches")
    start=s.index(HEAD); end=s.index(TAIL,start)
    hca=s[start:end]
    hca=one(hca,
        "B=ue(b),C=k,D=0;for(;!e.signal.aborted;)",
        "B=ue(b),C=k,D=0,__agyRehydrate=false;for(;!e.signal.aborted;)")
    hca=one(hca, "let E=Je();q?.(E);try{",
        "let E=Je(),__agyResumed=false;q?.(E);try{")
    hca=one(hca, "disableRehydration:f?.disableRehydration}),G=a(F,{signal:e.signal});for await(let H of g(G)){",
        'disableRehydration:__agyRehydrate?false:f?.disableRehydration}),' +
        MARKER +
        '__agyTransport=new AbortController,__agyOnResume=()=>{__agyResumed=true;__agyTransport.abort()},' +
        '__agyOnDispose=()=>__agyTransport.abort();' +
        'window.addEventListener("agy:host-resume",__agyOnResume);' +
        'e.signal.addEventListener("abort",__agyOnDispose,{once:true});' +
        'try{let G=a(F,{signal:__agyTransport.signal});for await(let H of g(G)){')
    hca=one(hca, "c(B),t?.(B))",
        "c(B),window.__AGY_NOTIFY_AGENT_STATE__?.(B),t?.(B),__agyRehydrate=false)")
    hca=one(hca, "}finally{g()}}h()}catch(F){",
        '}finally{g()}}}finally{' +
        'window.removeEventListener("agy:host-resume",__agyOnResume);' +
        'e.signal.removeEventListener("abort",__agyOnDispose)' +
        '}h()}catch(F){')
    hca=one(hca, "if(e.signal.aborted)break;let G=!0;",
        "if(e.signal.aborted)break;if(__agyResumed){D=0;C=k;__agyRehydrate=true;B=ue(b);continue}let G=!0;")
    hca=one(hca, "e.signal.aborted||h(await g(new Promise(F=>setTimeout(F,1E3))));",
        "if(__agyResumed){D=0;C=k;__agyRehydrate=true;B=ue(b);continue}" +
        "e.signal.aborted||h(await g(new Promise(F=>setTimeout(F,1E3))));")
    s=s[:start]+hca+s[end:]

    start=s.index(SUM_HEAD);end=s.index(SUM_TAIL,start)
    old=s[start:end]
    summary='''function NDa(){/* AGY_V24_SUMMARY_RECONNECT */var a=null,b=null;
return c=>{let last=null;
const resume=()=>{if(last)c.dispatch(last)};
window.addEventListener("agy:host-resume",resume);
window.addEventListener("pagehide",()=>window.removeEventListener("agy:host-resume",resume),{once:true});
return e=>f=>{if(LDa.match(f)){
last=f;let g=f.payload.subscribe,h=f.payload.analyticsService,k=a!==null;
a?.abort();b?.dispose();a=new AbortController;let live=a,l=MDa(c.dispatch);b=l;
let m=k,n=new Et(h,"trajectory_summary_load");
$i({label:"[TrajectorySummaries]",abort:a,createStream:q=>g({signal:q}),
onMessage:q=>{if(a!==live||live.signal.aborted)return;
Dt(n);l.add(q,m);m=!1;
try{window.__AGY_NOTIFY_SUMMARIES__?.(Object.entries(q.updates||{}))}catch(_){}},
onDisconnect:()=>{if(a===live)m=!0}})}
return e(f)}}}'''
    if not old.startswith('function NDa(){var a=null,b=null;return c=>e=>f=>{'):
        raise ValueError("v2.4 sidebar source changed")
    s=s[:start]+summary+s[end:]
    s=one(s,ROUTE_ANCHOR,ROUTE_ANCHOR+ROUTE_MARKER+'''
(function(){
let pending=null,attached=null,inFlight=0,navigationSerial=0;
const valid=id=>typeof id==="string"&&/^[A-Za-z0-9_+:-]{1,128}$/.test(id);
function currentConversation(router){
  let loc=router?.state?.location;
  let raw=loc?.pathname||"",match=/\\/c\\/([^/]+)$/.exec(raw),id="";
  if(match){try{id=decodeURIComponent(match[1])}catch(_){}}
  let focused=loc?.search?.focused;
  if(valid(focused))id=focused;
  return valid(id)?id:"";
}
function report(router){
  const id=currentConversation(router);
  try{window.Android?.onVisibleConversation?.(window.__AG_NATIVE_BRIDGE_TOKEN__,id)}catch(_){}
  if(pending&&id===pending){
    try{window.Android?.onNotificationNavigationResult?.(window.__AG_NATIVE_BRIDGE_TOKEN__,id)}catch(_){}
    pending=null;
    inFlight=0;
  }
}
window.__AGY_ATTACH_ROUTE_V24__=function(router){
  if(!router||attached===router)return;
  attached=router;
  const render=()=>report(router);
  let unsub=null;
  try{unsub=router.subscribe("onResolved",render)}catch(_){}
  window.addEventListener("agy:host-resume",render);
  window.addEventListener("pagehide",()=>{
    window.removeEventListener("agy:host-resume",render);
    try{if(typeof unsub==="function")unsub();else unsub?.unsubscribe?.()}catch(_){}
  },{once:true});
  render();
};
window.addEventListener("agy:open-conversation",function(e){
  let id=e?.detail?.conversationId;
  if(!valid(id)||!L5)return;
  if(pending===id&&inFlight)return;
  pending=id;
  if(currentConversation(L5)===id&&!inFlight){report(L5);return}
  const ticket=++navigationSerial;inFlight=ticket;
  try{
    const navigation=L5.navigate({to:"/c/$cascadeId",params:{cascadeId:id},replace:true});
    Promise.resolve(navigation).then(()=>{
      if(inFlight===ticket){inFlight=0;report(L5)}
    },()=>{if(inFlight===ticket){inFlight=0;if(pending===id)pending=null}});
  }catch(_){if(inFlight===ticket){inFlight=0;pending=null}}
});
})();''')
    s=one(s,ROUTE_INIT,ROUTE_INIT+';window.__AGY_ATTACH_ROUTE_V24__?.(L5)')
    return s

def main():
    if len(sys.argv)!=3:raise SystemExit("usage: patch_runtime_v24.py INPUT OUTPUT")
    src,dst=map(Path,sys.argv[1:])
    source=src.read_text(encoding="utf8")
    patched=patch(source)
    dst.write_text(patched,encoding="utf8")
    print("v2.4.0 stream, summaries and deep-link hooks applied",len(patched))

if __name__=="__main__":
    try:main()
    except (ValueError,KeyError) as err:raise SystemExit("v2.4 patch incompatible: "+str(err))
