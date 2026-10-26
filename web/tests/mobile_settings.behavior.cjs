const fs=require("node:fs"),vm=require("node:vm"),assert=require("node:assert/strict"),cp=require("node:child_process");
const python="import sys,json;from pathlib import Path;sys.path.insert(0,'web');from patch_mobile_composer import transform;s=transform(Path('web/main.js').read_text());a=s.index('function EZa(');b=s.index(';const GZa=',a);print(json.dumps(s[a:b]))";
const p=cp.spawnSync("python",["-c",python],{encoding:"utf8"});assert.equal(p.status,0,p.stderr);
const helper=fs.readFileSync("web/src/mobile/settings.js","utf8");
let mobile=true,scope=true,select={},changes=[],opened=[];
const ctx={UK:{Provider:"select-provider"},rK:()=>mobile,useAgyMobileLayout:()=>mobile,
 agyOriginalSettingsPage:"settings-page",EK:"drawer",WK:null,XK:null,YK:null,
 BA:()=>({scrollRef:{},overflow:{},onScroll(){}}),au:()=>null,aj:(...x)=>x.filter(Boolean).join(" "),
 yZa:"desktop-select",hZa:"desktop-option",BZa:"desktop-trigger",DZa:"desktop-value",FZa:null,
 W:"icon",FZa:null,lZa:"desktop-list",vZa:"desktop-portal",xZa:"positioner",sZa:"popup",
 A:{createContext:()=>({Provider:"settings-provider"}),useContext:c=>c===ctx.UK?select:scope,
  useState:v=>[v,()=>{}],useId:()=>"settings-select",useMemo:f=>f(),useCallback:f=>f,
  Fragment:"fragment",Children:{forEach:(x,f)=>(Array.isArray(x)?x:[x]).forEach(f),only:x=>x},
  isValidElement:x=>!!x?.props,
  cloneElement:(e,p)=>({...e,props:{...e.props,...p}}),
  createElement:(type,props,...children)=>({type,props:props||{},children})}};
vm.createContext(ctx);vm.runInContext(helper+"\n"+JSON.parse(p.stdout),ctx);
function nodes(tree,type){if(!tree||typeof tree!=="object")return[];
 return(tree.type===type?[tree]:[]).concat((tree.children||[]).flat(Infinity).flatMap(x=>nodes(x,type)));}
const options=[{type:ctx.FZa,props:{value:"Catppuccin",children:"Catppuccin"}},
 {type:ctx.FZa,props:{value:"Dracula",children:"Dracula"}},
 {type:ctx.FZa,props:{value:"locked",children:"Locked",disabled:true}}];
function root(disabled=false){
 const tree=ctx.VK({value:"Catppuccin",nativeOnMobile:true,disabled,children:options,onValueChange:v=>changes.push(v)});
 select=tree.props.value;select.setOpen=v=>opened.push(v);return tree;
}
let tree=root();assert.equal(select.nativeOnMobile,false);assert.equal(nodes(tree,"select").length,0);
let trigger=ctx.WK({children:"Catppuccin"});
assert.equal(trigger.props["aria-haspopup"],"listbox");trigger.props.onClick();assert.deepEqual(opened,[true]);
let option=ctx.FZa({value:"Dracula",children:"Dracula"});
assert.equal(option.props.role,"option");assert.equal(option.props["aria-selected"],false);
option.props.onClick({});assert.deepEqual(changes,["Dracula"]);assert.equal(opened.at(-1),false);
option=ctx.FZa({value:"Catppuccin",children:"Catppuccin"});assert.equal(option.props["aria-selected"],true);
option=ctx.FZa({value:"locked",disabled:true,children:"Locked"});option.props.onClick({});
assert.equal(changes.length,1,"disabled option must not save");
root(true);trigger=ctx.WK({children:"Catppuccin"});assert.equal(trigger.props.disabled,true);
trigger.props.onClick();option=ctx.FZa({value:"Dracula",children:"Dracula"});option.props.onClick({});
assert.equal(changes.length,1,"disabled control cannot save");
root();const sheet=ctx.XK({children:options});assert.equal(sheet.props.className,"agy-settings-select-sheet");
assert.equal(nodes(sheet,"div").filter(n=>n.props.role==="listbox").length,1);
scope=false;tree=root();assert.equal(select.nativeOnMobile,true);assert.equal(nodes(tree,"select").length,1);
mobile=false;tree=ctx.G_({title:"Appearance"});assert.equal(tree.props.value,false);
tree=root();assert.equal(nodes(tree,"desktop-select").length,1);
console.log("PASS actual Core settings selects: scoped themed mobile list, original value callback, selected/disabled options, native and desktop preservation");

let focused=-1;const optionsForKeys=[0,1,2].map(i=>({focus:()=>focused=i}));
const event={key:"End",currentTarget:{querySelectorAll:selector=>{assert.ok(selector.includes(":not(:disabled)"));return optionsForKeys;}},target:{closest:()=>optionsForKeys[0]},preventDefault(){}};
ctx.agySettingsListKeyDown(event);assert.equal(focused,2);
event.key="Home";ctx.agySettingsListKeyDown(event);assert.equal(focused,0);
event.key="ArrowDown";ctx.agySettingsListKeyDown(event);assert.equal(focused,1);
event.key="ArrowUp";ctx.agySettingsListKeyDown(event);assert.equal(focused,2);
console.log("PASS settings list keyboard: Home/End/arrow focus navigation skips disabled controls");
