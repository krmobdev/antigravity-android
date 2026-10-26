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
  return A.createElement("dialog",{ref:dialog,className:"agy-composer-sheet bg-card text-foreground border-border",
      "aria-label":"Добавить к сообщению",onCancel:e=>{e.preventDefault();onClose();},
      onClick:e=>{if(e.target===dialog.current){const r=dialog.current.getBoundingClientRect();
        if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)onClose();}}},
    A.createElement("header",null,A.createElement("strong",null,"Добавить к сообщению"),
      A.createElement("button",{type:"button","aria-label":"Закрыть меню",onClick:onClose},"✕")),
    items.map((group,index)=>A.createElement("div",{className:"agy-composer-action-group",key:index},
      group.map(item=>A.createElement("button",{type:"button",key:item.label,disabled:item.disabled,
        className:"agy-composer-action",onClick:()=>{onClose();item.onClick();}},
        A.createElement(item.icon,{width:24,height:24,"aria-hidden":true}),
        A.createElement("span",null,item.label==="Media"?"Фото и файлы":item.label))))),
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
