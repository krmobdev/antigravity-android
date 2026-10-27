window.__AGY_AUDIO_DIAG_BUILD__="2026-10-26-native-primary-v6";
function agyAudioError(error){
  // Native recorder already stores a localized message. Reformatting it
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
function agyNativePcmStop(){
  const current=agyNativePcmSession;
  if(!current)return;
  current.alive=false;
  if(current.timer!==null)clearTimeout(current.timer);
  agyNativePcmSession=null;
  try{current.api.stopNativePcm(current.token)}catch(e){}
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
  const session={api,token,alive:true,timer:null};
  agyNativePcmSession=session;
  const read=()=>{
    if(!session.alive)return;
    try{
      const value=api.readNativePcm(token);
      if(typeof value!=="string")throw Error("ERR_NATIVE_PCM_REPLY");
      if(/^ERR_[A-Z_]{2,48}$/.test(value))throw Error(value);
      if(value){
        if(value.length>32*1024)throw Error("ERR_NATIVE_PCM_FRAME");
        const binary=atob(value),data=new Uint8Array(binary.length);
        for(let i=0;i<binary.length;i++)data[i]=binary.charCodeAt(i);
        if(data.length)onChunk(data);
      }
    }catch(e){
      const code=e?.message;
      agyNativePcmStop();
      onError(/^ERR_[A-Z_]{2,48}$/.test(code)?code:"ERR_NATIVE_PCM_READ");
      return;
    }
    session.timer=setTimeout(read,45);
  };
  session.timer=setTimeout(read,45);
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
