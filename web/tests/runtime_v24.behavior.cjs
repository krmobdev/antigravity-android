/** Regression: actual v2.4 minified Hca and NDa are executed with fake Core streams. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const s=fs.readFileSync(process.argv[2]||'web/generated/main.js','utf8');
const piece=(begin,end)=>{const a=s.indexOf(begin);assert(a>=0,begin);const b=s.indexOf(end,a);assert(b>a,end);return s.slice(a,b)};
function host(){
 const callbacks=new Map();
 return {__AG_NATIVE_BRIDGE_TOKEN__:'test-token',
  addEventListener:(type,fn)=>{if(!callbacks.has(type))callbacks.set(type,new Set());callbacks.get(type).add(fn)},
  removeEventListener:(type,fn)=>callbacks.get(type)?.delete(fn),
  dispatchEvent:ev=>{for(const f of callbacks.get(ev.type)||[])f(ev)},
  size:type=>callbacks.get(type)?.size||0};
}
async function streamTest(){
 const window=host(), parent=new AbortController();let calls=0,errors=0,notifies=0,got=[],options=[];
 const ctx={window, AbortController,console,setTimeout,Promise,
  d:x=>x,Je:()=>String(calls),ue:id=>({conversationId:id}),
  p:(schema,data)=>{options.push(data);return data},te:{StreamAgentStateUpdatesRequestSchema:'s',ClientTrajectoryVerbosity:{PROD_UI:2}},
  Eca:(old,update)=>({...old,...update}),Gca:()=>false};
 ctx.a=(request,{signal})=>{
  calls++;
  if(calls===1)return{async *[Symbol.asyncIterator](){
   await new Promise((resolve,reject)=>{
    if(signal.aborted)return reject(new Error('transport aborted'));
    signal.addEventListener('abort',()=>reject(new Error('transport aborted')),{once:true});
   });
  }};
  return{async *[Symbol.asyncIterator](){yield {update:{status:1,fullyIdle:true}}}};
 };
 window.__AGY_NOTIFY_AGENT_STATE__=()=>{notifies++};
 vm.createContext(ctx);vm.runInContext(piece('async function Hca(',';var Ke=class'),ctx);
 let settled=false;
 const run=ctx.Hca(ctx.a,'chat-1',value=>{got.push(value);parent.abort()},parent,
  {disableRehydration:true,onError:()=>errors++}).then(()=>{settled=true});
 for(let i=0;i<80&&calls<1;i++)await new Promise(r=>setTimeout(r,5));
 assert.equal(calls,1,'first observer should subscribe');
 assert.equal(parent.signal.aborted,false);
 window.dispatchEvent({type:'agy:host-resume'});
 for(let i=0;i<80&&!settled;i++)await new Promise(r=>setTimeout(r,5));
 await run;
 assert.equal(calls,2,'only one new transport subscription');
 assert.equal(got.length,1,'rehydration restores Core state');
 assert.equal(got[0].fullyIdle,true);
 assert.equal(options[0].disableRehydration,true);
 assert.equal(options[1].disableRehydration,false,'resume must fetch actual snapshot');
 assert.equal(errors,0,'expected host resume is not agent error');
 assert.equal(notifies,1,'native bridge observes Core state');
 assert.equal(window.size('agy:host-resume'),0,'transport listener cleaned up');
 console.log('PASS Hca transport-only abort, parent preserved until result, reconnect + rehydration, no duplicate listeners');
}
function summaryTest(){
 const window=host(), calls=[],updates=[],dispatches=[];
 const ctx={window,AbortController,LDa:{match:f=>f.type==='summaries'},MDa:dispatch=>({
  add:(q,old)=>updates.push([q,old]),dispose:()=>calls.push('disposed')
 }),Et:class{},Dt:()=>{},$i:handlers=>calls.push(handlers),console, Object};
 vm.createContext(ctx);vm.runInContext(piece('function NDa()',';function Ft('),ctx);
 let dispatch;
 const store={dispatch:a=>dispatch(a)};
 dispatch=ctx.NDa()(store)(x=>{dispatches.push(x);return x});
 const action={type:'summaries',payload:{analyticsService:{},subscribe:()=>{}}};
 dispatch(action);assert.equal(calls.length,1);
 const first=calls[0];first.onMessage({updates:{'chat-1':{notFullyIdle:true}},deletes:[]});
 assert.equal(updates.length,1);
 window.dispatchEvent({type:'agy:host-resume'});
 assert.equal(first.abort.signal.aborted,true,'old summary stream aborted');
 const second=calls[calls.length-1];
 first.onMessage({updates:{'chat-1':{notFullyIdle:true}},deletes:[]});
 assert.equal(updates.length,1,'stale stream ignored');
 second.onMessage({updates:{'chat-1':{notFullyIdle:false}},deletes:[]});
 assert.equal(updates.length,2,'fresh summary snapshot accepted');
 assert.equal(updates[1][1],true,'fresh stream replaces stale sidebar state');
 window.dispatchEvent({type:'pagehide'});
 assert.equal(window.size('agy:host-resume'),0,'summary listener released on teardown');
 console.log('PASS NDa resume aborts old summary feed, ignores stale data, restores fresh sidebar snapshot');
}
function routeTest(){
 const window=host(),events=[],listeners=[];
 const router={state:{location:{pathname:'/c/alpha',search:{}}},navigate:({params})=>{
  router.state.location.pathname='/c/'+params.cascadeId;for(const f of listeners)f();
 },subscribe:(kind,fn)=>{assert.equal(kind,'onResolved');listeners.push(fn);return()=>listeners.splice(listeners.indexOf(fn),1)}};
 window.Android={onVisibleConversation:(token,id)=>{assert.equal(token,'test-token');events.push('visible:'+id)},
  onNotificationNavigationResult:(token,id)=>{assert.equal(token,'test-token');events.push('ack:'+id)}};
 const ctx={window,console};vm.createContext(ctx);
 vm.runInContext(piece('let L5=null;function t8b(','function u8b('),ctx);
 ctx.fakeRouter=router;vm.runInContext('L5=fakeRouter;window.__AGY_ATTACH_ROUTE_V24__(L5)',ctx);
 window.dispatchEvent({type:'agy:open-conversation',detail:{conversationId:'beta'}});
 assert(events.includes('ack:beta'),'only resolved matching route acknowledges native deep link');
 assert(events.includes('visible:beta'));
 window.dispatchEvent({type:'agy:open-conversation',detail:{conversationId:'../unsafe'}});
 assert(!events.includes('ack:../unsafe'),'invalid ID ignored');
 router.state.location={pathname:'/c/beta',search:{focused:'focused-1'}};
 for(const f of [...listeners])f();
 assert(events.includes('visible:focused-1'),'split pane reports focused conversation');
 window.dispatchEvent({type:'pagehide'});
 assert.equal(window.size('agy:host-resume'),0);
 console.log('PASS v2.4 router deep links and visible-chat reporting with token gate');
}
(async()=>{await streamTest();summaryTest();routeTest()})().catch(err=>{console.error(err);process.exitCode=1});
