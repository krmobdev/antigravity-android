#!/usr/bin/env python3
"""Pinned composer layout/menu hooks; existing send/model/voice logic is retained."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parent
MARKER="/* AGY_COMPOSER_KR1 */"

def once(source,old,new):
    if source.count(old)!=1:raise ValueError("Composer anchor mismatch: "+old[:65])
    return source.replace(old,new)

def transform(source):
    if MARKER in source:
        if source.count(MARKER)!=1 or "var zYb=AgyMobileContextMenu;" not in source:
            raise ValueError("Incomplete composer integration")
        return source
    source=once(source,'const BYa=A.createContext(0);',
                'const BYa=A.createContext(0),agySheetBackContext=A.createContext(null);')
    source=once(source,'className:h,finalFocus:k}){var l=A.useRef(null),m=A.useContext(BYa);DC(a,()=>{b(!1)});',
        'className:h,finalFocus:k,onBack:agyOnBack}){var agyNestedBack=A.useRef(null),agyLatestBack=A.useRef(null);var agyStepBack=()=>{if(agyNestedBack.current?.()===true)return;if(agyOnBack)agyOnBack();else b(false);};agyLatestBack.current=agyStepBack;var l=A.useRef(null),m=A.useContext(BYa);DC(a,agyStepBack);A.useEffect(()=>{if(!a)return;return agyRegisterMobileOverlay(()=>agyLatestBack.current());},[a]);')
    source=once(source,'(r||t?.reason!=="escape-key"&&t?.reason!=="outside-press"||!EC(l.current))&&b(r)',
        '(r||t?.reason!=="escape-key"&&t?.reason!=="outside-press"||!EC(l.current))&&agySheetRequestChange(r,t,b,agyStepBack)')
    source=once(source,'A.createElement(BYa.Provider,{value:m+1},e)',
        'A.createElement(BYa.Provider,{value:m+1},A.createElement(agySheetBackContext.Provider,{value:agyNestedBack},e))')
    source=once(source,'var k=y0a(!0);c=k.path.length;',
        'var k=y0a(!0);useAgyMobilePathBack(k.path.length,k.pop);c=k.path.length;')
    source=once(source,'A.createElement(EK,{open:n.open,onOpenChange:n.setIsOpen,finalFocus:h,maxHeight:"85dvh"}',
        'A.createElement(EK,{open:n.open,onOpenChange:n.setIsOpen,onBack:()=>{if(n.path.length)n.pop();else n.setIsOpen(false);},finalFocus:h,maxHeight:"85dvh"}')
    source=once(source,'function z0a({title:a,onBack:b}){return A.createElement("div",{className:"sticky top-0',
        'function z0a({title:a,onBack:b}){return A.createElement("div",{className:"agy-mobile-sheet-header sticky top-0')
    # The upstream recorder's reset cleared errors, and the surrounding UI
    # hid error messages whenever recording had already stopped.
    source=once(source,'catch(y){x(),f(gp(y)),q()}',
                'catch(y){x(),q(),f(agyAudioError(y))}')
    source=once(source,'var d5a=a=>{var [b,c]',
                'var d5a=a=>{var [agyPending,setAgyPending]=(0,A.useState)(!1),[b,c]')
    source=once(source,'try{try{q();let y=x(await w(navigator.mediaDevices.getUserMedia({audio:!0})))',
                """try{let agyPhase="getUserMedia";try{q();setAgyPending(true);
                  const agyNative=agyNativeMicStart({name:"NativePrimary"},C=>{
                    if(C){let bytes=Uint8Array.from(atob(C.data),x=>x.charCodeAt(0));
                      a(p(I.MediaSchema,{mimeType:C.mimeType,
                        payload:{case:"inlineData",value:bytes},durationSeconds:C.durationSeconds}));}
                    q();
                  },msg=>{q();f(msg)},()=>m.current);
                  if(agyNative?.recorder){
                    g.current=agyNative.recorder;n.current=Date.now();c(!0);
                    l.current=window.setTimeout(()=>r(),3E5);setAgyPending(false);return;
                  }
                  if(agyNative?.error){
                    f("Не удалось начать запись через Android ("+agyNative.error+").");
                    setAgyPending(false);return;
                  }
                  let y=x(await w(navigator.mediaDevices.getUserMedia({audio:!0})))""")
    source=once(source,'catch(y){x(),q(),f(agyAudioError(y))}}finally{w()}',
                """catch(y){x();q();let native=agyNativeMicStart(y,C=>{
                  if(C){let bytes=Uint8Array.from(atob(C.data),x=>x.charCodeAt(0));
                    a(p(I.MediaSchema,{mimeType:C.mimeType,
                       payload:{case:"inlineData",value:bytes},durationSeconds:C.durationSeconds}));}
                  q();
                },msg=>{q();f(msg)},()=>m.current);
                if(native?.recorder){g.current=native.recorder;n.current=Date.now();c(!0);
                  l.current=window.setTimeout(()=>r(),3E5)}
                else f(agyAudioStageError(agyPhase,y)+(native?.error?" / Android: "+native.error:""))
              }finally{setAgyPending(false)}}finally{w()}""")
    source=once(source,'g.current=null;k.current=[];m.current=!1;',
                'g.current?.__agyNative&&agyNativeMicCancel();g.current=null;k.current=[];m.current=!1;')
    source=once(source,'isRecording:b,\nerror:e,hasMicrophone:v}',
                'isRecording:b,starting:agyPending,\nerror:e,hasMicrophone:v}')
    # Preserve the ORIGINAL upstream voice behavior: select live speech-to-text
    # when its Core feature is available (Yf); otherwise offer audio attachment.
    # Native Android recording is a compatibility implementation ONLY for
    # the upstream audio-attachment branch, never a forced UI mode.
    # Original Core speech transcription expects raw little-endian signed
    # 16kHz PCM chunks. Android WebView's getUserMedia fails on some phones
    # even when the app has RECORD_AUDIO; use native AudioRecord as a transport
    # to THE SAME upstream streaming transcription RPC and Lexical editor.
    start='let Ya=la(await fa(navigator.mediaDevices.getUserMedia({audio:{channelCount:1,sampleRate:{ideal:48E3},echoCancellation:!0,noiseSuppression:!0}})));'
    end='Ea.connect(na);u(!0);da();'
    if source.count(start)!=1 or source.count(end)!=1:
        raise ValueError("Upstream streaming microphone anchor changed")
    start_index=source.index(start)
    end_index=source.index(end,start_index)+len(end)
    old_stream=source[start_index:end_index]
    if len(old_stream)!=811 or 'audio-streaming-processor' not in old_stream:
        raise ValueError("Unsafe upstream streaming microphone patch")
    source=once(source,old_stream,"""let agyNativePcm=agyNativePcmAvailable(),Ya=null;
      if(!agyNativePcm)
        Ya=la(await fa(navigator.mediaDevices.getUserMedia({audio:{channelCount:1,sampleRate:{ideal:48E3},echoCancellation:!0,noiseSuppression:!0}})));
      if(Za.signal.aborted){
        if(agyNativePcm)agyNativePcmStop();
        else Ya.getTracks().forEach(Ua=>Ua.stop());
      }else{
        var Aa=[];M.current=0;
        var agyPcmChunk=Ua=>{
          var lb=M.current++,cb=L.current;
          cb&&n?n.sendAudioChunk(p(Via,{sessionId:cb,data:Ua,sequenceNumber:lb})).catch(zb=>{
            console.error("[AudioStreaming] Native PCM chunk transport failed",zb?.name||"Error")
          }):Aa.push({data:Ua,seq:lb});
        };
        if(agyNativePcm){
          agyNativePcmStart(agyPcmChunk,code=>{
            ma("Ошибка распознавания Android ("+code+").");ja()
          });
        }else{
          H.current=Ya;var va=C?.audioContext;
          if(!va)throw Error("Streaming audio feature is not available");
          G.current=va;
          va.state==="suspended"&&la(await fa(va.resume()));
          var Ea=va.createMediaStreamSource(Ya),
              na=new AudioWorkletNode(va,"audio-streaming-processor");
          J.current=na;
          na.port.onmessage=Ua=>{
            if(Ua.data!=="done")agyPcmChunk(new Uint8Array(Ua.data))
          };
          Ea.connect(na);
        }
        u(!0);da();""")
    source=once(source,'ma=(0,A.useCallback)(fa=>{y(fa);ha.current?.(fa)},[]),ja=(0,A.useCallback)(()=>{H.current',
                'ma=(0,A.useCallback)(fa=>{y(fa);ha.current?.(fa)},[]),ja=(0,A.useCallback)(()=>{agyNativePcmStop();H.current')
    source=once(source,'ia=(0,A.useCallback)(()=>\n{var fa=L.current;',
                'ia=(0,A.useCallback)(()=>\n{agyNativePcmStop();var fa=L.current;')
    source=once(source,'error:x,hasMicrophone:B,isAudioStreamingAvailable:!!n&&D',
                'error:x,hasMicrophone:B||agyNativePcmAvailable(),isAudioStreamingAvailable:!!n&&D')
    source=once(source,'ma(gp(Za)),u(!1),ja()}finally{R.current=!1}',
                'ma(/^ERR_NATIVE_[A-Z_]+$/.test(Za?.message||"")?"Ошибка микрофона Android ("+Za.message+").":gp(Za)),u(!1),ja()}finally{R.current=!1}')
    source=once(source,'isRecording:Ob,error:ib,hasMicrophone:tb}=d5a(Qb)',
                'isRecording:Ob,starting:agyStarting,error:ib,hasMicrophone:tb}=d5a(Qb)')
    source=once(source,'audioPhase:Ob?"recording":"idle",error:ib',
                'audioPhase:Ob?"recording":agyStarting?"requesting":"idle",error:ib')
    source=once(source,'Ob,ib,tb])',
                'Ob,agyStarting,ib,tb])')
    source=once(source,'onClick:D,disabled:H,className:aj(t===',
                'onClick:D,disabled:H||t==="requesting",className:aj(t===')
    # MediaRecorder callback is asynchronous; snapshot the duration before
    # cleanup resets n.current. Also show a clear error for empty recordings.
    source=once(source,'B.onstop=()=>{var C=new Blob(k.current,{type:B.mimeType});',
                'B.onstop=()=>{var agySeconds=n.current?(Date.now()-n.current)/1E3:0,C=new Blob(k.current,{type:B.mimeType});if(C.size===0){q();f("Запись пуста. Проверьте микрофон и попробуйте снова.");return;}')
    source=once(source,'F=\nn.current?(Date.now()-n.current)/1E3:0',
                'F=agySeconds')
    # Explicit audio MIME is supported on the tested Android 15 WebView.
    # The fallback keeps upstream settings for older Android WebView releases.
    source=once(source,'h.current=y;let B=new MediaRecorder(y,{audioBitsPerSecond:64E3});',
                'h.current=y;agyPhase="MediaRecorder";let agyOptions={audioBitsPerSecond:64E3};if(MediaRecorder.isTypeSupported?.("audio/webm;codecs=opus"))agyOptions.mimeType="audio/webm;codecs=opus";let B=new MediaRecorder(y,agyOptions);')
    source=once(source,'B.start();c(!0);',
                'agyPhase="start";B.start();c(!0);')
    source=once(source,'B.ondataavailable=C=>{C.data.size>0&&k.current.push(C.data)};',
                'B.onerror=C=>{q();f(agyAudioStageError("recording",C?.error||C));};B.ondataavailable=C=>{C.data.size>0&&k.current.push(C.data)};')
    # Include errors and permission-waiting states in the parent header;
    # previously the error component could never render while idle.
    source=once(source,'(oe.audioPhase===\n"recording"&&!Yf||Ya.length>0',
                '((oe.audioPhase==="recording"||oe.audioPhase==="requesting")&&!Yf||!!oe.error||Ya.length>0')
    source=once(source,'g,oe.audioPhase==="recording"&&!Yf',
                'g,oe.error&&A.createElement("div",{role:"alert",className:"agy-composer-audio-error"},agyAudioError(oe.error)),oe.audioPhase==="requesting"&&!Yf&&A.createElement("div",{role:"status",className:"agy-composer-audio-pending"},"Подключение микрофона…"),oe.audioPhase==="recording"&&!Yf')
    source=once(source,'"aria-label":a?"Stop recording":"Record voice memo"',
                '"aria-label":a?"Остановить запись":"Записать голосовое сообщение"')
    source=once(source,'A.createElement("span",{"data-tooltip-id":u?q:void 0,className:"min-w-0 overflow-hidden text-ellipsis whitespace-nowrap"},t,v&&A.createElement("span",{className:"opacity-70"}," ",v))',
        'A.createElement(AgyModelName,{label:t,effort:v,tooltipId:u?q:void 0})')
    # Expose the existing Core selector on mobile, using the same conversation
    # scope and original preset/save/error handlers. Never change a preset here.
    source=once(source,'sideQuestionControl:y,permissionsSelector:v}',
        'sideQuestionControl:y,permissionsSelector:v??(!Ea?A.createElement(AgyMobilePermissions,{conversationId:O,dropdownPlacement:B}):null)}')
    source=once(source,'},ha,A.createElement(DYb,',
        '},A.createElement(AgyDesktopPermissions,{control:ha}),A.createElement(DYb,')
    source=once(source,'var ECb=A.createContext(!1),G_=({title:',
        'var ECb=A.createContext(!1),agyOriginalSettingsPage=({title:')
    source=once(source,'e=aj("w-full bg-transparent",e?"h-full overflow-y-auto":"");',
        'e=aj("agy-settings-page w-full bg-transparent",e?"h-full overflow-y-auto":"");')
    source=once(source,'className:`${h?"":"py-2 px-3"} w-full bg-transparent flex justify-between gap-4',
        'className:`agy-settings-row ${h?"":"py-2 px-3"} w-full bg-transparent flex justify-between gap-4')
    source=once(source,'children:n}){var q=rK(),[r,t]=A.useState(!!f)',
        'children:n}){var agySettings=A.useContext(agySettingsContext),agySelectId=A.useId();h=agySettings?false:h;var q=rK(),[r,t]=A.useState(!!f)')
    source=once(source,'D=A.useCallback(G=>{y||x(G);c?.(G)},[y,c])',
        'D=A.useCallback(G=>{if(k)return;y||x(G);c?.(G)},[y,c,k])')
    source=once(source,'setOpen:C,isMobile:q,nativeOnMobile:h,\nopen:v',
        'setOpen:C,isMobile:q,nativeOnMobile:h,settings:agySettings,disabled:k,listId:agySelectId,\nopen:v')
    source=once(source,'[C,q,h,v,B,D,F,l,m]);return q&&h?',
        '[C,q,h,v,B,D,F,l,m,k,agySettings,agySelectId]);return q&&h?')
    source=once(source,'return A.cloneElement(g,{onClick:h=>{g.props.onClick?.(h);\ne.setOpen(!0)}})',
        'return A.cloneElement(g,{disabled:e.disabled||g.props.disabled,className:aj(g.props.className,e.settings&&"agy-settings-select-trigger"),"aria-haspopup":e.settings?"listbox":g.props["aria-haspopup"],"aria-expanded":e.open,"aria-controls":e.settings?e.listId:void 0,onClick:h=>{if(e.disabled||g.props.disabled)return;g.props.onClick?.(h);e.setOpen(!0)}})')
    source=once(source,'return A.createElement("button",{type:"button",onClick:()=>{e.setOpen(!0)},className:aj("flex w-full cursor-pointer items-center rounded-lg border border-border bg-background px-4 py-2.5 text-left text-foreground shadow-sm",c)},b)',
        'return A.createElement("button",{type:"button",disabled:e.disabled,"aria-haspopup":e.settings?"listbox":void 0,"aria-expanded":e.open,"aria-controls":e.settings?e.listId:void 0,"aria-labelledby":e.ariaLabelledBy,"aria-describedby":e.ariaDescribedBy,onClick:()=>{if(!e.disabled)e.setOpen(!0)},className:aj("flex w-full cursor-pointer items-center rounded-lg border border-border bg-background px-4 py-2.5 text-left text-foreground shadow-sm",e.settings&&"agy-settings-select-trigger",c)},b)')
    source=once(source,'A.createElement(EK,{open:k.open,onOpenChange:k.setOpen,maxHeight:"85dvh"},\nA.createElement("div",{className:"flex flex-col gap-px overflow-y-auto overscroll-contain p-1",style:{maxHeight:"80dvh"}},a))',
        'A.createElement(EK,{open:k.open,onOpenChange:k.setOpen,maxHeight:"85dvh",className:k.settings?"agy-settings-select-sheet":void 0},k.settings&&A.createElement(AgySettingsSelectHeader,{onClose:()=>k.setOpen(false)}),A.createElement("div",{id:k.settings?k.listId:void 0,role:k.settings?"listbox":void 0,onKeyDown:k.settings?agySettingsListKeyDown:void 0,"aria-labelledby":k.ariaLabelledBy,"aria-label":k.ariaLabelledBy?void 0:"Выберите значение",className:"flex flex-col gap-px overflow-y-auto overscroll-contain p-1",style:{maxHeight:"70dvh"}},a))')
    source=once(source,'m=n.value===a,A.createElement("button",{type:"button",disabled:f,onClick:q=>{l?.(q);e?.();n.onValueChange(a);n.setOpen(!1)}',
        'm=n.value===a,A.createElement("button",{type:"button",role:n.settings?"option":void 0,"aria-selected":n.settings?m:void 0,"data-agy-select-option":n.settings?a:void 0,disabled:f||n.disabled,onClick:q=>{if(f||n.disabled)return;l?.(q);e?.();n.onValueChange(a);n.setOpen(!1)}')
    source=once(source,"var zYb=({items:a,dropdownPlacement:b,onOpenChange:c})",
                "var agyOriginalContextMenu=({items:a,dropdownPlacement:b,onOpenChange:c})")
    source=once(source,";const AYb=()=>Promise.resolve(p(kia));",
                ";"+MARKER+"\n"+(ROOT/"src/mobile/composer.js").read_text()+"\n"+(ROOT/"src/mobile/settings.js").read_text()+"\nconst AYb=()=>Promise.resolve(p(kia));")
    source=once(source,'className:"flex flex-col w-full shrink-0",style:Ja?',
                'className:"agy-composer flex flex-col w-full shrink-0",style:Ja?')
    source=once(source,'ref:Us,className:"relative flex flex-col gap-0 p-1 rounded-',
                'ref:Us,className:"agy-composer-surface relative flex flex-col gap-0 p-1 rounded-')
    source=once(source,'className:"@container/input-footer flex w-full items-center justify-between gap-1 p-0.5"',
                'className:"agy-composer-footer @container/input-footer flex w-full items-center justify-between gap-1 p-0.5"')
    settings_start='A.createElement("div",{className:"flex items-center gap-0 min-w-0"},K!==void 0'
    if source.count(settings_start)!=1:raise ValueError("Composer settings anchor mismatch")
    left=source.index(settings_start)
    settings_end=',ca)),A.createElement("div",{className:"shrink-0 flex items-center gap-0.5"}'
    right=source.index(settings_end,left)+len(',ca)')
    desktop=source[left:right]
    pair='A.createElement("div",{className:"flex items-center gap-1 min-w-0"},h,k)'
    extra=once(desktop,pair,'A.createElement("div",{className:"flex items-center gap-1 min-w-0"},k)')
    source=source[:left]+'A.createElement(AgyComposerModelControl,{primary:h,permissions:ha,desktop:'+desktop+'})'+source[right:]
    source=once(source,'A.createElement(zYb,{items:ia,dropdownPlacement:r,onOpenChange:Ma=>{Ma&&la()}})',
        'A.createElement(zYb,{items:ia,extraControls:'+extra+',dropdownPlacement:r,onOpenChange:Ma=>{Ma&&la()}})')
    return source

if __name__=="__main__":
    p=Path(sys.argv[1]);s=p.read_text();result=transform(s)
    if result!=s:p.write_text(result)
    print("mobile composer integration: OK")
