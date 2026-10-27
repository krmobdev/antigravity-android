"""Generation ownership and complete audio delivery for pinned upstream dictation."""
def transform_voice_lifecycle(source, once):
    start=source.index('var dO=({editorRef:')
    end=source.index(';function D5a',start)
    hook=source[start:end]
    def replace(old,new):
        nonlocal hook
        hook=once(hook,old,new)
    replace('R=(0,A.useRef)(!1),[S,U]',
        'R=(0,A.useRef)(!1),agyEnding=(0,A.useRef)(null),agyRun=(0,A.useRef)(null),agyEpoch=(0,A.useRef)(0),[agyStarting,setAgyStarting]=(0,A.useState)(false),[agyFinishing,setAgyFinishing]=(0,A.useState)(false),[S,U]')
    replace('ja=(0,A.useCallback)(()=>{agyNativePcmStop();H.current',
        'ja=(0,A.useCallback)(()=>{agyEpoch.current++;agyEnding.current?.finish();agyRun.current?.delivery?.cancel();agyRun.current=null;agyNativePcmStop();H.current')
    replace('R.current=!0;try{ja();y(null);',
        'R.current=!0;let agyOwner=null,agyTicket=agyEpoch.current;try{setAgyStarting(true);if(agyEnding.current)await agyEnding.current.promise;if(agyTicket!==agyEpoch.current)return;ja();y(null);')
    replace('let Za=new AbortController;K.current=Za;let agyNativePcm=',
        'let Za=new AbortController;agyOwner=Za;K.current=Za;const agyThisRun={owner:Za,delivery:null,stopRequested:false,hasFinal:false,captureDone:Promise.resolve()};agyRun.current=agyThisRun;let agyNativePcm=')
    replace('if(Za.signal.aborted){','if(Za.signal.aborted||K.current!==Za){')
    begin=hook.index('        var Aa=[];M.current=0;\n        var agyPcmChunk=')
    finish=hook.index('        if(agyNativePcm){',begin)
    hook=hook[:begin]+'''        M.current=0;
        const agyDelivery=agyCreateDictationDelivery(Za.signal,
          (id,data,sequenceNumber)=>n.sendAudioChunk(p(Via,{sessionId:id,data,sequenceNumber})),
          error=>{if(K.current===Za&&!Za.signal.aborted){ma(agyAudioError(error));ja()}});
        agyThisRun.delivery=agyDelivery;
        var agyPcmChunk=Ua=>{
          if(Za.signal.aborted||K.current!==Za)return;
          agyDelivery.add(Ua,M.current++);
        };
'''+hook[finish:]
    replace('J.current=na;\n          na.port.onmessage=Ua=>{\n            if(Ua.data!=="done")agyPcmChunk(new Uint8Array(Ua.data))\n          };',
        'J.current=na;agyThisRun.captureDone=new Promise(resolve=>{agyThisRun.finishCapture=resolve});\n          na.port.onmessage=Ua=>{if(Ua.data==="done")agyThisRun.finishCapture();else agyPcmChunk(new Uint8Array(Ua.data))};')
    replace('ma("Android transcription error ("+code+").");ja()',
        'if(K.current!==Za||Za.signal.aborted)return;if(code==="ERR_PCM_LIMIT"){ia();return;}ma(agyAudioError(Error(code)));ja()')
    replace('let Oa=Ga.value.text;Oa&&ea.current&&ea.current(Oa);',
        'let Oa=Ga.value.text;if(Oa.trim())agyThisRun.hasFinal=true;agyEnding.current?.owner===Za&&agyEnding.current.finish();Oa&&ea.current&&ea.current(Oa);')
    replace('let Ga=Qa.message;',
        'if(K.current!==Za||Za.signal.aborted)break;let Ga=Qa.message;')
    replace('zb();lb(Error("Stream closed before session was ready"));u(!1);w(Qa=>!Qa.isFinal&&Qa.text?{text:Qa.text,\nisFinal:!0}:Qa)',
        'zb();lb(Error("ERR_CORE_READY_CLOSED"));if(K.current===Za&&!Za.signal.aborted){agyNativePcmStop();O.current!==null&&(window.clearTimeout(O.current),O.current=null);if(agyEnding.current?.owner===Za)agyEnding.current.finish();else if(!agyThisRun.hasFinal)ma(agyAudioError(Error("ERR_CORE_NO_TRANSCRIPT")));L.current=null;qa();u(false);w(Qa=>!Qa.isFinal&&Qa.text?{text:Qa.text,isFinal:true}:Qa)}')
    replace('Za.signal.aborted||Qa instanceof Error&&Qa.name==="AbortError"||',
        'K.current!==Za||Za.signal.aborted||Qa instanceof Error&&Qa.name==="AbortError"||')
    replace('console.error("[AudioStreaming] Stream error:",Qa),ma(gp(Qa)),',
        'console.error("[AudioStreaming] Stream error",Qa?.name||"Error"),agyNativePcmStop(),agyEnding.current?.owner===Za&&agyEnding.current.finish(),ma(agyAudioError(Qa)),')
    replace('Ia=la(await fa(Ja));if(Za.signal.aborted)u(!1);',
        'Ia=la(await fa(agyDictationWait(Ja,20000,"ERR_CORE_READY_TIMEOUT",Za.signal)));if(Za.signal.aborted||K.current!==Za){}')
    begin=hook.index('for(let Ua of Aa)n.sendAudioChunk(')
    finish=hook.index('Aa.length=0',begin)+len('Aa.length=0')
    old=hook[begin:finish]
    if len(old)!=194:raise ValueError("Buffered audio delivery anchor changed: "+str(len(old)))
    replace(old,'agyDelivery.ready(Ia)')
    replace('catch(Za){la(),console.error("[AudioStreaming] Failed to start:",Za),ma(/^ERR_NATIVE_[A-Z_]+$/.test(Za?.message||"")?"Android microphone error ("+Za.message+").":gp(Za)),u(!1),ja()}finally{R.current=!1}',
        'catch(Za){la();if((agyOwner?K.current===agyOwner:agyTicket===agyEpoch.current)&&!agyOwner?.signal.aborted){console.error("[AudioStreaming] Failed to start",Za?.name||"Error");ma(agyAudioError(Za));u(false);ja()}}finally{R.current=false;setAgyStarting(false)}')
    begin=hook.index('ia=(0,A.useCallback)(()=>\n{agyNativePcmStop();var fa=L.current;')
    finish=hook.index(',ra=(0,A.useCallback)',begin)
    hook=hook[:begin]+'''ia=(0,A.useCallback)(()=>{
      const run=agyRun.current,owner=K.current;
      if(!run?.delivery||run.owner!==owner||owner.signal.aborted){ja();return;}
      if(run.stopRequested)return;
      run.stopRequested=true;
      // Cancel uses ja; Stop preserves queued audio for transcription.
      agyNativePcmStop(true);
      if(K.current!==owner||owner.signal.aborted)return;
      J.current&&J.current.port.postMessage("stop");
      H.current&&(H.current.getTracks().forEach(la=>la.stop()),H.current=null);
      qa();setAgyFinishing(true);
      let finish;
      const finalized=new Promise(resolve=>{finish=resolve});
      const ending={owner,finish,promise:null};
      agyEnding.current=ending;
      ending.promise=agyDictationWait((async()=>{
        await agyDictationWait(run.captureDone,2000,"ERR_CORE_CAPTURE_STOP_TIMEOUT",owner.signal);
        if(K.current===owner&&J.current){J.current.disconnect();J.current=null;}
        const id=await run.delivery.drain();
        if(owner.signal.aborted)throw eO(owner.signal);
        await agyEndDictationSession(()=>n.endAudioSession(p(Wia,{sessionId:id})),owner.signal);
        await finalized;
        if(!run.hasFinal)throw Error("ERR_CORE_NO_TRANSCRIPT");
      })(),30000,"ERR_CORE_RESULT_TIMEOUT",owner.signal);
      ending.promise.catch(error=>{
        if(K.current!==owner||owner.signal.aborted)return;
        owner.abort();K.current=null;L.current=null;J.current?.disconnect();J.current=null;u(false);
        w(la=>la.text&&!la.isFinal?{text:la.text,isFinal:true}:la);
        ma(agyAudioError(error));
      });
      const settled=()=>{if(agyEnding.current===ending){agyEnding.current=null;setAgyFinishing(false)}};
      ending.promise.then(settled,settled);
    },[n,ja,qa,ma])'''+hook[finish:]
    begin=hook.index('(0,A.useEffect)(()=>{var fa=null;q&&(fa=window.setTimeout(')
    finish=hook.index('[q,e]);',begin)+len('[q,e]);')
    hook=hook[:begin]+'var agyRemaining=useAgyDictationClock(q,ia);'+hook[finish:]
    replace('audioPhase:S?"submitting":ua?"finalizing":q?"recording":"idle"',
        'remainingSeconds:agyRemaining,audioPhase:S?"submitting":q?"recording":agyFinishing||ua?"finalizing":agyStarting?"requesting":"idle"')
    return source[:start]+hook+source[end:]
