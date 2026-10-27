/* Execute the actual version-pinned, patched dO hook, not a replacement recorder. */
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const bundle=fs.readFileSync(0,'utf8');
const start=bundle.indexOf('var dO=({editorRef:');
const end=bundle.indexOf(';function D5a',start);
assert(start>=0&&end>start,'actual streaming hook');
const hookEnd=bundle.indexOf(';function F5a',end);
assert(hookEnd>end);
const helper=fs.readFileSync(process.env.AGY_VOICE_HELPER||'web/src/mobile/composer.js','utf8').split('/* Compact mobile composer')[0];
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
 const rpc={
  streamAudioTranscription:(...args)=>api.streamAudioTranscription(...args),
  sendAudioChunk:(...args)=>api.sendAudioChunk(...args),
  endAudioSession:(...args)=>api.endAudioSession(...args)
 };
 const box={A,Cy:()=>rpc,c5a:()=>true,Lj:()=>new Map([['streamingAudioFeature',{enabled:true}]]),
  Pj:x=>x,C5a:()=>({waitForNextTrue:P,flush:Q}),d:x=>x,p:(schema,x)=>x,Uia:{},Via:{},Wia:{},
  AbortController,DOMException,Error,Uint8Array,atob:x=>Buffer.from(x,'base64').toString('binary'),
  console:{error(){},warn(){}},gp:x=>x?.message||String(x),
  performance:{now:()=>now},Date:{now:()=>now},
  setTimeout(fn,ms){const id=++nextTimer;timers.set(id,{fn,at:now+ms});return id},
  clearTimeout(id){timers.delete(id)},setInterval(){throw Error('unexpected interval')},
  AndroidBridge:native,__AG_NATIVE_BRIDGE_TOKEN__:'test-only'};
 box.window=box;vm.createContext(box);vm.runInContext(helper+'\n'+bundle.slice(start,hookEnd),box);
 const render=()=>{cursor=0;const result=box.dO({onError:e=>errors.push(e),onFinalTranscript:t=>finals.push(t)});for(const run of effects.splice(0))run();return result};
 return {render,streams,ends,errors,finals,chunks,box,api,
  get starts(){return nativeStarts},get stops(){return nativeStops},
  async ready(id='session'){const promise=render().startStreaming();await tick();const s=streams.at(-1);
   assert(s,'Core streaming started');s.push({message:{case:'ready',value:{sessionId:id}}});await promise;await tick();return s},
  advance(ms){now+=ms;for(const [id,t]of [...timers])if(t.at<=now){timers.delete(id);t.fn()}}
 };
}
const cases=[];
function test(name,fn){cases.push({name,fn})}
test('explicit Cancel before Core ready does not show an error',async()=>{
 const f=fixture(),started=f.render().startStreaming();await tick();
 f.render().cancelStreaming();
 const e=new Error('cancelled');e.name='AbortError';f.streams[0].fail(e);
 await started;await tick();
 assert.equal(f.errors.length,0,'cancelled startup must not become UnknownError');
 assert.equal(f.stops,1,'native device released once');
});
test('a new capture waits for the previous endAudioSession acknowledgement',async()=>{
 const f=fixture();await f.ready('first');f.render().stopStreaming();await tick();
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
 const f=fixture();const old=await f.ready('old');f.render().stopStreaming();await tick();f.ends[0].resolve();f.render().cancelStreaming();await tick();
 await f.ready('new');old.fail(new Error('late old response'));await tick();
 assert.equal(f.errors.length,0,'stale stream must be silent');
 assert.equal(f.render().isStreaming,true);
 assert.equal(f.stops,1,'new microphone stays running');f.render().cancelStreaming();
});
test('late old transcript must not enter the next prompt',async()=>{
 const f=fixture(),old=await f.ready('old');f.render().stopStreaming();await tick();f.ends[0].resolve();f.render().cancelStreaming();await tick();
 await f.ready('new');old.push({message:{case:'transcription',value:{text:'stale fixture',isFinal:true}}});await tick();
 assert.deepEqual(f.finals,[]);f.render().cancelStreaming();
});
test('ten record-stop-record cycles retain generation ownership',async()=>{
 const f=fixture();
 for(let i=0;i<10;i++){
  const s=await f.ready('cycle-'+i);
  assert.equal(f.render().isStreaming,true);
  f.render().stopStreaming();await tick();f.ends.at(-1).resolve();await tick();
  s.push({message:{case:'transcription',value:{text:'cycle fixture',isFinal:true}}});s.close();await tick();
 }
 assert.equal(f.finals.length,10);
 assert.equal(f.starts,10);assert.equal(f.stops,10);assert.deepEqual(f.errors,[]);
});
test('double Stop sends one Core end request',async()=>{
 const f=fixture(),s=await f.ready('once');
 f.render().stopStreaming();await tick();f.render().stopStreaming();await tick();
 assert.equal(f.ends.length,1);assert.equal(f.stops,1);
 f.ends[0].resolve();s.close();await tick();
});
test('cancel while waiting for finalization prevents a queued restart',async()=>{
 const f=fixture(),s=await f.ready('first');f.render().stopStreaming();await tick();
 const pending=f.render().startStreaming();await tick();
 assert.equal(f.starts,1);
 f.render().cancelStreaming();f.ends[0].resolve();s.close();
 await pending;await tick();
 assert.equal(f.starts,1);assert.deepEqual(f.errors,[]);
});
test('a timed out Core stop is bounded and allows a subsequent retry',async()=>{
 const f=fixture();await f.ready('timeout');f.render().stopStreaming();await tick();
 const next=f.render().startStreaming();await tick();
 f.advance(20000);await next;await tick();
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
test('a final transcript after four seconds is retained',async()=>{
 const f=fixture(),stream=await f.ready('slow');
 f.render().stopStreaming();await tick();await tick();f.ends[0].resolve();f.advance(4000);await tick();
 stream.push({message:{case:'transcription',value:{text:'delayed fixture',isFinal:true}}});await tick();
 assert.deepEqual(f.finals,['delayed fixture'],'a normal server delay must not discard speech');
 assert.deepEqual(f.errors,[]);
});
test('Stop before Core ready delivers the buffered short recording',async()=>{
 const f=fixture(),started=f.render().startStreaming();await tick();
 let frame='AQIDBA==';f.box.AndroidBridge.readNativePcm=()=>{const x=frame;frame='';return x};
 f.advance(45);f.render().stopStreaming();await tick();
 f.streams[0].push({message:{case:'ready',value:{sessionId:'short'}}});await started;await tick();
 assert.equal(f.chunks.length,1,'buffered speech is delivered after ready');
 assert.equal(f.ends.length,1,'Stop finalizes instead of canceling a pending handshake');
 f.ends[0].resolve();
 f.streams[0].push({message:{case:'transcription',value:{text:'short fixture',isFinal:true}}});await tick();
 assert.deepEqual(f.finals,['short fixture']);assert.deepEqual(f.errors,[]);
});
test('Core end waits for all pending audio chunk acknowledgements',async()=>{
 const f=fixture(),stream=await f.ready('transport'),sent=deferred();
 f.api.sendAudioChunk=chunk=>{f.chunks.push(chunk);return sent.promise};
 let frame='AQIDBA==';f.box.AndroidBridge.readNativePcm=()=>{const x=frame;frame='';return x};
 f.advance(45);f.render().stopStreaming();await tick();await tick();
 assert.equal(f.ends.length,0,'end cannot overtake the last audio request');
 sent.resolve();await tick();assert.equal(f.ends.length,1);
 f.ends[0].resolve();stream.push({message:{case:'transcription',value:{text:'delivered fixture',isFinal:true}}});await tick();
 assert.deepEqual(f.finals,['delivered fixture']);
});
test('a chunk transport failure is surfaced rather than silently ending empty',async()=>{
 const f=fixture();await f.ready('failed-send');
 f.api.sendAudioChunk=()=>Promise.reject(Object.assign(new Error('private fixture'),{code:14}));
 f.box.AndroidBridge.readNativePcm=()=>'AQIDBA==';
 f.advance(45);await tick();
 assert(f.errors.length>0,'failed audio transport must be visible');
 assert(f.errors.every(x=>!x.includes('private fixture')));
 f.render().cancelStreaming();
});
test('an empty completed stream reports a missing transcription',async()=>{
 const f=fixture(),stream=await f.ready('empty');
 f.render().stopStreaming();await tick();await tick();f.ends[0].resolve();stream.close();await tick();
 assert(f.errors.length>0,'do not report empty completion as successful dictation');
});
test('Stop drains queued native PCM before releasing capture',async()=>{
 const f=fixture(),stream=await f.ready('tail');
 let frame='AQIDBA==';f.box.AndroidBridge.readNativePcm=()=>{const x=frame;frame='';return x};
 f.render().stopStreaming();await tick();await tick();
 assert.equal(f.chunks.length,1,'tail queued between polls must not be discarded');
 f.ends[0].resolve();stream.push({message:{case:'transcription',value:{text:'tail fixture',isFinal:true}}});await tick();
 assert.deepEqual(f.finals,['tail fixture']);
});
test('browser fallback waits for the worklet final chunk and done marker',async()=>{
 const f=fixture();let worklet,trackStops=0;
 f.box.AndroidBridge=null;
 f.box.navigator={mediaDevices:{getUserMedia:async()=>({getTracks:()=>[{stop(){trackStops++}}]})}};
 const context={state:'running',createMediaStreamSource:()=>({connect(){}})};
 f.box.Lj=()=>new Map([['streamingAudioFeature',{enabled:true,audioContext:context}]]);
 f.box.AudioWorkletNode=class{constructor(){worklet=this;this.port={postMessage(){}}}disconnect(){}};
 const stream=await f.ready('browser');
 f.render().stopStreaming();await tick();
 assert.equal(f.ends.length,0,'wait for final browser audio instead of ending early');
 worklet.port.onmessage({data:new Uint8Array([1,2,3,4]).buffer});
 worklet.port.onmessage({data:'done'});await tick();
 assert.equal(f.chunks.length,1);assert.equal(f.ends.length,1);
 f.ends[0].resolve();stream.push({message:{case:'transcription',value:{text:'browser fixture',isFinal:true}}});await tick();
 assert.deepEqual(f.finals,['browser fixture']);assert.equal(trackStops,1);assert.deepEqual(f.errors,[]);
});
test('missing Core readiness after Stop reports an error and frees capture',async()=>{
 const f=fixture(),started=f.render().startStreaming();await tick();
 f.render().stopStreaming();await tick();f.advance(20000);await started;await tick();
 assert.equal(f.stops,1);assert.equal(f.ends.length,0);
 assert(f.errors.some(e=>e.includes('did not become ready')));
});
test('a missing final result times out explicitly after capture has stopped',async()=>{
 const f=fixture();await f.ready('no-result');f.render().stopStreaming();await tick();
 f.ends[0].resolve();await tick();assert.equal(f.stops,1);
 f.advance(30000);await tick();
 assert(f.errors.some(e=>e==='Transcription timed out. Please try again.'));
 assert.deepEqual(f.finals,[]);assert.equal(f.render().audioPhase,'idle');
});
test('delivery buffers and in-flight audio requests remain bounded',async()=>{
 const f=fixture(),errors=[];
 let owner=new AbortController();require('events').setMaxListeners(0,owner.signal);
 let delivery=f.box.agyCreateDictationDelivery(owner.signal,()=>new Promise(()=>{}),e=>{errors.push(e.message);owner.abort()});
 delivery.add(new Uint8Array(6720001),0);assert.deepEqual(errors,['ERR_CORE_AUDIO_BUFFER']);
 owner=new AbortController();require('events').setMaxListeners(0,owner.signal);errors.length=0;
 delivery=f.box.agyCreateDictationDelivery(owner.signal,()=>new Promise(()=>{}),e=>{errors.push(e.message);owner.abort()});
 delivery.ready('bounded');
 for(let i=0;i<129;i++)delivery.add(new Uint8Array(2),i);
 await tick();assert.deepEqual(errors,['ERR_CORE_CHUNK_BACKLOG']);
});
test('a stalled audio request fails explicitly and cancel stays silent',async()=>{
 const f=fixture(),errors=[];let owner=new AbortController();
 let delivery=f.box.agyCreateDictationDelivery(owner.signal,()=>new Promise(()=>{}),e=>{errors.push(e.message);owner.abort()});
 delivery.ready('stalled');delivery.add(new Uint8Array(2),0);
 f.advance(20000);await tick();assert.equal(errors.length,1);
 owner=new AbortController();errors.length=0;
 delivery=f.box.agyCreateDictationDelivery(owner.signal,()=>new Promise(()=>{}),e=>errors.push(e.message));
 delivery.ready('cancelled');delivery.add(new Uint8Array(2),0);
 const draining=delivery.drain().catch(e=>e.name);owner.abort();
 assert.equal(await draining,'AbortError');f.advance(30000);await tick();assert.deepEqual(errors,[]);
});
test('native PCM decoding rejects malformed odd-sized frames',async()=>{
 const f=fixture();
 assert.deepEqual(Array.from(f.box.agyDecodeNativePcm('AQIDBA==')),[1,2,3,4]);
 assert.throws(()=>f.box.agyDecodeNativePcm('AQID'),/ERR_NATIVE_PCM_FRAME/);
});
test('native and Core failures retain bounded safe reason codes',async()=>{
 const f=fixture();
 assert.equal(f.box.agyAudioError('Android microphone error (ERR_PCM_START).'),'Android microphone error (ERR_PCM_START).');
 const msg=f.box.agyAudioError({name:'ConnectError',code:8,message:'private fixture content'});
 assert(!msg.includes('private fixture'));assert(msg.includes('8'),'safe Core status is visible');
});
(async()=>{
 let failed=0;
 const selected=process.env.AGY_VOICE_CASE_PATTERN?cases.filter(t=>new RegExp(process.env.AGY_VOICE_CASE_PATTERN).test(t.name)):cases;
 for(const t of selected)try{
  let watchdog;
  try{await Promise.race([t.fn(),new Promise((_,reject)=>{watchdog=setTimeout(()=>reject(Error("test did not settle")),2500)})])}
  finally{clearTimeout(watchdog)}
  console.log('PASS '+t.name)}catch(e){failed++;console.error('FAIL '+t.name+': '+e.message)}
 console.log((selected.length-failed)+'/'+selected.length+' actual streaming-hook regressions passed');
 process.exitCode=failed?1:0;
})().catch(e=>{console.error(e);process.exitCode=1});
