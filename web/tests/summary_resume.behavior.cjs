const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const handlers={}, streams=[], actions=[];
const context={window:{addEventListener:(name,fn)=>handlers[name]=fn},AbortController,
  Ys:class {},Xs:()=>{},Ok:options=>streams.push(options),
  Uya:{match:a=>a.type==='start'},cma:()=>({type:'clear'}),Fo:batch=>({type:'batch',batch})};
vm.createContext(context);vm.runInContext(fs.readFileSync(process.argv[2],'utf8'),context);
let dispatch;const store={dispatch:a=>dispatch(a)};
dispatch=context.Vya()(store)(a=>actions.push(a));
const start={type:'start',payload:{subscribe:()=>{},analyticsService:{}}};
dispatch(start);assert.equal(streams.length,1);
handlers['agy:host-resume']?.({});
assert.equal(streams.length,2,'retained page must renew its summary stream on first resume');
assert.equal(streams[0].abort.signal.aborted,true);
assert.equal(actions.filter(a=>a.type==='clear').length,0,'retain old data until fresh snapshot arrives');
streams[0].onMessage({updates:{stale:{notFullyIdle:true}},deletes:[]});
assert.equal(actions.filter(a=>a.type==='batch').length,0,'aborted stream must not overwrite fresh state');
streams[1].onMessage({updates:{chat:{status:1,notFullyIdle:false}},deletes:[]});
assert.equal(actions.filter(a=>a.type==='clear').length,1);
assert.equal(actions.filter(a=>a.type==='batch').length,1);
handlers['agy:agent-proof-changed']?.({detail:{fullyIdle:false}});
assert.equal(streams.length,2,'running update does not needlessly restart summaries');
handlers['agy:agent-proof-changed']?.({detail:{fullyIdle:true}});
assert.equal(streams.length,3,'native completion must refresh a foreground list too');
streams[1].onDisconnect();
streams[2].onMessage({updates:{chat:{status:1,notFullyIdle:false}},deletes:[]});
assert.equal(actions.filter(a=>a.type==='clear').length,2);
console.log('PASS retained summary subscription refreshes on resume/native completion and rejects stale stream events');
