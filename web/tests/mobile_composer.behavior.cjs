const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const source=fs.readFileSync(path.join(__dirname,"../src/mobile/composer.js"),"utf8");
function setup(mobile){
  let slots=[],cursor=0,effects=[],notifications=[],backCount=0,pop;
  const listeners=new Map(),frames=new Map();let nextFrame=0,disconnects=0;
  const history={state:{route:1},pushState(s){this.state=s;},back(){backCount++;this.state={route:1};}};
  const ctx={Math,history,location:{href:"https://local/c/test"},agyOriginalContextMenu(){},cWb(){},
    document:{querySelector:()=>({getBoundingClientRect:()=>({top:600})})},
    requestAnimationFrame:f=>{frames.set(++nextFrame,f);return nextFrame;},cancelAnimationFrame:id=>frames.delete(id),
    ResizeObserver:class{observe(){}disconnect(){disconnects++;}},
    window:{innerHeight:800,matchMedia:()=>({matches:mobile,addEventListener(){},removeEventListener(){}}),
      addEventListener:(name,f)=>{listeners.set(name,f);if(name==="popstate")pop=f;},removeEventListener:name=>{listeners.delete(name);if(name==="popstate")pop=null;}},
    A:{Fragment:"fragment",
      useState(v){const i=cursor++;if(!(i in slots))slots[i]=typeof v==="function"?v():v;return[slots[i],v=>slots[i]=v];},
      useRef(v){const i=cursor++;return slots[i]??(slots[i]={current:v});},
      useEffect(f,deps){const i=cursor++;if(!effects[i])effects[i]={deps,cleanup:f()};},
      createElement(type,props,...children){if(props?.ref)props.ref.current={showModal(){},close(){}};return{type,props:props||{},children};}
    }};
  vm.createContext(ctx);vm.runInContext(source,ctx);
  return{ctx,history,get pop(){return pop;},get backCount(){return backCount;},get listeners(){return listeners.size;},get frames(){return frames.size;},get disconnects(){return disconnects;},
    render(name,props){cursor=0;return ctx[name](props);},
    cleanup(){effects.forEach(e=>e?.cleanup?.());effects=[];}};
}
function nodes(tree,type){if(!tree||typeof tree!=="object")return[];return(tree.type===type?[tree]:[]).concat((tree.children||[]).flat(Infinity).flatMap(n=>nodes(n,type)));}
let calls=0,changes=[],h=setup(true);
const icon=()=>null,items=[[{label:"Media",icon,onClick:()=>calls++},{label:"Folder",icon,disabled:true,onClick:()=>calls++}]];
let tree=h.render("zYb",{items,onOpenChange:v=>changes.push(v)});
const plus=nodes(tree,"button")[0];assert.equal(plus.props["aria-haspopup"],"dialog");
plus.props.onClick();tree=h.render("zYb",{items,onOpenChange:v=>changes.push(v)});
const sheet=nodes(tree,h.ctx.AgyComposerSheet)[0];assert.ok(sheet);assert.equal(calls,0);assert.equal(changes[0],true);h.cleanup();
h=setup(true);let closed=0;tree=h.render("AgyComposerSheet",{items,onClose:()=>closed++});
const buttons=nodes(tree,"button");
assert.equal(buttons.length,3);assert.equal(buttons[1].children[1].children[0],"Фото и файлы");
assert.equal(buttons[2].children[1].children[0],"Папка");assert.equal(buttons[2].props.disabled,true);buttons[1].props.onClick();
assert.equal(closed,1);assert.equal(calls,1);
tree.props.onCancel({preventDefault(){}});assert.equal(closed,2);
h.pop();assert.equal(closed,3);h.cleanup();assert.equal(h.backCount,1);assert.equal(h.listeners,0);assert.equal(h.frames,0);assert.equal(h.disconnects,1);
h=setup(false);tree=h.render("zYb",{items});assert.equal(tree.type,h.ctx.agyOriginalContextMenu);h.cleanup();
h=setup(true);const primary={type:"model",props:{},children:[]},desktop={type:"desktop-settings",props:{},children:[]};
const permissions={type:"permissions",props:{},children:[]};
tree=h.render("AgyComposerModelControl",{primary,permissions,desktop});
assert.equal(nodes(tree,"div").find(n=>n.props.className==="agy-composer-model-control").children[0],primary);
assert.equal(nodes(tree,"div").find(n=>n.props.className==="agy-composer-permissions-control").children[0],permissions);h.cleanup();
h=setup(false);tree=h.render("AgyComposerModelControl",{primary,desktop});assert.equal(tree,desktop);h.cleanup();
console.log("PASS mobile composer: adaptive menu, supported actions, disabled state, close/Back cleanup, desktop fallback");

h=setup(true);assert.equal(h.ctx.agyCompactModelName("Gemini 3.8 Flash"),"3.8 Flash");
assert.equal(h.ctx.agyCompactModelName("Claude Sonnet 4.6"),"Sonnet 4.6");
assert.equal(h.ctx.agyCompactModelName("My private model"),"My private model");
tree=h.render("AgyModelName",{label:"Gemini 3.8 Flash",effort:"High"});
assert.equal(tree.props.title,"Gemini 3.8 Flash · High");
assert.equal(nodes(tree,"span").find(n=>n.props.className==="agy-model-short-label").children[0],"3.8 Flash");h.cleanup();
h=setup(true);tree=h.render("AgyMobilePermissions",{conversationId:"fixture",dropdownPlacement:"top-start"});
assert.equal(tree.type,h.ctx.cWb);assert.equal(tree.props.conversationId,"fixture");h.cleanup();
h=setup(false);tree=h.render("AgyMobilePermissions",{conversationId:"fixture"});assert.equal(tree,null);h.cleanup();
h=setup(false);tree=h.render("AgyDesktopPermissions",{control:permissions});assert.equal(tree,permissions);h.cleanup();
h=setup(true);tree=h.render("AgyDesktopPermissions",{control:permissions});assert.equal(tree,null);h.cleanup();
console.log("PASS compact mobile toolbar: short/full model labels, distinct permissions, unchanged desktop scope, no duplicate selector");

// Menu stays above the real footer for tall screens and IME viewport changes.
h=setup(true);const style=new Map(),node={style:{setProperty:(k,v)=>style.set(k,v)}};
h.ctx.agyPositionAddMenu(node,{getBoundingClientRect:()=>({top:600})},{offsetTop:0});
assert.equal(style.get('--agy-menu-bottom'),'208px');assert.equal(style.get('--agy-menu-max-height'),'584px');
h.ctx.agyPositionAddMenu(node,{getBoundingClientRect:()=>({top:300})},{offsetTop:50});
assert.equal(style.get('--agy-menu-bottom'),'508px');assert.equal(style.get('--agy-menu-max-height'),'234px');
h.ctx.agyPositionAddMenu(node,null);assert.equal(style.get('--agy-menu-bottom'),'508px');
console.log('PASS Add card positioning with IME viewport offsets and complete observer/frame cleanup');
