/* VM behavior tests against the actual patched v2.4 FDa Redux middleware. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const path=process.argv[2]||'web/generated/main.js';
const src=fs.readFileSync(path,'utf8'),marker='/* AGY_V24_VISIBLE_READ_RECEIPT */';
assert.equal(src.split(marker).length-1,1,'staged bundle must contain exactly one read patch');
const from=src.indexOf(marker),to=src.indexOf(';async function GDa(',from);
assert(to>from,'exact upstream FDa boundary present');
const code=src.slice(from,to);
function setup(){
 const calls=[],frames=[],service=[],seen=[];
 const state={conversation:{localLastViewedTimes:{},convoState:{type:'idle'}},
   trajectorySummaries:{summaries:{}}};
 const node={dataset:{cascadeId:'A'},isConnected:true,
  getBoundingClientRect:()=>({width:300,height:700,top:0,bottom:700}),
  querySelector:()=>({})};
 let displayed=[node],visible=true,focused=true;
 const doc={get visibilityState(){return visible?'visible':'hidden'},hasFocus:()=>focused,
  querySelectorAll:()=>displayed};
 const ctx={document:doc,window:{innerHeight:900},console,
  Date,Set,Number,Array,Math,requestAnimationFrame:fn=>frames.push(fn),
  DDa:{match:g=>g.type==='start'},EDa:{match:g=>g.type==='focus'},
  Ct:{match:g=>g.type==='route'},rm:{match:g=>g.type==='summary'},
  T:{CascadeRunStatus:{RUNNING:2,BUSY:4}},
  te:{ConversationAnnotationsSchema:'annotations'},
  p:(_,v)=>v,Xd:v=>({seconds:v.getTime()/1000}),Gca:()=>false,
  Op:{actions:{markViewedLocally:x=>({type:'markLocally',payload:x})}},
  Cpb:x=>({type:'persistCoreRead',payload:x})};
 vm.createContext(ctx);
 vm.runInContext(code,ctx);
 const store={getState:()=>state,dispatch:null};
 const next=action=>{calls.push(action);if(action.type==='route')
    state.conversation.convoState={type:'active',cascadeId:action.payload.cascadeId};
   if(action.type==='summary'){
     for(const [id,summary] of action.payload.updates)
       state.trajectorySummaries.summaries[id]=summary;
   }
   if(action.type==='markLocally')
      state.conversation.localLastViewedTimes[action.payload.cascadeId]=300;
   return action;};
 const dispatch=ctx.FDa()(store)(next);
 store.dispatch=action=>dispatch(action);
 const annotations=(ids,v)=>{service.push([ids,v]);return Promise.resolve({})};
 const start=()=>dispatch({type:'start',payload:{
  updateConversationAnnotations:annotations,recordError:()=>{},
  getStartingConversationIds:()=>[]}});
 const route=id=>dispatch({type:'route',payload:{cascadeId:id}});
 const focus=x=>dispatch({type:'focus',payload:x});
 const summary=(id,stamp,{running=false,lastViewed=0,explicitUnread=false}={})=>{
  state.conversation.localLastViewedTimes[id]=lastViewed;
  dispatch({type:'summary',payload:{updates:[[id,{
    notFullyIdle:running,waitingSteps:[],
    status:running?2:1,lastModifiedTime:{seconds:stamp,nanos:0},
    annotations:{markedAsUnread:explicitUnread,lastUserViewTime:{seconds:lastViewed,nanos:0}}
  }]]}});
 };
 const paint=()=>{while(frames.length)frames.shift()()};
 const marked=()=>calls.filter(x=>x.type==='persistCoreRead').map(x=>x.payload.cascadeId);
 return {calls,service,seen,state,node,doc,ctx,store,start,route,focus,summary,paint,marked,
   setVisible:x=>visible=x,setFocused:x=>focused=x,setDisplayed:x=>displayed=x};
}
function t(name,fn){fn();console.log('PASS',name)}
t('A finished response in already open visible chat persists read without route change',()=>{
 const x=setup();x.start();x.route('A');x.summary('A',100,{running:true});
 x.paint();assert.deepEqual(x.marked(),[]);
 x.summary('A',105);x.paint();assert.deepEqual(x.marked(),['A']);
 assert(x.calls.some(c=>c.type==='markLocally'));
 x.summary('A',105);x.paint();assert.deepEqual(x.marked(),['A'],'no duplicate read RPC');
});
t('B background or unrelated conversation remains unread',()=>{
 const x=setup();x.start();x.route('A');x.setVisible(false);
 x.summary('A',105);x.paint();assert.deepEqual(x.marked(),[]);
 x.setVisible(true);x.summary('B',105);x.paint();assert.deepEqual(x.marked(),[]);
});
t('C ordinary sidebar route changes retain upstream Core read annotation',()=>{
 const x=setup();x.start();x.route('A');x.route('B');
 assert(x.service.length>=1,'existing route-view write still executed');
 assert(x.service.some(e=>e[1].lastUserViewTime));
});
t('D notification navigation uses same upstream route-view read write',()=>{
 const x=setup();x.start();x.route('A');const n=x.service.length;
 x.route('B');assert(x.service.length>n);
});
t('E locked screen never treats completion as viewed',()=>{
 const x=setup();x.start();x.route('A');x.setVisible(false);x.setFocused(false);
 x.focus(false);x.summary('A',106);x.paint();assert.deepEqual(x.marked(),[]);
});
t('F foreground focus/resume preserves original Core read action',()=>{
 const x=setup();x.start();x.route('A');x.focus(false);
 const n=x.service.length;x.setVisible(true);x.setFocused(true);x.focus(true);
 assert(x.service.length>n,'Core receipt on focus restoration');
});
t('G multiple conversations preserve distinct read states',()=>{
 const x=setup();x.start();x.route('A');
 x.summary('B',106);x.summary('C',106);x.summary('A',107);x.paint();
 assert.deepEqual(x.marked(),['A']);
});
t('H persisted lastUserViewTime survives fresh Redux store/Activity',()=>{
 const x=setup();x.start();x.route('A');
 x.summary('A',107,{lastViewed:110});x.paint();
 assert.deepEqual(x.marked(),[],'authoritative persisted read timestamp prevents new read');
});
t('stale frame, empty/unrendered UI and visible-message guard',()=>{
 const x=setup();x.start();x.route('A');x.summary('A',107);
 x.route('B');x.paint();assert.deepEqual(x.marked(),[],'pending A cannot mark after route change');
 const y=setup();y.start();y.route('A');y.setDisplayed([]);
 y.summary('A',107);y.paint();assert.deepEqual(y.marked(),[],'no receipt when chat is not rendered');
});
