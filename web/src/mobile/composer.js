function agyAudioError(error){
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
  return typeof error==="string"&&error.startsWith("Нет доступа к микрофону.")?error:
    "Не удалось начать запись. Проверьте доступ к микрофону и доступность голосового ввода.";
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
function AgyComposerModelControl({primary,desktop}){
  return useAgyMobileLayout()?A.createElement("div",{className:"agy-composer-model-control"},primary):desktop;
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
