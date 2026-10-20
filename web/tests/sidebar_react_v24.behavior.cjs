/** Execute actual extracted upstream v2.4 React VAb/z_ helpers with the packaged native-state bridge. */
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const s=fs.readFileSync(process.argv[2]||'web/generated/main.js','utf8');
const start=s.indexOf('function VAb('),mid=s.indexOf(';function WAb(',start);
const zs=s.indexOf('z_=({cascadeId:a',mid),ze=s.indexOf('};var YAb=',zs);
assert(start>0&&mid>start&&zs>mid&&ze>zs,'v2.4 sidebar React helpers missing');
const vAb=s.slice(start,mid),zAb='var '+s.slice(zs,ze+2);
assert(vAb.includes('A.useSyncExternalStore')&&
    vAb.includes('AGY_SIDEBAR_RECONCILE_V24'),'actual React subscription absent');
assert(zAb.includes('AGY_SIDEBAR_RUNNING_RECONCILE_V24'),'secondary running predicate absent');
const window={};
const bridgeEvents=[];
window.Android={wasAgentRecentlyFinished:(token,id)=>bridgeEvents.includes(id),
  diagnosticEvent:()=>{},onAgentState:()=>{}};
window.__AG_NATIVE_BRIDGE_TOKEN__='test-token';
const events={};
window.addEventListener=(name,fn)=>{(events[name]??=[]).push(fn)};
const document={visibilityState:'visible',
  addEventListener:(name,fn)=>{(events[name]??=[]).push(fn)}};
const ctx={window,document,console,Set,Map};
vm.createContext(ctx);
const base=require('node:fs').readFileSync('web/src/patches/web_optimizations/sidebar-reconcile.js','utf8');
const notifications=fs.readFileSync('web/src/patches/web_optimizations/agent-notifications.js','utf8');
vm.runInContext(base,ctx);vm.runInContext(notifications,ctx);
let focused='',active={},starting={},running={},calls=0,activeSubs=[];
ctx.Hm=fn=>fn({});ctx.Ip=()=>focused;ctx.KR=id=>!active[id];
ctx.tz=()=>focused&&running[focused]||false;ctx.SX=id=>!!starting[id];ctx.TAb=x=>x;
ctx.A={useSyncExternalStore:(subscribe,snapshot)=>{
    const dispose=subscribe(()=>calls++);activeSubs.push(dispose);return snapshot();
  },createElement:(type,props,...children)=>({type,props,children})};
ctx.T={CascadeRunStatus:{IDLE:1}};
ctx.UZ='UZ';ctx.W='W';ctx.XAb=()=>null;
for(const f of ['rdb','sdb','qdb','VR','UR','Ddb','pY','Gpb','Bdb','bN','WAb'])
    ctx[f]=()=>undefined;
ctx.pY=id=>active[id]?'running':'ready';
ctx.KR=id=>!active[id];
vm.runInContext(vAb,ctx);vm.runInContext(zAb,ctx);
function test(name,fn){fn();console.log('PASS',name)}
const render=(id)=>ctx.z_({cascadeId:id,timestamp:'idleOnly'}).props;
test('actual React z_ renders original notFullyIdle as Working',()=>{
 active.A=true;active.B=false;assert.equal(render('A').isLoading,true);
 assert.equal(render('B').isLoading,false);
});
test('native Core RUNNING retains Working for exactly one dialog',()=>{
 window.__AGY_NOTIFY_AGENT_STATE__({conversationId:'A',status:2,fullyIdle:false});
 assert.equal(render('A').isLoading,true);
});
test('Core IDLE fullyIdle suppresses stale React isLoading in actual z_',()=>{
 window.__AGY_NOTIFY_AGENT_STATE__({conversationId:'A',status:1,fullyIdle:true});
 assert.equal(render('A').isLoading,false);
});
test('B running while A done and C earlier completed maintain independent indicators',()=>{
 active.A=true;active.B=true;active.C=true;
 window.__AGY_NOTIFY_AGENT_STATE__({conversationId:'B',status:2,fullyIdle:false});
 window.__AGY_NOTIFY_AGENT_STATE__({conversationId:'C',status:1,fullyIdle:true});
 assert.equal(render('A').isLoading,false);
 assert.equal(render('B').isLoading,true);
 assert.equal(render('C').isLoading,false);
});
test('a real second RUNNING re-enables React indicator after completion',()=>{
 window.__AGY_NOTIFY_AGENT_STATE__({conversationId:'A',status:2,fullyIdle:false});
 assert.equal(render('A').isLoading,true);
});
test('selected React e source follows native proof while optimistic starting remains',()=>{
 focused='A';running.A=true;
 window.__AGY_NOTIFY_AGENT_STATE__({conversationId:'A',status:1,fullyIdle:true});
 assert.equal(ctx.VAb('A'),false);
 starting.A=true;assert.equal(ctx.VAb('A'),true);
 starting.A=false;focused='';
});
test('React receives real listener events and cleans up on unmount',()=>{
 assert(calls>0,'existing native notification state must trigger React subscriptions');
 const before=calls;
 for(const dispose of activeSubs)dispose();
 window.__AGY_NOTIFY_AGENT_STATE__({conversationId:'A',status:2,fullyIdle:false});
 assert.equal(calls,before);
});
test('native durable completed proof after WebView recreation drives React snapshot',()=>{
 bridgeEvents.push('C');active.C=true;assert.equal(render('C').isLoading,false);
});
