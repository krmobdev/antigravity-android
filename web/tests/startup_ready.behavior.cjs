const fs=require("node:fs"),vm=require("node:vm"),assert=require("node:assert/strict"),path=require("node:path");
const dir=path.join(__dirname,"../src/patches/startup_ready");
const source=["signal","error-capture","console-hook","detect-ui","arm","public-api"].map(n=>fs.readFileSync(path.join(dir,n+".js"),"utf8")).join("\n");
function fixture(noDocument=false){
 let nodes=[],calls=[],frames=[],intervals=new Map(),timers=new Map(),seq=0,observer,shell=null;
 const root={parentElement:null,style:{display:"block",visibility:"visible",opacity:"1"},querySelector:()=>shell,querySelectorAll:selector=>selector.includes("button:not")?nodes:nodes.filter(n=>n.primary!==false)};
 const ctx={innerWidth:360,innerHeight:800,console:{info(){},log(){},warn(){},error(){}},
  __AG_NATIVE_BRIDGE_TOKEN__:"fixture-token",Android:{onUiReady:t=>calls.push(t),onUiError(){}},
  requestAnimationFrame:f=>(frames.push(f),frames.length),
  setInterval:f=>(intervals.set(++seq,f),seq),clearInterval:id=>intervals.delete(id),
  setTimeout:f=>(timers.set(++seq,f),seq),clearTimeout:id=>timers.delete(id),
  getComputedStyle:e=>e.style||{display:"block",visibility:"visible",opacity:"1"},
  addEventListener(){},
  document:noDocument?null:{readyState:"complete",getElementById:()=>root,body:root},
  MutationObserver:class{constructor(f){this.callback=f;observer=this;}observe(){}disconnect(){this.disconnected=true;}}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext("(function(){"+source+"})();",ctx);
 function node(extra={}){return{parentElement:root,closest:()=>null,getBoundingClientRect:()=>({width:280,height:48,left:8,right:288,top:8,bottom:56}),...extra};}
 return{ctx,calls,root,node,setShell:n=>shell=n,setNodes:a=>nodes=a,paint(){const f=frames.shift();if(f)f();},
  flush(){for(let i=0;i<10&&frames.length;i++)this.paint();},
  mutate(){observer?.callback();},expire(){[...timers.values()].forEach(f=>f());},
  get cleaned(){return intervals.size===0&&timers.size===0&&(!observer||observer.disconnected);}};
}
let h=fixture();h.ctx.__agStartupReady.signal("external");h.ctx.console.info("[ConversationLoad] complete");h.expire();h.flush();
assert.equal(h.calls.length,0,"empty/spinner root must not become ready on a timer or console hint");assert.ok(h.cleaned);
h=fixture(true);h.ctx.__agStartupReady.signal("no-document");assert.equal(h.calls.length,0);
h=fixture();h.setNodes([h.node({closest:()=>({})})]);h.mutate();h.flush();assert.equal(h.calls.length,0,"hidden/inert control");
h=fixture();h.root.style.opacity="0";h.setNodes([h.node()]);h.mutate();h.flush();assert.equal(h.calls.length,0,"transparent ancestor");
h=fixture();h.setNodes([h.node({getBoundingClientRect:()=>({width:30,height:40,left:0,right:30,top:900,bottom:940})})]);h.mutate();h.flush();assert.equal(h.calls.length,0,"off-screen mounted menu");
h=fixture();h.setNodes([h.node()]);h.mutate();assert.equal(h.calls.length,0);h.paint();assert.equal(h.calls.length,0);h.paint();
assert.deepEqual(h.calls,["fixture-token"]);assert.ok(h.cleaned);h.ctx.__agStartupReady.signal("duplicate");h.flush();assert.equal(h.calls.length,1);
h=fixture();h.setNodes([h.node()]);h.mutate();h.paint();h.setNodes([]);h.paint();assert.equal(h.calls.length,0,"DOM proof disappeared before paint");
h.setNodes([h.node()]);h.mutate();h.flush();assert.equal(h.calls.length,1);
console.log("PASS startup ready: no timed/console/API bypass, visible controls and two frames, hidden/offscreen rejection, lost proof, exactly-once bridge and cleanup");

h=fixture();h.expire();assert.ok(h.cleaned);h.setNodes([h.node()]);
h.ctx.__agStartupReady.resume();h.flush();
assert.equal(h.calls.length,1,"foreground resume can recover after observer expiration");assert.ok(h.cleaned);
console.log("PASS startup foreground resume: expired observers rearm, actual DOM proof required, cleanup preserved");

h=fixture();h.setShell(h.node());h.setNodes([h.node({primary:false})]);h.mutate();h.flush();
assert.equal(h.calls.length,0,"Core header/settings button alone cannot reveal startup");
h.setNodes([h.node()]);h.mutate();h.flush();assert.equal(h.calls.length,1,"Core content unblocks readiness");
h=fixture();h.ctx.document.readyState="interactive";h.setNodes([h.node()]);h.mutate();h.flush();
assert.equal(h.calls.length,0,"document/styles still loading");
h.ctx.document.readyState="complete";h.mutate();h.flush();assert.equal(h.calls.length,1);
h=fixture();h.setNodes([h.node({primary:false})]);h.mutate();h.flush();
assert.equal(h.calls.length,1,"button-only auth/onboarding entry is still accessible");
h=fixture();h.setNodes([h.node()]);h.mutate();h.paint();h.setNodes([h.node()]);h.paint();
assert.equal(h.calls.length,0,"replaced startup content must not reveal an intermediate frame");
h.mutate();h.flush();assert.equal(h.calls.length,1);
h=fixture();let headerLeft=8;
h.setShell(h.node({getBoundingClientRect:()=>({left:headerLeft,top:0,width:40,height:40})}));
h.setNodes([h.node()]);h.mutate();h.paint();headerLeft=50;h.paint();
assert.equal(h.calls.length,0,"header layout changed between frames");
h.mutate();h.flush();assert.equal(h.calls.length,1);
h=fixture();h.setNodes([h.node()]);h.mutate();h.paint();h.root.style.color="rgb(255,255,255)";h.paint();
assert.equal(h.calls.length,0,"startup palette changed between frames");
h.mutate();h.flush();assert.equal(h.calls.length,1);
console.log("PASS startup reveal: header-only and loading-document rejection, auth fallback, content replacement/header/palette stability");
