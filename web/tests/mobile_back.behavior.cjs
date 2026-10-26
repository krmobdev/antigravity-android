const fs=require("node:fs"),vm=require("node:vm"),assert=require("node:assert/strict"),cp=require("node:child_process");
const python="import sys,json;from pathlib import Path;sys.path.insert(0,'web');from patch_mobile_composer import transform;s=transform(Path('web/main.js').read_text());a=s.index('function EK(');b=s.index(';\"use client\";var CYa',a);print(json.dumps(s[a:b]))";
const result=cp.spawnSync("python",["-c",python],{encoding:"utf8"});
assert.equal(result.status,0,result.stderr);
const actual=JSON.parse(result.stdout);
const attachments=fs.readFileSync("web/src/mobile/attachments.js","utf8");
const composer=fs.readFileSync("web/src/mobile/composer.js","utf8");
const prefix=attachments.slice(attachments.indexOf("const agyMobileOverlayStack"),attachments.indexOf("function agyAttachmentKind"));
const helpers=composer.slice(composer.indexOf("function useAgyMobilePathBack"),composer.indexOf("/* Compact mobile composer"));
let refs=[],effects=[],cursor=0,currentContext=0,stockBack;
const box={window:{},EC:()=>false,DC:(open,back)=>{stockBack=back},
  BYa:{},agySheetBackContext:{},kYa:"root",KC:"portal",ZXa:"backdrop",zYa:"position",jYa:"sheet",
  A:{useRef(v){const i=cursor++;return refs[i]||(refs[i]={current:v})},
    useContext:()=>currentContext,
    useEffect(fn,deps){const i=cursor++,old=effects[i];
      if(!old||deps.some((v,j)=>v!==old.deps[j])){old?.cleanup?.();effects[i]={deps,cleanup:fn()};}},
    createElement:(type,props,...children)=>({type,props:props||{},children})}};
vm.createContext(box);vm.runInContext(prefix+"\n"+helpers+"\n"+actual,box);
function render(props){cursor=0;currentContext=0;return box.EK(props)}
function stack(){return vm.runInContext("agyMobileOverlayStack.length",box)}
for(let cycle=0;cycle<4;cycle++){
  let open=true,depth=2,rootChanges=0;
  const change=v=>{open=v;rootChanges++};
  const back=()=>{if(depth)depth--;else change(false)};
  let root=render({open,onOpenChange:change,onBack:back});
  assert.equal(stack(),1);
  assert.equal(box.window.__AGY_CLOSE_MOBILE_OVERLAY__(),true);
  assert.equal(depth,1);assert.equal(open,true);assert.equal(rootChanges,0);
  root=render({open,onOpenChange:change,onBack:()=>back()});
  assert.equal(stack(),1,"rerender must not duplicate handler");
  root.props.onOpenChange(false,{reason:"outside-press"});
  assert.equal(depth,0);assert.equal(open,true);
  stockBack();assert.equal(open,false);
  render({open,onOpenChange:change,onBack:back});assert.equal(stack(),0);
  assert.equal(box.window.__AGY_CLOSE_MOBILE_OVERLAY__,undefined);
}
// A Settings child path is owned by this drawer, before the root close callback.
let open=true,pops=0,rootClosed=0;
const root=render({open,onOpenChange:()=>rootClosed++});
const owner=refs[0];owner.current=()=>{pops++;return true};
root.props.onOpenChange(false,{reason:"outside-press"});
assert.equal(pops,1);assert.equal(rootClosed,0);
box.window.__AGY_CLOSE_MOBILE_OVERLAY__();assert.equal(pops,2);
owner.current=null;root.props.onOpenChange(false,{reason:"escape-key"});
assert.equal(rootClosed,1);
render({open:false,onOpenChange:()=>{}});
// Dispose an underlying overlay before the top: it must never reappear.
let lower=0,upper=0;
const unregisterLower=box.agyRegisterMobileOverlay(()=>lower++);
const unregisterUpper=box.agyRegisterMobileOverlay(()=>upper++);
unregisterLower();box.window.__AGY_CLOSE_MOBILE_OVERLAY__();
assert.equal(lower,0);assert.equal(upper,1);
unregisterUpper();assert.equal(stack(),0);
console.log("PASS actual patched drawer: one-level native Back/backdrop/Escape, Settings child path, four reopen cycles, no stale or duplicate handlers");
