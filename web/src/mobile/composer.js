window.__AGY_AUDIO_DIAG_BUILD__="2026-10-27-pcm-delivery-v8";
function agyAudioError(error){
  // Native recorder already stores an English message. Reformatting it
  // used to erase the real reason and show the same generic error every time.
  const known=[
    "Microphone access denied. Allow audio recording in app settings.",
    "Microphone not found. Check your device connection.",
    "Microphone is busy or unavailable. Close other recording apps.",
    "Voice recording is not supported by this Android WebView. Check for a WebView update.",
    "WebView blocked microphone access. Check your security settings.",
    "Recording was interrupted. Try again.",
    "Recording is empty. Check your microphone and try again.",
    "Recording failed. Check your microphone and try again."
  ];
  if(typeof error==="string" && known.includes(error)) return error;
  if(typeof error==="string" && error.startsWith("Recording failed during ")) return error;
  // Android's native microphone returns bounded error codes. Retain them
  // across the outer UI formatter instead of disguising them as UnknownError.
  if(typeof error==="string" &&
    /^Failed to (start|finish) Android recording \(ERR_[A-Z_]{2,48}\)\.$/.test(error))
    return error;
  if(typeof error==="string" &&
    (/^Android (microphone|transcription) error \(ERR_[A-Z_]{2,48}\)\.$/.test(error) ||
     /^Transcription failed \(Core status [0-9]{1,2}\)\. Try recording again\.$/.test(error) ||
     error==="Previous dictation is still finishing. Try again."))return error;
  const dictationErrors={"ERR_CORE_CAPTURE_STOP_TIMEOUT":"Audio capture did not finish. Please try again.","ERR_CORE_READY_TIMEOUT": "Transcription did not become ready. Please try again.", "ERR_CORE_READY_CLOSED": "Transcription closed before it was ready. Please try again.", "ERR_CORE_CHUNK_TIMEOUT": "Could not deliver the recording for transcription. Please try again.", "ERR_CORE_CHUNK_SEND": "Could not deliver the recording for transcription. Please try again.", "ERR_CORE_CHUNK_BACKLOG": "Transcription connection is too slow. Please try again.", "ERR_CORE_AUDIO_BUFFER": "Transcription audio buffer is full. Please try again.", "ERR_CORE_RESULT_TIMEOUT": "Transcription timed out. Please try again.", "ERR_CORE_NO_TRANSCRIPT": "No transcription was returned. Please try again."};
  if(typeof error==="string"&&Object.values(dictationErrors).includes(error))return error;
  const code=error?.message;
  if(typeof code==="string"&&Object.prototype.hasOwnProperty.call(dictationErrors,code))return dictationErrors[code];
  if(typeof code==="string"&&/^ERR_[A-Z_]{2,48}$/.test(code)){
    if(code==="ERR_CORE_END_TIMEOUT")return "Previous dictation is still finishing. Try again.";
    return "Android microphone error ("+code+").";
  }
  if(Number.isInteger(error?.code)&&error.code>=0&&error.code<=16)
    return "Transcription failed (Core status "+error.code+"). Try recording again.";
  const name=error?.name||"";
  if(name==="NotAllowedError"||name==="PermissionDeniedError")
    return "Microphone access denied. Allow audio recording in app settings.";
  if(name==="NotFoundError"||name==="DevicesNotFoundError")
    return "Microphone not found. Check your device connection.";
  if(name==="NotReadableError"||name==="TrackStartError")
    return "Microphone is busy or unavailable. Close other recording apps.";
  if(name==="NotSupportedError"||name==="TypeError")
    return "Voice recording is not supported by this Android WebView. Check for a WebView update.";
  if(name==="SecurityError")
    return "WebView blocked microphone access. Check your security settings.";
  if(name==="AbortError")
    return "Recording was interrupted. Try again.";
  // Return a safe exception class, never an arbitrary browser error message
  // that might include private paths or account data.
  const safeClass=typeof name==="string"&&/^[A-Za-z]{2,36}Error$/.test(name)?name:"UnknownError";
  return "Could not start recording ("+safeClass+"). Check microphone support in Android WebView.";
}
function agyAudioStageError(stage,error){
  const names={
    getUserMedia:"Android audio capture",
    MediaRecorder:"recorder creation",
    start:"recording startup",
    recording:"audio recording"
  };
  const place=names[stage]||"recording preparation";
  // Report only safe type and phase, never a raw browser error with private data.
  const raw=error?.name||error?.constructor?.name||typeof error;
  const kind=typeof raw==="string"&&/^[A-Za-z][A-Za-z0-9]{1,35}$/.test(raw)?raw:"UnknownError";
  return "Recording failed during "+place+" ("+kind+"). "+agyAudioError(error);
}
/**
 * Recording fallback uses Android's native AAC/MP4 recorder only after
 * browser getUserMedia fails. User initiation and native bridge token required.
 * Never bypass an explicit browser permission denial.
 */
function agyNativeMicAvailable(){
  const api=window.AndroidBridge||window.Android;
  return typeof window.__AG_NATIVE_BRIDGE_TOKEN__==="string"
      && typeof api?.startNativeVoice==="function";
}
function agyNativeMicStart(browserError,onFinished,onFailure,isCanceled){
  const reason=browserError?.name||"";
  if(reason==="NotAllowedError"||reason==="SecurityError")return null;
  const api=window.AndroidBridge||window.Android;
  const token=window.__AG_NATIVE_BRIDGE_TOKEN__;
  if(!token||typeof api?.startNativeVoice!=="function")return null;
  let status;
  try{status=api.startNativeVoice(token)}catch(e){return{error:"ERR_NATIVE_BRIDGE"}}
  if(status!=="OK")return{error:/^ERR_[A-Z_]{2,48}$/.test(status)?status:"ERR_NATIVE_START"};
  const native={
    __agyNative:true,state:"recording",
    stop(){
      if(this.state==="inactive")return;
      this.state="inactive";
      if(isCanceled()){
        try{api.cancelNativeVoice(token)}catch(e){}
        onFinished(null);
        return;
      }
      try{
        const raw=api.stopNativeVoice(token);
        const result=JSON.parse(raw);
        if(!result.ok)throw new Error(result.error||"ERR_NATIVE_STOP");
        if(result.mimeType!=="audio/mp4"||!result.data||result.data.length>22*1024*1024)
          throw new Error("ERR_AUDIO_FORMAT");
        onFinished(result);
      }catch(e){
        const code=e?.message||"ERR_NATIVE_STOP";
        onFailure("Failed to finish Android recording ("+
          (/^ERR_[A-Z_]{2,48}$/.test(code)?code:"ERR_NATIVE_STOP")+").");
      }
    }
  };
  return{recorder:native};
}
function agyNativeMicCancel(){
  try{
    const api=window.AndroidBridge||window.Android;
    if(typeof api?.cancelNativeVoice==="function"&&window.__AG_NATIVE_BRIDGE_TOKEN__)
      api.cancelNativeVoice(window.__AG_NATIVE_BRIDGE_TOKEN__);
  }catch(e){}
}
/*
 * Transport adapter only. Uses native 16kHz PCM when Android WebView cannot
 * capture audio. The original Core streamAudioTranscription RPC and Lexical
 * transcript editor remain unchanged.
 */
let agyNativePcmSession=null;
function agyNativePcmAvailable(){
  const api=window.AndroidBridge||window.Android;
  return typeof window.__AG_NATIVE_BRIDGE_TOKEN__==="string"
    && typeof api?.startNativePcm==="function"
    && typeof api?.readNativePcm==="function"
    && typeof api?.stopNativePcm==="function";
}
function agyNativePcmStop(flush=false){
  const current=agyNativePcmSession;
  if(!current)return;
  current.alive=false;
  if(current.timer!==null)clearTimeout(current.timer);
  agyNativePcmSession=null;
  let failure=null;
  try{
    if(flush){
      // Native queue is capped at 20 frames; three reads drain its existing
      // backlog (eight frames per poll), with one bounded extra empty check.
      for(let i=0;i<4;i++){
        const data=agyDecodeNativePcm(current.api.readNativePcm(current.token));
        if(!data.length)break;
        current.onChunk(data);
      }
    }
  }catch(error){failure=error?.message||"ERR_NATIVE_PCM_READ"}
  finally{try{current.api.stopNativePcm(current.token)}catch(error){failure="ERR_NATIVE_PCM_BRIDGE"}}
  if(failure)current.onError(/^ERR_[A-Z_]{2,48}$/.test(failure)?failure:"ERR_NATIVE_PCM_READ");
}
function agyNativePcmStart(onChunk,onError){
  if(!agyNativePcmAvailable())throw Error("ERR_NATIVE_PCM_UNAVAILABLE");
  agyNativePcmStop();
  const api=window.AndroidBridge||window.Android;
  const token=window.__AG_NATIVE_BRIDGE_TOKEN__;
  let status;
  try{status=api.startNativePcm(token)}
  catch(e){throw Error("ERR_NATIVE_PCM_BRIDGE")}
  if(status!=="OK")
    throw Error(/^ERR_[A-Z_]{2,48}$/.test(status)?status:"ERR_NATIVE_PCM_START");
  const session={api,token,onChunk,onError,alive:true,timer:null};
  agyNativePcmSession=session;
  const read=()=>{
    if(!session.alive)return;
    try{
      const data=agyDecodeNativePcm(api.readNativePcm(token));
      if(data.length)onChunk(data);
    }catch(e){
      const code=e?.message;
      agyNativePcmStop();
      onError(/^ERR_[A-Z_]{2,48}$/.test(code)?code:"ERR_NATIVE_PCM_READ");
      return;
    }
    if(session.alive)session.timer=setTimeout(read,45);
  };
  session.timer=setTimeout(read,45);
}

// Wait for real Core completion; network latency is not successful dictation.
function agyEndDictationSession(end,signal){
  let request;
  try{request=end()}catch(error){return Promise.reject(error)}
  return agyDictationWait(request,20000,"ERR_CORE_END_TIMEOUT",signal);
}
// Bounded waits reject on cancellation and clear their timer/listener on every exit.
function agyDictationWait(promise,ms,code,signal){
  return new Promise((resolve,reject)=>{
    let settled=false;
    const finish=(fn,value)=>{
      if(settled)return;settled=true;clearTimeout(timer);
      signal?.removeEventListener("abort",abort);fn(value);
    };
    const abort=()=>finish(reject,new DOMException("Aborted","AbortError"));
    const timer=setTimeout(()=>finish(reject,Error(code)),ms);
    // Observe the underlying operation even if it completes after timeout/abort.
    Promise.resolve(promise).then(value=>finish(resolve,value),error=>finish(reject,error));
    if(signal?.aborted)abort();else signal?.addEventListener("abort",abort,{once:true});
  });
}
// Keep the original Core request shapes and sequence numbers. Only delivery
// completion is tracked; no audio is stored on disk or in diagnostic output.
function agyCreateDictationDelivery(signal,send,onFailure){
  let id=null,buffer=[],bufferedBytes=0,failure=null,resolveReady;
  const pending=new Set();
  const ready=new Promise(resolve=>{resolveReady=resolve});
  const fail=error=>{
    if(failure||signal.aborted)return;
    failure=error;
    onFailure(error);
  };
  const dispatch=(data,seq)=>{
    if(signal.aborted||failure)return;
    if(pending.size>=128){fail(Error("ERR_CORE_CHUNK_BACKLOG"));return;}
    let request;
    try{request=send(id,data,seq)}catch(error){fail(Error("ERR_CORE_CHUNK_SEND"));return;}
    const promise=agyDictationWait(request,20000,"ERR_CORE_CHUNK_TIMEOUT",signal);
    pending.add(promise);
    promise.then(()=>pending.delete(promise),error=>{
      pending.delete(promise);
      if(!signal.aborted)fail(Number.isInteger(error?.code)?error:Error("ERR_CORE_CHUNK_SEND"));
    });
  };
  const cancel=()=>{buffer=[];bufferedBytes=0;pending.clear();signal.removeEventListener("abort",cancel)};
  signal.addEventListener("abort",cancel,{once:true});
  return {
    add(data,seq){
      if(signal.aborted||failure)return;
      if(id){dispatch(data,seq);return;}
      // 210 seconds of mono 16kHz PCM16: never accumulate beyond one dictation.
      if(bufferedBytes+data.length>6720000){fail(Error("ERR_CORE_AUDIO_BUFFER"));return;}
      buffer.push({data,seq});bufferedBytes+=data.length;
    },
    ready(sessionId){
      if(signal.aborted||failure||id)return;
      id=sessionId;
      const queued=buffer;buffer=[];bufferedBytes=0;
      for(const chunk of queued)dispatch(chunk.data,chunk.seq);
      resolveReady(id);
    },
    async drain(){
      const sessionId=await agyDictationWait(ready,20000,"ERR_CORE_READY_TIMEOUT",signal);
      if(failure)throw failure;
      while(pending.size)await Promise.all([...pending]);
      if(failure)throw failure;
      return sessionId;
    },
    cancel
  };
}
function agyDecodeNativePcm(value){
  if(typeof value!=="string")throw Error("ERR_NATIVE_PCM_REPLY");
  if(/^ERR_[A-Z_]{2,48}$/.test(value))throw Error(value);
  if(value.length>32*1024)throw Error("ERR_NATIVE_PCM_FRAME");
  const binary=atob(value),data=new Uint8Array(binary.length);
  for(let i=0;i<binary.length;i++)data[i]=binary.charCodeAt(i);
  if(data.length%2)throw Error("ERR_NATIVE_PCM_FRAME");
  return data;
}

const AGY_DICTATION_LIMIT_MS=210000;
function agyDictationRemaining(deadline,now){
  return Math.max(0,Math.ceil((deadline-now)/1000));
}
function useAgyDictationClock(recording,onLimit){
  const [remaining,setRemaining]=A.useState(210);
  const latest=A.useRef(onLimit);latest.current=onLimit;
  A.useEffect(()=>{
    if(!recording)return;
    const deadline=performance.now()+AGY_DICTATION_LIMIT_MS;
    let alive=true,timer=null;
    const update=()=>{
      if(!alive)return;
      const left=deadline-performance.now();
      setRemaining(agyDictationRemaining(deadline,performance.now()));
      if(left<=0){alive=false;latest.current();return;}
      timer=setTimeout(update,Math.min(1000,left));
    };
    update();
    return()=>{alive=false;if(timer!==null)clearTimeout(timer)};
  },[recording]);
  return recording?remaining:null;
}
function AgyDictationCountdown({seconds}){
  const value=Number.isFinite(seconds)?Math.max(0,seconds):210;
  const label=Math.floor(value/60)+":"+String(value%60).padStart(2,"0");
  return A.createElement("div",{className:"agy-dictation-countdown",role:"timer",
    "aria-label":"Dictation time remaining","aria-live":"off"},label+" remaining");
}

// The current drawer owns its child path; unrelated hidden drawers cannot pop it.
function useAgyMobilePathBack(depth,pop){
  const owner=A.useContext(agySheetBackContext);
  A.useLayoutEffect(()=>{
    if(!owner||depth===0)return;
    const back=()=>{pop();return true;};owner.current=back;
    return()=>{if(owner.current===back)owner.current=null;};
  },[owner,depth,pop]);
}
function agySheetRequestChange(open,details,change,stepBack){
  if(open)change(true);
  else if(details?.reason==="close-press")change(false);
  else stepBack();
}
/* Compact mobile composer; all settings keep their original controllers. */
function useAgyMobileLayout(){
  const query=window.__AG_MOBILE_LAYOUT_QUERY__||"(max-width: 720px), (pointer: coarse)";
  const [mobile,setMobile]=A.useState(()=>window.matchMedia(query).matches);
  A.useEffect(()=>{
    const mq=window.matchMedia(query),update=()=>setMobile(mq.matches);
    mq.addEventListener("change",update);return()=>mq.removeEventListener("change",update);
  },[]);
  return mobile;
}
function agyCompactModelName(label){
  const text=String(label||"Model");
  const gemini=/^Gemini\s+(\S+)\s+(.+)$/i.exec(text);
  if(gemini)return gemini[1]+" "+gemini[2];
  return text.replace(/^Claude\s+(?=Sonnet|Opus|Haiku)/i,"");
}
function AgyModelName({label,effort,tooltipId}){
  const mobile=useAgyMobileLayout();
  return A.createElement("span",{"data-tooltip-id":tooltipId,
    className:mobile?"agy-model-button-label":"min-w-0 overflow-hidden text-ellipsis whitespace-nowrap",
    title:mobile?label+(effort?" · "+effort:""):undefined},
    A.createElement("span",{className:mobile?"agy-model-short-label":undefined},mobile?agyCompactModelName(label):label),
    effort&&A.createElement("span",{className:mobile?"agy-model-effort-label":"opacity-70"},mobile?" · ":" ",effort));
}
function AgyMobilePermissions(props){
  return useAgyMobileLayout()?A.createElement(cWb,props):null;
}
function AgyDesktopPermissions({control}){
  return useAgyMobileLayout()?null:control;
}
function AgyComposerModelControl({primary,permissions,desktop}){
  if(!useAgyMobileLayout())return desktop;
  return A.createElement(A.Fragment,null,
    permissions&&A.createElement("div",{className:"agy-composer-permissions-control"},permissions),
    A.createElement("div",{className:"agy-composer-model-control"},primary));
}
function agyComposerActionLabel(label){
  return ({Media:"Photos & files",Folder:"Folder",Mentions:"Mentions",Actions:"Actions",Browser:"Browser","Screen Recording":"Screen recording"})[label]||label;
}
/* Position the Add card above the real footer, including Android IME changes. */
function agyPositionAddMenu(node,footer,viewport=window.visualViewport){
  if(!node||!footer)return;
  const rect=footer.getBoundingClientRect(),top=viewport?.offsetTop||0;
  node.style.setProperty("--agy-menu-bottom",Math.max(8,window.innerHeight-rect.top+8)+"px");
  node.style.setProperty("--agy-menu-max-height",Math.max(48,rect.top-top-16)+"px");
}
function AgyComposerSheet({items,onClose,extraControls}){
  const dialog=A.useRef(null);
  A.useEffect(()=>{
    const node=dialog.current;node?.showModal();
    const footer=document.querySelector(".agy-composer-footer"),viewport=window.visualViewport;
    let frame=0;
    const position=()=>{cancelAnimationFrame(frame);frame=requestAnimationFrame(()=>agyPositionAddMenu(node,footer,viewport));};
    const observer=footer&&typeof ResizeObserver==="function"?new ResizeObserver(position):null;
    observer?.observe(footer);position();
    window.addEventListener("resize",position);
    viewport?.addEventListener("resize",position);viewport?.addEventListener("scroll",position);
    const unregister=typeof agyRegisterMobileOverlay==="function"?agyRegisterMobileOverlay(onClose):()=>{};
    return()=>{
      unregister();
      cancelAnimationFrame(frame);observer?.disconnect();
      window.removeEventListener("resize",position);
      viewport?.removeEventListener("resize",position);viewport?.removeEventListener("scroll",position);
      node?.close();
    };
  },[]);
  return A.createElement("dialog",{ref:dialog,className:"agy-composer-sheet text-foreground",
      "aria-label":"Add to message",onCancel:e=>{e.preventDefault();e.stopPropagation();onClose();},
      onClick:e=>{e.stopPropagation();if(e.target===dialog.current){const r=dialog.current.getBoundingClientRect();
        if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)onClose();}}},
    A.createElement("header",null,A.createElement("strong",null,"Add to message"),
      A.createElement("button",{type:"button","aria-label":"Close menu",onClick:e=>{e.stopPropagation();onClose();}},"✕")),
    items.map((group,index)=>A.createElement("div",{className:"agy-composer-action-group",key:index},
      group.map(item=>A.createElement("button",{type:"button",key:item.label,disabled:item.disabled,
        className:"agy-composer-action",onClick:e=>{e.stopPropagation();item.onClick();onClose();}},
        A.createElement("span",{className:"agy-composer-action-icon"},A.createElement(item.icon,{width:24,height:24,"aria-hidden":true})),
        A.createElement("span",null,agyComposerActionLabel(item.label)))))),
    extraControls&&A.createElement("section",{className:"agy-composer-extra-controls"},
      A.createElement("div",{className:"agy-composer-settings-label"},"Chat settings"),extraControls));
}
function AgyMobileContextMenu(props){
  const mobile=useAgyMobileLayout(),[open,setOpen]=A.useState(false);
  const change=value=>{setOpen(value);props.onOpenChange?.(value);};
  if(!mobile)return A.createElement(agyOriginalContextMenu,props);
  return A.createElement(A.Fragment,null,
    A.createElement("button",{type:"button",className:"agy-composer-add",
      "aria-label":"Add to message","aria-haspopup":"dialog","aria-expanded":open,
      onClick:()=>change(true)},"+"),
    open&&A.createElement(AgyComposerSheet,{items:props.items,extraControls:props.extraControls,onClose:()=>change(false)}));
}
var zYb=AgyMobileContextMenu;


// Desktop popups retain Core's own Escape behavior; Android dispatches the key.
function agyDesktopOverlayOpen(){
  if(window.__AG_DESKTOP_MODE__!==true||useAgyDesktopNarrow())return false;
  return [...document.querySelectorAll('[role=menu],[role=listbox],[role=dialog],[role=alertdialog]')].some(node=>{
    if(node.closest('[aria-hidden=true],[inert]'))return false;
    const r=node.getBoundingClientRect(),style=getComputedStyle(node);
    return r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight&&style.visibility!=="hidden"&&style.display!=="none"&&(Number(style.opacity)>0||node.hasAttribute("data-open"));
  });
}
function useAgyDesktopNarrow(){return window.matchMedia("(max-width: 720px)").matches;}
window.__AGY_DESKTOP_OVERLAY_OPEN__=agyDesktopOverlayOpen;
