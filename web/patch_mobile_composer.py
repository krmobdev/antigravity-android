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
    source=once(source,'className:h,finalFocus:k}){var l=A.useRef(null),m=A.useContext(BYa);DC(a,()=>{b(!1)});',
        'className:h,finalFocus:k,onBack:agyOnBack}){var l=A.useRef(null),m=A.useContext(BYa);DC(a,()=>{b(!1)});A.useEffect(()=>{if(!a)return;return agyRegisterMobileOverlay(()=>{if(agyOnBack)agyOnBack();else b(false);});},[a,b,agyOnBack]);')
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
    source=once(source,"var zYb=({items:a,dropdownPlacement:b,onOpenChange:c})",
                "var agyOriginalContextMenu=({items:a,dropdownPlacement:b,onOpenChange:c})")
    source=once(source,";const AYb=()=>Promise.resolve(p(kia));",
                ";"+MARKER+"\n"+(ROOT/"src/mobile/composer.js").read_text()+"\nconst AYb=()=>Promise.resolve(p(kia));")
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
    source=source[:left]+'A.createElement(AgyComposerModelControl,{primary:h,desktop:'+desktop+'})'+source[right:]
    source=once(source,'A.createElement(zYb,{items:ia,dropdownPlacement:r,onOpenChange:Ma=>{Ma&&la()}})',
        'A.createElement(zYb,{items:ia,extraControls:'+extra+',dropdownPlacement:r,onOpenChange:Ma=>{Ma&&la()}})')
    return source

if __name__=="__main__":
    p=Path(sys.argv[1]);s=p.read_text();result=transform(s)
    if result!=s:p.write_text(result)
    print("mobile composer integration: OK")
