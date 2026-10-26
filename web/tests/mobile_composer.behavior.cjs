const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const source=fs.readFileSync(path.join(__dirname,"../src/mobile/composer.js"),"utf8");
function setup(mobile){
  let slots=[],cursor=0,effects=[],notifications=[],backCount=0,pop;
  const history={state:{route:1},pushState(s){this.state=s;},back(){backCount++;this.state={route:1};}};
  const ctx={Math,history,location:{href:"https://local/c/test"},agyOriginalContextMenu(){},
    window:{matchMedia:()=>({matches:mobile,addEventListener(){},removeEventListener(){}}),
      addEventListener:(name,f)=>{pop=f;},removeEventListener:()=>{pop=null;}},
    A:{Fragment:"fragment",
      useState(v){const i=cursor++;if(!(i in slots))slots[i]=typeof v==="function"?v():v;return[slots[i],v=>slots[i]=v];},
      useRef(v){const i=cursor++;return slots[i]??(slots[i]={current:v});},
      useEffect(f,deps){const i=cursor++;if(!effects[i])effects[i]={deps,cleanup:f()};},
      createElement(type,props,...children){if(props?.ref)props.ref.current={showModal(){},close(){}};return{type,props:props||{},children};}
    }};
  vm.createContext(ctx);vm.runInContext(source,ctx);
  return{ctx,history,get pop(){return pop;},get backCount(){return backCount;},
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
assert.equal(buttons[2].props.disabled,true);buttons[1].props.onClick();
assert.equal(closed,1);assert.equal(calls,1);
tree.props.onCancel({preventDefault(){}});assert.equal(closed,2);
h.pop();assert.equal(closed,3);h.cleanup();assert.equal(h.backCount,1);
h=setup(false);tree=h.render("zYb",{items});assert.equal(tree.type,h.ctx.agyOriginalContextMenu);h.cleanup();
h=setup(true);const primary={type:"model",props:{},children:[]},desktop={type:"desktop-settings",props:{},children:[]};
tree=h.render("AgyComposerModelControl",{primary,desktop});assert.equal(tree.children[0],primary);h.cleanup();
h=setup(false);tree=h.render("AgyComposerModelControl",{primary,desktop});assert.equal(tree,desktop);h.cleanup();
console.log("PASS mobile composer: adaptive menu, supported actions, disabled state, close/Back cleanup, desktop fallback");
