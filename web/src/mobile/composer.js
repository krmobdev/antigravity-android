window.__AGY_AUDIO_DIAG_BUILD__="2026-10-26-native-primary-v6";
function agyAudioError(error){
  // Native recorder already stores a localized message. Reformatting it
  // used to erase the real reason and show the same generic error every time.
  const known=[
    "Нет доступа к микрофону. Разрешите запись звука в настройках приложения.",
    "Микрофон не найден. Проверьте подключение устройства.",
    "Микрофон занят или недоступен. Закройте другое приложение с записью.",
    "Запись голоса не поддерживается текущим Android WebView. Проверьте обновление системного WebView.",
    "WebView заблокировал доступ к микрофону. Проверьте настройки безопасности.",
    "Запись была прервана. Попробуйте ещё раз.",
    "Запись пуста. Проверьте микрофон и попробуйте снова.",
    "Ошибка записи. Проверьте микрофон и попробуйте снова."
  ];
  if(typeof error==="string" && known.includes(error)) return error;
  if(typeof error==="string" && error.startsWith("Не удалось начать запись на этапе ")) return error;
  // Android's native microphone returns bounded error codes. Retain them
  // across the outer UI formatter instead of disguising them as UnknownError.
  if(typeof error==="string" &&
    /^Не удалось (начать|завершить) запись через Android \(ERR_[A-Z_]{2,48}\)\.$/.test(error))
    return error;
  const name=error?.name||"";
  if(name==="NotAllowedError"||name==="PermissionDeniedError")
    return "Нет доступа к микрофону. Разрешите запись звука в настройках приложения.";
  if(name==="NotFoundError"||name==="DevicesNotFoundError")
    return "Микрофон не найден. Проверьте подключение устройства.";
  if(name==="NotReadableError"||name==="TrackStartError")
    return "Микрофон занят или недоступен. Закройте другое приложение с записью.";
  if(name==="NotSupportedError"||name==="TypeError")
    return "Запись голоса не поддерживается текущим Android WebView. Проверьте обновление системного WebView.";
  if(name==="SecurityError")
    return "WebView заблокировал доступ к микрофону. Проверьте настройки безопасности.";
  if(name==="AbortError")
    return "Запись была прервана. Попробуйте ещё раз.";
  // Return a safe exception class, never an arbitrary browser error message
  // that might include private paths or account data.
  const safeClass=typeof name==="string"&&/^[A-Za-z]{2,36}Error$/.test(name)?name:"UnknownError";
  return "Не удалось начать запись ("+safeClass+"). Проверьте поддержку микрофона в Android WebView.";
}
function agyAudioStageError(stage,error){
  const names={
    getUserMedia:"получения звука от Android",
    MediaRecorder:"создания аудиорекордера",
    start:"старта записи",
    recording:"записи аудио"
  };
  const place=names[stage]||"подготовки записи";
  // Report only safe type and phase, never a raw browser error with private data.
  const raw=error?.name||error?.constructor?.name||typeof error;
  const kind=typeof raw==="string"&&/^[A-Za-z][A-Za-z0-9]{1,35}$/.test(raw)?raw:"UnknownError";
  return "Не удалось начать запись на этапе "+place+" ("+kind+"). "+agyAudioError(error);
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
        onFailure("Не удалось завершить запись через Android ("+
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
  const query="(max-width: 720px), (pointer: coarse)";
  const [mobile,setMobile]=A.useState(()=>window.matchMedia(query).matches);
  A.useEffect(()=>{
    const mq=window.matchMedia(query),update=()=>setMobile(mq.matches);
    mq.addEventListener("change",update);return()=>mq.removeEventListener("change",update);
  },[]);
  return mobile;
}
function agyCompactModelName(label){
  const text=String(label||"Модель");
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
  return ({Media:"Фото и файлы",Folder:"Папка",Mentions:"Упоминания",Actions:"Действия",Browser:"Браузер","Screen Recording":"Запись экрана"})[label]||label;
}
function AgyComposerSheet({items,onClose,extraControls}){
  const dialog=A.useRef(null);
  A.useEffect(()=>{
    const node=dialog.current;node?.showModal();
    const unregister=typeof agyRegisterMobileOverlay==="function"?agyRegisterMobileOverlay(onClose):()=>{};
    const marker="agy-composer-"+Math.random().toString(36).slice(2);
    history.pushState({...history.state,agyMobileOverlay:marker},"",location.href);
    const back=()=>onClose();window.addEventListener("popstate",back);
    return()=>{
      unregister();
      window.removeEventListener("popstate",back);
      if(history.state?.agyMobileOverlay===marker)history.back();
      node?.close();
    };
  },[]);
  return A.createElement("dialog",{ref:dialog,className:"agy-composer-sheet text-foreground",
      "aria-label":"Добавить к сообщению",onCancel:e=>{e.preventDefault();onClose();},
      onClick:e=>{if(e.target===dialog.current){const r=dialog.current.getBoundingClientRect();
        if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)onClose();}}},
    A.createElement("header",null,A.createElement("strong",null,"Добавить к сообщению"),
      A.createElement("button",{type:"button","aria-label":"Закрыть меню",onClick:onClose},"✕")),
    items.map((group,index)=>A.createElement("div",{className:"agy-composer-action-group",key:index},
      group.map(item=>A.createElement("button",{type:"button",key:item.label,disabled:item.disabled,
        className:"agy-composer-action",onClick:()=>{onClose();item.onClick();}},
        A.createElement("span",{className:"agy-composer-action-icon"},A.createElement(item.icon,{width:24,height:24,"aria-hidden":true})),
        A.createElement("span",null,agyComposerActionLabel(item.label)))))),
    extraControls&&A.createElement("section",{className:"agy-composer-extra-controls"},
      A.createElement("div",{className:"agy-composer-settings-label"},"Настройки чата"),extraControls));
}
function AgyMobileContextMenu(props){
  const mobile=useAgyMobileLayout(),[open,setOpen]=A.useState(false);
  const change=value=>{setOpen(value);props.onOpenChange?.(value);};
  if(!mobile)return A.createElement(agyOriginalContextMenu,props);
  return A.createElement(A.Fragment,null,
    A.createElement("button",{type:"button",className:"agy-composer-add",
      "aria-label":"Добавить к сообщению","aria-haspopup":"dialog","aria-expanded":open,
      onClick:()=>change(true)},"+"),
    open&&A.createElement(AgyComposerSheet,{items:props.items,extraControls:props.extraControls,onClose:()=>change(false)}));
}
var zYb=AgyMobileContextMenu;
