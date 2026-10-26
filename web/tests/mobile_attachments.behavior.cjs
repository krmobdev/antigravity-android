#!/usr/bin/env node
"use strict";
const assert=require("node:assert/strict"), fs=require("node:fs"), vm=require("node:vm"), cp=require("node:child_process"), path=require("node:path");
const root=path.resolve(__dirname,"../.."), source=fs.readFileSync(path.join(root,"web/src/mobile/attachments.js"),"utf8");
let checks=0;
function check(value,reason){assert.ok(value,reason);checks++;}
function harness(){
  let slots=[],cursor=0,effects=[],deferred=[],created=new Set(),listeners=new Map(),closeCount=0,historyBack=0;
  const A={
    useRef(value){const i=cursor++;return slots[i]??(slots[i]={current:value});},
    useState(value){const i=cursor++;if(!(i in slots))slots[i]=value;return[slots[i],v=>slots[i]=typeof v==="function"?v(slots[i]):v];},
    useEffect(fn,deps){const i=cursor++,old=effects[i];if(!old||deps.some((d,j)=>d!==old.deps[j]))deferred.push(()=>{old?.cleanup?.();effects[i]={deps,cleanup:fn()};});},
    useContext(){return{stepHandler:{openFile(){}}};},
    createElement(type,props,...children){props=props||{};if(props.ref)props.ref.current={showModal(){},close(){closeCount++;}};return{type,props,children};}
  };
  const history={state:{original:true},pushState(s){this.state=s;},back(){historyBack++;this.state={original:true};}};
  const ctx={A,AB:new WeakMap(),Uint8Array,TextDecoder,Blob,Math,history,location:{href:"https://local/c/test"},
    window:{addEventListener:(n,f)=>listeners.set(n,f),removeEventListener:n=>listeners.delete(n)},
    URL:{createObjectURL(){const u="blob:test-"+created.size;created.add(u);return u;},revokeObjectURL(u){created.delete(u);}},
    Lj:()=>({get:()=>false}),Iy:{},tB:()=>false,qB:()=>false};
  vm.createContext(ctx);vm.runInContext(source,ctx);
  return{ctx,created,listeners,history,
    render(name,props){cursor=0;const tree=ctx[name](props);for(const effect of deferred.splice(0))effect();return tree;},
    cleanup(){for(const e of effects)e?.cleanup?.();effects=[];},
    get historyBack(){return historyBack;}
  };
}
function nodes(tree,predicate){
  if(!tree||typeof tree!=="object")return[];
  return(predicate(tree)?[tree]:[]).concat((tree.children||[]).flat(Infinity).flatMap(n=>nodes(n,predicate)));
}
function media(name,mime,text){return{description:name,mimeType:mime,payload:{case:"inlineData",value:new TextEncoder().encode(text)}};}
(async()=>{
  let h=harness();
  check(h.ctx.agyAttachmentKind(media("a.html","text/html",""))==="text","HTML shown only as text");
  check(h.ctx.agyAttachmentKind(media("photo.svg","image/svg+xml",""))==="document","SVG not executed as image");
  let selected=[media("a.txt","text/plain","hello"),media("b.json","application/json","{}")],removed=0;
  const props=()=>({selectedMedia:selected,setSelectedMedia:v=>{selected=typeof v==="function"?v(selected):v;removed++;},limit:5});
  let tree=h.render("uYb",props());
  let cards=nodes(tree,n=>n.type===h.ctx.AgyAttachmentCard);
  check(cards.length===2,"multiple equal card components");
  cards[0].props.onOpen();
  check(removed===0&&selected.length===2,"opening never deletes");
  tree=h.render("uYb",props());
  check(nodes(tree,n=>n.type===h.ctx.AgyAttachmentPreview).length===1,"card opens preview");
  nodes(tree,n=>n.type===h.ctx.AgyAttachmentCard)[1].props.onRemove();
  check(selected.length===1&&selected[0].description==="a.txt","remove only selected card");
  h.cleanup();

  h=harness();const attack='<img src=x onerror="steal()">\n<script>private()</script>';
  const text=media("unsafe.md","text/markdown",attack);
  h.render("AgyAttachmentPreview",{media:text,onClose(){}});
  await new Promise(setImmediate);
  tree=h.render("AgyAttachmentPreview",{media:text,onClose(){}});
  const pre=nodes(tree,n=>n.type==="pre")[0];
  check(pre?.children[0]===attack&&!pre.props.dangerouslySetInnerHTML,"untrusted text stays a text node");
  h.cleanup();check(h.historyBack===1,"overlay history restored on close");

  h=harness();const large=media("big.txt","text/plain","");large.payload.value=new Uint8Array(1048577);
  h.render("AgyAttachmentPreview",{media:large,onClose(){}});
  await new Promise(setImmediate);
  tree=h.render("AgyAttachmentPreview",{media:large,onClose(){}});
  check(nodes(tree,n=>n.type==="pre").length===0,"oversize text rejected");
  h.cleanup();

  h=harness();const binary=media("b.txt","text/plain","a\0b");
  h.render("AgyAttachmentPreview",{media:binary,onClose(){}});
  await new Promise(setImmediate);tree=h.render("AgyAttachmentPreview",{media:binary,onClose(){}});
  check(!nodes(tree,n=>n.type==="pre").length,"binary text rejected");h.cleanup();

  h=harness();const image=media("a.png","image/png","test-image");
  h.render("AgyAttachmentCard",{media:image,onOpen(){},onRemove(){}});
  check(h.created.size===1,"thumbnail object URL allocated");h.cleanup();check(!h.created.size,"thumbnail URL revoked");
  h=harness();h.render("AgyAttachmentPreview",{media:image,onClose(){}});
  await new Promise(setImmediate);check(h.created.size===1,"image preview allocated");
  h.cleanup();check(!h.created.size,"full-screen URL revoked");

  h=harness();let resolve;
  h.ctx.AB.set(image,{size:4,arrayBuffer:()=>new Promise(r=>resolve=r)});
  h.render("AgyAttachmentPreview",{media:image,onClose(){}});
  h.cleanup();resolve(new ArrayBuffer(4));await new Promise(setImmediate);
  check(!h.created.size,"late read after unmount cannot leak an object URL");

  // Execute the actual patched upload hook, retaining its existing validation.
  const python='from pathlib import Path; import sys; sys.path.insert(0,"web"); import patch_mobile_attachments as p; s=p.transform(Path("web/main.js").read_text()); print(s[s.index("var u5a="):s.index(";var v5a=")]+";")';
  const hook=cp.execFileSync("python",["-c",python],{cwd:root,encoding:"utf8"});
  let pending=[],added=[],errors=[];
  const context={A:{useRef:()=>({current:0}),useCallback:f=>f,useMemo:f=>f()},
    d:x=>x,$t:()=>()=>{},pNa:[],qNa:["application/pdf"],SN:file=>new Promise((resolve,reject)=>pending.push({file,resolve,reject})),Promise,Array};
  vm.createContext(context);vm.runInContext(hook,context);
  const input=context.u5a(items=>added.push(...items),0,false,error=>errors.push(error),5);
  const batch=Array.from({length:4},(_,i)=>({name:i+".pdf",type:"application/pdf",size:100}));
  const first=input.handleMediaFiles(batch),second=input.handleMediaFiles(batch);
  check(pending.length===5,"concurrent selections reserve max five slots");
  pending.forEach(p=>p.resolve({description:p.file.name}));await Promise.all([first,second]);
  check(added.length===5&&errors.includes("Maximum number of attachments reached"),"real limit visible without fake additions");

  const before=added.length;
  await input.handleMediaFiles([]);
  check(added.length===before,"cancelled/empty selection leaves existing files unchanged");
  const failed=input.handleMediaFiles([batch[0]]);
  pending[pending.length-1].reject(Error("read failed"));await failed;
  check(added.length===before&&errors.includes("Failed to read attachment"),"read failure is visible and never adds a fake attachment");
  const pickPython='from pathlib import Path; import sys; sys.path.insert(0,"web"); import patch_mobile_attachments as p; s=p.transform(Path("web/main.js").read_text()); print(s[s.index("var o5a="):s.index(";function p5a")]+ ";")';
  vm.runInContext(cp.execFileSync("python",["-c",pickPython],{cwd:root,encoding:"utf8"}),context);
  let selections=0;
  const picker=context.o5a({onFilesSelected:()=>selections++});
  const target={files:[batch[0]],value:"file.pdf"};
  picker.handleFileChange({target});target.value="file.pdf";picker.handleFileChange({target});
  check(selections===2&&target.value==="","same file may be selected again after removal/error");
  console.log("PASS mobile attachments:",checks,"behaviour assertions");
})().catch(error=>{console.error(error);process.exitCode=1;});
