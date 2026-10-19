"use strict";
const fs=require("fs"),vm=require("vm"),assert=require("assert");
const src=fs.readFileSync(process.argv[2],"utf8");
const written=[];
const window={
  Android:{diagnosticEvent:(kind,detail)=>written.push({kind,detail})},
  addEventListener:()=>{}
};
const document={addEventListener:()=>{},visibilityState:"visible"};
vm.runInNewContext(src,{window,document,console:{info:()=>{}},Date,Math,Map,Set,Number,String});
const classify=window.__AGY_DIAG_RESOLVE_INDICATOR__;
assert.strictEqual(classify({isArchived:true,pending:true,isBackgroundWorking:true,isLoading:true,isUnread:true}),"archive");
assert.strictEqual(classify({pending:true,isBackgroundWorking:true,isLoading:true,isUnread:true}),"permission");
assert.strictEqual(classify({isBackgroundWorking:true,isLoading:true,isUnread:true}),"background_dots");
assert.strictEqual(classify({isLoading:true,isUnread:true}),"spinner");
assert.strictEqual(classify({isUnread:true}),"unread");
assert.strictEqual(classify({}),"time_or_empty");
assert(written.some(x=>x.kind==="diagnostic_version"));
const prefix=written[0].detail.match(/page=([^ ]+) seq=\d+/)?.[1];
assert(prefix,"page id exists");
const vw=window.__AGY_DIAG_VW_CAPTURE__;
const take=window.__AGY_DIAG_VW_TAKE__;
const log=window.__AGY_DIAG_XW_COMMIT__;
vw("cascade-001",false,true,false,false,true,false);
const captured=take("cascade-001");
assert.strictEqual(captured.verifiedIdle,true);
assert.strictEqual(captured.result,false);
assert.strictEqual(take("cascade-001"),null);
const base={id:"cascade-001",component:":r0:",selected:captured.selected,
 summaryStatus:2,notFullyIdle:captured.summaryActive,agentActive:captured.agentActive,
 starting:captured.starting,verifiedIdle:captured.verifiedIdle,vwResult:captured.result,
 yTResult:"ready",childRunning:false,isLoading:false,isBackgroundWorking:false,
 isArchived:false,pending:false,isUnread:false,timestampMode:"idleOnly",renderedIndicator:"time_or_empty"};
log(base);
let commits=()=>written.filter(x=>x.kind==="xw_commit");
assert.strictEqual(commits().length,1,"first component commit must log");
log(base);
assert.strictEqual(commits().length,1,"unchanged component commit deduplicates");
log({...base,component:":r1:"});
assert.strictEqual(commits().length,2,"second same-conversation component logs its FIRST commit");
log({...base,isLoading:true,renderedIndicator:"spinner"});
assert.strictEqual(commits().length,3,"changed state after commit logs");
vw("cascade-001",true,false,false,false,true,false);
assert.strictEqual(commits().length,3,"render capture without commit must not log");
window.__AGY_DIAG_AGENT_OBSERVE__({conversationId:"cascade-001",status:1,fullyIdle:true});
window.__AGY_DIAG_JS_SUBSCRIBERS__("cascade-001",1,2);
assert(written.some(x=>x.kind==="js_agent"));
assert(written.some(x=>x.kind==="reconcile_subscribers"));
const nums=written.map(x=>Number(x.detail.match(/seq=(\d+)/)?.[1]));
assert(nums.every((x,i)=>i===0||x>nums[i-1]),"strictly increasing sequence across events");
assert(written.every(x=>x.detail.includes("page="+prefix+" ")),"all entries have stable page identity");
console.log("OK: priority, immutable render snapshot, abandoned render, commit dedup per instance, page/sequence correlation");
