const fs=require("node:fs"),vm=require("node:vm"),assert=require("node:assert/strict");
const source=fs.readFileSync("web/main.js","utf8");
const start=source.indexOf("function bWb("),end=source.indexOf(";function dWb(",start);
assert.ok(start>0&&end>start);
const raw=source.slice(start,end);
function setup(project=false,placeholder=false,fail=false){
  const writes=[],updates=[],errors=[],toasts=[];
  const current={project:{id:"p",settings:{mode:"vetted",untouched:"keep"}},isPlaceholder:placeholder};
  const provider={getState:()=>current};
  const feature=project?{getProjectStateProvider:()=>provider,
    projectListStateProvider:{getState:()=>({loaded:true})},
    updateProject:async value=>{updates.push(value);if(fail)throw Error("fixture")}}:null;
  const ctx={SC:()=>({setPrefValues:async value=>{writes.push(value);if(fail)throw Error("fixture")}}),
    Xm:()=>value=>toasts.push(value),$t:()=>(...value)=>errors.push(value),
    xk:{},Lj:()=>({get:()=>undefined}),jV:()=>undefined,bS:()=>undefined,eS:()=>false,
    Hm:()=>project?"p":undefined,iV:()=>undefined,Pj:x=>x?.getState?.(),
    VC:()=>"default",Ui:{PERMISSION_PRESET:"preset",CASCADE_AUTO_EXECUTION_POLICY:"auto"},
    EM:()=>"default",FM:s=>s?.mode||"use_global",DM:()=>["default","request_review","vetted"],
    CM:{default:{label:"Default",prefValues:{preset:"default"}},
      request_review:{label:"Review",prefValues:{preset:"review"}},
      vetted:{label:"Vetted",prefValues:{preset:"vetted"}}},
    d:value=>value,gp:String,p:(schema,value)=>({...value}),Oh:{},Nh:{},
    W1a:mode=>({mode}),aW:()=>({lowercase:"project",capitalized:"Project"}),
    aWb:"icon",NL:"menu",OL:"trigger",PL:"panel",VL:"options",WL:"heading",
    W:"glyph",ty:"tooltip",QL:"option",UL:"separator",aj:(...x)=>x.join(" "),
    A:{Fragment:"fragment",useContext:()=>({projectManagementFeature:feature}),
      useMemo:fn=>fn(),useCallback:fn=>fn,useId:()=>"fixture",
      createElement:(type,props,...children)=>({type,props:props||{},children})}};
  vm.createContext(ctx);vm.runInContext(raw,ctx);
  return {ctx,writes,updates,errors,toasts};
}
function nodes(tree,type){if(!tree||typeof tree!=="object")return[];
  return(tree.type===type?[tree]:[]).concat((tree.children||[]).flat(Infinity).flatMap(n=>nodes(n,type)));}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
(async()=>{
  let h=setup();let menu=h.ctx.cWb({conversationId:undefined});
  assert.equal(h.writes.length,0,"render/open cannot change permissions");
  let option=nodes(menu,"option").find(n=>n.props["data-testid"]==="permissions-preset-option-request_review");
  option.props.onClick();await tick();
  assert.equal(h.writes.length,1);assert.equal(h.writes[0],h.ctx.CM.request_review.prefValues);
  assert.equal(h.updates.length,0);
  h=setup(true);menu=h.ctx.cWb({conversationId:"task"});
  option=nodes(menu,"option").find(n=>n.props["data-testid"]==="permissions-preset-option-request_review");
  option.props.onClick();await tick();
  assert.equal(h.writes.length,0);assert.equal(h.updates.length,1);
  assert.equal(h.updates[0].settings.mode,"request_review");
  assert.equal(h.updates[0].settings.untouched,"keep");
  h=setup(true,true);h.ctx.bWb("task").selectPreset("request_review");await tick();
  assert.equal(h.updates.length,0,"placeholder scope must not fake a successful save");
  h=setup(false,false,true);h.ctx.bWb().selectPreset("request_review");await tick();
  assert.equal(h.errors.length,1);assert.equal(h.toasts.length,1);
  console.log("PASS actual Core permissions: selection writes global/project settings once, opening is read-only, unrelated settings preserved, placeholder/failed saves handled");
})().catch(e=>{console.error(e);process.exit(1)});
