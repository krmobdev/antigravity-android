"""Generation ownership and drain ordering for the pinned upstream dictation hook."""
def transform_voice_lifecycle(source, once):
    start=source.index('var dO=({editorRef:')
    end=source.index(';function D5a',start)
    hook=source[start:end]
    def replace(old,new):
        nonlocal hook
        hook=once(hook,old,new)
    replace('R=(0,A.useRef)(!1),[S,U]',
        'R=(0,A.useRef)(!1),agyEnding=(0,A.useRef)(null),agyEpoch=(0,A.useRef)(0),[agyStarting,setAgyStarting]=(0,A.useState)(false),[agyFinishing,setAgyFinishing]=(0,A.useState)(false),[S,U]')
    replace('ja=(0,A.useCallback)(()=>{agyNativePcmStop();H.current',
        'ja=(0,A.useCallback)(()=>{agyEpoch.current++;agyEnding.current?.finish();agyNativePcmStop();H.current')
    replace('R.current=!0;try{ja();y(null);',
        'R.current=!0;let agyOwner=null,agyTicket=agyEpoch.current;try{setAgyStarting(true);if(agyEnding.current)await agyEnding.current.promise;if(agyTicket!==agyEpoch.current)return;ja();y(null);')
    replace('let Za=new AbortController;K.current=Za;let agyNativePcm=',
        'let Za=new AbortController;agyOwner=Za;K.current=Za;let agyNativePcm=')
    replace('if(Za.signal.aborted){','if(Za.signal.aborted||K.current!==Za){')
    replace('var agyPcmChunk=Ua=>{\n          var lb=',
        'var agyPcmChunk=Ua=>{\n          if(Za.signal.aborted||K.current!==Za)return;\n          var lb=')
    replace('ma("Android transcription error ("+code+").");ja()',
        'if(K.current!==Za||Za.signal.aborted)return;if(code==="ERR_PCM_LIMIT"){ia();return;}ma(agyAudioError(Error(code)));ja()')
    replace('let Oa=Ga.value.text;Oa&&ea.current&&ea.current(Oa);',
        'agyEnding.current?.owner===Za&&agyEnding.current.finish();let Oa=Ga.value.text;Oa&&ea.current&&ea.current(Oa);')
    replace('let Ga=Qa.message;',
        'if(K.current!==Za||Za.signal.aborted)break;let Ga=Qa.message;')
    replace('zb();lb(Error("Stream closed before session was ready"));u(!1);w(Qa=>!Qa.isFinal&&Qa.text?{text:Qa.text,\nisFinal:!0}:Qa)',
        'zb();lb(Error("Stream closed before session was ready"));if(K.current===Za&&!Za.signal.aborted){agyNativePcmStop();O.current!==null&&(window.clearTimeout(O.current),O.current=null);agyEnding.current?.owner===Za&&agyEnding.current.finish();L.current=null;qa();u(false);w(Qa=>!Qa.isFinal&&Qa.text?{text:Qa.text,isFinal:true}:Qa)}')
    replace('Za.signal.aborted||Qa instanceof Error&&Qa.name==="AbortError"||',
        'K.current!==Za||Za.signal.aborted||Qa instanceof Error&&Qa.name==="AbortError"||')
    replace('console.error("[AudioStreaming] Stream error:",Qa),ma(gp(Qa)),',
        'console.error("[AudioStreaming] Stream error",Qa?.name||"Error"),agyNativePcmStop(),agyEnding.current?.owner===Za&&agyEnding.current.finish(),ma(agyAudioError(Qa)),')
    replace('Ia=la(await fa(Ja));if(Za.signal.aborted)u(!1);',
        'Ia=la(await fa(fO(Ja,Za.signal)));if(Za.signal.aborted||K.current!==Za){}')
    replace('catch(Za){la(),console.error("[AudioStreaming] Failed to start:",Za),ma(/^ERR_NATIVE_[A-Z_]+$/.test(Za?.message||"")?"Android microphone error ("+Za.message+").":gp(Za)),u(!1),ja()}finally{R.current=!1}',
        'catch(Za){la();if((agyOwner?K.current===agyOwner:agyTicket===agyEpoch.current)&&!agyOwner?.signal.aborted){console.error("[AudioStreaming] Failed to start",Za?.name||"Error");ma(agyAudioError(Za));u(false);ja()}}finally{R.current=false;setAgyStarting(false)}')
    begin=hook.index('ia=(0,A.useCallback)(()=>\n{agyNativePcmStop();var fa=L.current;')
    finish=hook.index(',ra=(0,A.useCallback)',begin)
    hook=hook[:begin]+'''ia=(0,A.useCallback)(()=>{
      agyNativePcmStop();
      var fa=L.current,agyOwner=K.current;
      if(fa&&n){
        if(agyEnding.current?.owner===agyOwner)return;
        J.current&&J.current.port.postMessage("stop");
        H.current&&(H.current.getTracks().forEach(la=>la.stop()),H.current=null);
        let finish;
        const finalized=new Promise(resolve=>{finish=resolve});
        const ending={owner:agyOwner,finish,promise:agyEndDictationSession(()=>n.endAudioSession(p(Wia,{sessionId:fa}))).then(()=>finalized)};
        agyEnding.current=ending;setAgyFinishing(true);
        ending.promise.catch(error=>{
          if(K.current===agyOwner&&!agyOwner?.signal.aborted)ma(agyAudioError(error));
        });
        const settled=()=>{if(agyEnding.current===ending){agyEnding.current=null;setAgyFinishing(false)}};
        ending.promise.then(settled,settled);
        O.current!==null&&window.clearTimeout(O.current);
        O.current=window.setTimeout(()=>{
          if(K.current!==agyOwner)return;
          O.current=null;ending.finish();K.current?.abort();K.current=null;L.current=null;u(false);
          w(la=>la.text&&!la.isFinal?{text:la.text,isFinal:true}:la);
        },3000);
        qa();
      }else ja();
    },[n,ja,qa,ma])'''+hook[finish:]
    begin=hook.index('(0,A.useEffect)(()=>{var fa=null;q&&(fa=window.setTimeout(')
    finish=hook.index('[q,e]);',begin)+len('[q,e]);')
    hook=hook[:begin]+'var agyRemaining=useAgyDictationClock(q,ia);'+hook[finish:]
    replace('audioPhase:S?"submitting":ua?"finalizing":q?"recording":"idle"',
        'remainingSeconds:agyRemaining,audioPhase:S?"submitting":q?"recording":agyStarting?"requesting":agyFinishing||ua?"finalizing":"idle"')
    return source[:start]+hook+source[end:]
