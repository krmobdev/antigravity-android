/* Execute the actual version-pinned, patched dO hook, not a replacement recorder. */
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const bundle=fs.readFileSync(0,'utf8');
const start=bundle.indexOf('var dO=({editorRef:');
const end=bundle.indexOf(';function D5a',start);
assert(start>=0&&end>start,'actual streaming hook');
const hookEnd=bundle.indexOf(';function F5a',end);
assert(hookEnd>end);
const helper=fs.readFileSync('web/src/mobile/composer.js','utf8').split('/* Compact mobile composer')[0];
const tick=async()=>{for(let i=0;i<12;i++)await Promise.resolve()};
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return{promise,resolve,reject}}
class Stream{
 constructor(){this.items=[];this.wait=null}
 push(value){this.items.push({value,done:false});this.flush()}
 close(){this.items.push({done:true});this.flush()}
 fail(error){this.items.push({error});this.flush()}
 flush(){if(!this.wait||!this.items.length)return;const w=this.wait;this.wait=null;const n=this.items.shift();n.error?w.reject(n.error):w.resolve(n)}
 next(){const w=deferred();this.wait=w;this.flush();return w.promise}
 [Symbol.asyncIterator](){return this}
}
function fixture(){
 let cursor=0,now=0,nextTimer=0,nativeStarts=0,nativeStops=0;
 const effects=[],cells=[],timers=new Map(),streams=[],ends=[],errors=[],finals=[],chunks=[];
 const Q=()=>{},P=()=>Promise.resolve();
 const A={
  useState(init){const i=cursor++;if(!cells[i]){const value=typeof init==='function'?init():init;
   const c={value};c.set=v=>{c.value=typeof v==='function'?v(c.value):v};cells[i]=c}
   return[cells[i].value,cells[i].set]},
  useRef(init){const i=cursor++;return cells[i]||(cells[i]={current:init})},
  useCallback(fn,deps){return A.useMemo(()=>fn,deps)},
  useMemo(fn,deps){const i=cursor++,old=cells[i];
   if(!old||deps.some((v,n)=>v!==old.deps[n]))cells[i]={value:fn(),deps};
   return cells[i].value},
  useEffect(fn,deps){const i=cursor++,old=cells[i];
   if(!old||!deps||deps.some((v,n)=>v!==old.deps[n])){
    cells[i]={deps,cleanup:old?.cleanup};
    effects.push(()=>{cells[i].cleanup?.();cells[i].cleanup=fn()});
   }}
 };
 const native={
  startNativePcm(){nativeStarts++;return'OK'},
  stopNativePcm(){nativeStops++},
  readNativePcm(){return''}
 };
 const api={
  streamAudioTranscription(request,opts){assert.equal(request.mimeType,'audio/pcm;rate=16000');const s=new Stream();s.signal=opts.signal;streams.push(s);return s},
  sendAudioChunk(chunk){chunks.push(chunk);return Promise.resolve()},
  endAudioSession(request){const e=deferred();e.id=request.sessionId;ends.push(e);return e.promise}
 };
 const box={A,Cy:()=>api,c5a:()=>true,Lj:()=>new Map([['streamingAudioFeature',{enabled:true}]]),
  Pj:x=>x,C5a:()=>({waitForNextTrue:P,flush:Q}),d:x=>x,p:(schema,x)=>x,Uia:{},Via:{},Wia:{},
  AbortController,DOMException,Error,Uint8Array,atob:x=>Buffer.from(x,'base64').toString('binary'),
  console:{error(){},warn(){}},gp:x=>x?.message||String(x),
  performance:{now:()=>now},Date:{now:()=>now},
  setTimeout(fn,ms){const id=++nextTimer;timers.set(id,{fn,at:now+ms});return id},
  clearTimeout(id){timers.delete(id)},setInterval(){throw Error('unexpected interval')},
  AndroidBridge:native,__AG_NATIVE_BRIDGE_TOKEN__:'test-only'};
 box.window=box;vm.createContext(box);vm.runInContext(helper+'\n'+bundle.slice(start,hookEnd),box);
 const render=()=>{cursor=0;const result=box.dO({onError:e=>errors.push(e),onFinalTranscript:t=>finals.push(t)});for(const run of effects.splice(0))run();return result};
 return {render,streams,ends,errors,finals,chunks,box,
  get starts(){return nativeStarts},get stops(){return nativeStops},
  async ready(id='session'){const promise=render().startStreaming();await tick();const s=streams.at(-1);
   assert(s,'Core streaming started');s.push({message:{case:'ready',value:{sessionId:id}}});await promise;await tick();return s},
  advance(ms){now+=ms;for(const [id,t]of [...timers])if(t.at<=now){timers.delete(id);t.fn()}}
 };
}
const cases=[];
function test(name,fn){cases.push({name,fn})}
test('stop before Core ready: expected cancellation does not show an error',async()=>{
 const f=fixture(),started=f.render().startStreaming();await tick();
 f.render().stopStreaming();
 const e=new Error('cancelled');e.name='AbortError';f.streams[0].fail(e);
 await started;await tick();
 assert.equal(f.errors.length,0,'cancelled startup must not become UnknownError');
 assert.equal(f.stops,1,'native device released once');
});
test('a new capture waits for the previous endAudioSession acknowledgement',async()=>{
 const f=fixture();await f.ready('first');f.render().stopStreaming();
 assert.equal(f.ends.length,1);
 const next=f.render().startStreaming();await tick();
 assert.equal(f.starts,1,'do not open device while Core end request is in flight');
 f.ends[0].resolve();await tick();
 assert.equal(f.starts,1,'keep accepting the old final transcript until completion');
 f.streams[0].push({message:{case:'transcription',value:{text:'final fixture',isFinal:true}}});
 await tick();
 assert.deepEqual(f.finals,['final fixture']);
 assert.equal(f.starts,2);f.streams[1].push({message:{case:'ready',value:{sessionId:'second'}}});
 await next;f.render().cancelStreaming();
});
test('late old Core failure cannot poison the new recording',async()=>{
 const f=fixture();const old=await f.ready('old');f.render().stopStreaming();f.ends[0].resolve();f.advance(3000);await tick();
 await f.ready('new');old.fail(new Error('late old response'));await tick();
 assert.equal(f.errors.length,0,'stale stream must be silent');
 assert.equal(f.render().isStreaming,true);
 assert.equal(f.stops,1,'new microphone stays running');f.render().cancelStreaming();
});
test('late old transcript must not enter the next prompt',async()=>{
 const f=fixture(),old=await f.ready('old');f.render().stopStreaming();f.ends[0].resolve();f.advance(3000);await tick();
 await f.ready('new');old.push({message:{case:'transcription',value:{text:'stale fixture',isFinal:true}}});await tick();
 assert.deepEqual(f.finals,[]);f.render().cancelStreaming();
});
test('ten record-stop-record cycles retain generation ownership',async()=>{
 const f=fixture();
 for(let i=0;i<10;i++){
  const s=await f.ready('cycle-'+i);
  assert.equal(f.render().isStreaming,true);
  f.render().stopStreaming();f.ends.at(-1).resolve();await tick();
  s.close();await tick();
 }
 assert.equal(f.starts,10);assert.equal(f.stops,10);assert.deepEqual(f.errors,[]);
});
test('double Stop sends one Core end request',async()=>{
 const f=fixture(),s=await f.ready('once');
 f.render().stopStreaming();f.render().stopStreaming();
 assert.equal(f.ends.length,1);assert.equal(f.stops,1);
 f.ends[0].resolve();s.close();await tick();
});
test('cancel while waiting for finalization prevents a queued restart',async()=>{
 const f=fixture(),s=await f.ready('first');f.render().stopStreaming();
 const pending=f.render().startStreaming();await tick();
 assert.equal(f.starts,1);
 f.render().cancelStreaming();f.ends[0].resolve();s.close();
 await pending;await tick();
 assert.equal(f.starts,1);assert.deepEqual(f.errors,[]);
});
test('a timed out Core stop is bounded and allows a subsequent retry',async()=>{
 const f=fixture();await f.ready('timeout');f.render().stopStreaming();
 const next=f.render().startStreaming();await tick();
 f.advance(5000);await next;await tick();
 assert.equal(f.starts,1,'failed drain must not start a new capture');
 assert(f.errors.some(e=>e==='Previous dictation is still finishing. Try again.'));
 const oldErrors=f.errors.length;await f.ready('retry');
 assert.equal(f.starts,2);assert.equal(f.errors.length,oldErrors);f.render().cancelStreaming();
});
test('native 210-second limit finalizes transcription as ordinary Stop',async()=>{
 const f=fixture(),s=await f.ready('limited');
 f.box.AndroidBridge.readNativePcm=()=>'ERR_PCM_LIMIT';
 f.advance(45);await tick();
 assert.equal(f.ends.length,1);assert.equal(f.stops,1);assert.deepEqual(f.errors,[]);
 s.push({message:{case:'transcription',value:{text:'limit fixture',isFinal:true}}});s.close();
 f.ends[0].resolve();await tick();
 assert.deepEqual(f.finals,['limit fixture']);assert.equal(f.render().isStreaming,false);
});
test('actual streaming hook exposes recording and stops at 3:30',async()=>{
 const f=fixture();const pending=f.render().startStreaming();await tick();
 assert.equal(f.render().audioPhase,'recording','Stop is available while Core is becoming ready');
 f.streams[0].push({message:{case:'ready',value:{sessionId:'clock'}}});await pending;
 f.advance(209000);assert.equal(f.render().remainingSeconds,1);assert.equal(f.ends.length,0);
 f.advance(1000);await tick();
 assert.equal(f.ends.length,1);assert.equal(f.stops,1);
 f.ends[0].resolve();f.streams[0].close();await tick();assert.equal(f.render().isStreaming,false);
});
test('native and Core failures retain bounded safe reason codes',async()=>{
 const f=fixture();
 assert.equal(f.box.agyAudioError('Android microphone error (ERR_PCM_START).'),'Android microphone error (ERR_PCM_START).');
 const msg=f.box.agyAudioError({name:'ConnectError',code:8,message:'private fixture content'});
 assert(!msg.includes('private fixture'));assert(msg.includes('8'),'safe Core status is visible');
});
(async()=>{
 let failed=0;
 for(const t of cases)try{await t.fn();console.log('PASS '+t.name)}catch(e){failed++;console.error('FAIL '+t.name+': '+e.message)}
 console.log((cases.length-failed)+'/'+cases.length+' actual streaming-hook regressions passed');
 process.exitCode=failed?1:0;
})().catch(e=>{console.error(e);process.exitCode=1});
