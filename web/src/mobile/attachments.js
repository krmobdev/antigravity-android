/* Injected into the pinned client scope; uses canonical selectedMedia state. */
function agyAttachmentKind(media) {
  const mime=(media.mimeType||"").toLowerCase(), name=(media.description||"").toLowerCase();
  if (/^image\/(png|jpeg|gif|webp)$/.test(mime)) return "image";
  if (mime.startsWith("audio/")) return "audio";
  if (mime.startsWith("video/")) return "video";
  if (/\.(txt|md|json|csv|py|js|mjs|ts|tsx|jsx|java|kt|kts|c|h|cpp|hpp|rs|go|sh|xml|yaml|yml|css|html|htm|sql|toml|ini|conf|log|rb|php|swift)$/.test(name) ||
      /^(text\/|application\/(json|xml))/.test(mime)) return "text";
  return "document";
}
function agyAttachmentBytes(media) {
  const data=media.payload?.case==="inlineData"?media.payload.value:null;
  return data instanceof Uint8Array?data:null;
}
function agyAttachmentSize(media) {
  return AB.get(media)?.size ?? agyAttachmentBytes(media)?.byteLength ?? null;
}
function agyAttachmentSizeLabel(size) {
  if(size==null)return "Размер неизвестен";
  return size<1024?size+" Б":size<1048576?(size/1024).toFixed(1)+" КБ":(size/1048576).toFixed(1)+" МБ";
}
function AgyAttachmentPreview({media,onClose,openExternal}) {
  const dialog=A.useRef(null), [view,setView]=A.useState({loading:true}),kind=agyAttachmentKind(media);
  A.useEffect(()=>{
    let alive=true,objectUrl=null;
    const file=AB.get(media),bytes=agyAttachmentBytes(media),size=file?.size??bytes?.byteLength??0;
    (async()=>{
      try{
        if(kind==="text"&&size>1048576)throw Error("Просмотр текста ограничен 1 МБ.");
        const data=file?new Uint8Array(await file.arrayBuffer()):bytes;
        if(!alive)return;
        if(!data||(kind!=="text"&&!data.byteLength))throw Error("Локальная копия недоступна. Добавьте файл повторно для просмотра.");
        if(kind==="text"){
          if(data.length>1048576||data.includes(0))throw Error("Файл слишком велик или не является текстом.");
          setView({text:new TextDecoder("utf-8",{fatal:true}).decode(data)});
        }else if(["image","audio","video"].includes(kind)){
          objectUrl=URL.createObjectURL(new Blob([data],{type:media.mimeType}));
          setView({url:objectUrl});
        }else setView({error:"Этот формат не поддерживает встроенный просмотр. Исходный файл остаётся вложенным."});
      }catch(error){if(alive)setView({error:error.message||"Не удалось прочитать вложение."});}
    })();
    return()=>{alive=false;if(objectUrl)URL.revokeObjectURL(objectUrl);};
  },[media,kind]);
  A.useEffect(()=>{
    const node=dialog.current;node?.showModal();
    const marker="agy-attachment-"+Math.random().toString(36).slice(2);
    history.pushState({...history.state,agyMobileOverlay:marker},"",location.href);
    const back=()=>onClose();window.addEventListener("popstate",back);
    return()=>{
      window.removeEventListener("popstate",back);
      if(history.state?.agyMobileOverlay===marker)history.back();
      node?.close();
    };
  },[]);
  const name=media.description||"Вложение";
  return A.createElement("dialog",{ref:dialog,className:"agy-attachment-dialog bg-card text-foreground","aria-label":name,
      onCancel:e=>{e.preventDefault();onClose();}},
    A.createElement("header",{className:"agy-attachment-preview-header"},
      A.createElement("span",null,name),
      A.createElement("button",{type:"button",onClick:onClose,"aria-label":"Закрыть просмотр"},"✕")),
    view.loading?A.createElement("p",{role:"status"},"Чтение вложения…"):
    view.text!==undefined?A.createElement("pre",{"data-testid":"attachment-safe-text"},view.text):
    view.url?A.createElement(kind==="image"?"img":kind,{src:view.url,alt:name,controls:kind!=="image",className:"agy-attachment-full-media"}):
    A.createElement("div",null,A.createElement("p",{role:"status"},view.error),
      openExternal&&A.createElement("button",{type:"button",onClick:openExternal},"Открыть файл")));
}
function AgyAttachmentCard({media,onOpen,onRemove}){
  const [thumbnail,setThumbnail]=A.useState(null),kind=agyAttachmentKind(media),name=media.description||"Вложение";
  A.useEffect(()=>{
    if(kind!=="image")return;
    const data=media.thumbnail?.length?media.thumbnail:agyAttachmentBytes(media);
    if(!data?.length)return;
    const url=URL.createObjectURL(new Blob([data],{type:media.mimeType}));setThumbnail(url);
    return()=>URL.revokeObjectURL(url);
  },[media,kind]);
  return A.createElement("div",{className:"agy-attachment-card bg-muted border-border text-foreground","data-testid":"input-attachment"},
    A.createElement("button",{type:"button",className:"agy-attachment-open",onClick:onOpen,
        "aria-label":"Открыть "+name,"data-testid":"input-attachment-open"},
      thumbnail?A.createElement("img",{src:thumbnail,alt:"",className:"agy-attachment-thumb"}):
        A.createElement("span",{"aria-hidden":true,className:"agy-attachment-icon"},kind==="audio"?"♫":kind==="video"?"▶":"▤"),
      A.createElement("span",{className:"agy-attachment-details"},
        A.createElement("span",{className:"agy-attachment-name"},name),
        A.createElement("span",{className:"agy-attachment-meta"},agyAttachmentSizeLabel(agyAttachmentSize(media))),
        A.createElement("span",{className:"agy-attachment-meta"},media.mimeType||"Файл"))),
    A.createElement("button",{type:"button",className:"agy-attachment-remove","aria-label":"Удалить "+name,
      "data-testid":"input-attachment-remove",onClick:e=>{e.stopPropagation();onRemove();}},"✕"));
}
var uYb=function AgyAttachments({selectedMedia,setSelectedMedia,limit=5}){
  const [selected,setSelected]=A.useState(null), features=Lj(), handler=A.useContext(Iy)?.stepHandler;
  const canOpenExternal=selected?.uri&&handler?.openFile&&!tB(selected.mimeType)&&
    ((selected.mimeType==="application/pdf"&&features.get("pdfViewer"))||qB(selected.mimeType));
  A.useEffect(()=>{if(selected&&!selectedMedia.includes(selected))setSelected(null);},[selectedMedia,selected]);
  if(!selectedMedia.length)return null;
  return A.createElement("section",{className:"agy-attachments","aria-label":"Вложения"},
    A.createElement("div",{className:"agy-attachment-count",role:"status"},"Вложения: "+selectedMedia.length+" / "+limit),
    A.createElement("div",{className:"agy-attachment-list"},selectedMedia.map((media,index)=>A.createElement(AgyAttachmentCard,{
      key:index,media,onOpen:()=>setSelected(media),onRemove:()=>setSelectedMedia(current=>current.filter((item,i)=>i!==index))}))),
    selected&&A.createElement(AgyAttachmentPreview,{key:selectedMedia.indexOf(selected),media:selected,onClose:()=>setSelected(null),openExternal:canOpenExternal?()=>handler.openFile(selected.uri):null}));
};
