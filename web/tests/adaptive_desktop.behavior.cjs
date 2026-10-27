const fs=require("node:fs"),vm=require("node:vm"),assert=require("node:assert/strict"),cp=require("node:child_process");
const source=fs.readFileSync("web/src/mobile/composer.js","utf8");
function setup(desktop,width,coarse,upstream=false){
 let query;
 const ctx={window:{__AG_DESKTOP_MODE__:desktop,__AG_MOBILE_LAYOUT_QUERY__:typeof desktop==="boolean"?(desktop?"(max-width: 720px)":"(max-width: 720px), (pointer: coarse)"):undefined,
  matchMedia:q=>(query=q,{matches:width<=720||(q.includes("coarse")&&coarse),addEventListener(){},removeEventListener(){}})},
  A:{useState:f=>[typeof f==="function"?f():f,()=>{}],useEffect(){}},Lj:()=>({get:()=>({enabled:upstream})})};
 vm.createContext(ctx);vm.runInContext(source,ctx);
 return{ctx,get query(){return query;}};
}
let h=setup(true,400,true);assert.equal(h.ctx.useAgyMobileLayout(),true);
h=setup(true,900,true);assert.equal(h.ctx.useAgyMobileLayout(),false,"desktop landscape must not become mobile just because it has touch");
h=setup(false,900,true);assert.equal(h.ctx.useAgyMobileLayout(),true,"mobile preference remains mobile on touch landscape");
h=setup(undefined,1200,false);assert.equal(h.ctx.useAgyMobileLayout(),false);
const python="import sys,json;from pathlib import Path;sys.path.insert(0,'web');from patch_mobile_composer import transform;s=transform(Path('web/main.js').read_text());a=s.index('var rK=');b=s.index('const YXa=',a);print(json.dumps(s[a:b]))";
const result=cp.spawnSync("python",["-c",python],{encoding:"utf8"});assert.equal(result.status,0,result.stderr);
const actual=JSON.parse(result.stdout);
for(const [desktop,width,coarse,upstream,want] of [[true,400,true,false,true],[true,900,true,true,false],[false,900,true,false,true],[undefined,900,false,true,true]]){
 h=setup(desktop,width,coarse,upstream);vm.runInContext(actual, h.ctx);assert.equal(h.ctx.rK(),want);
}
h=setup(true,900,true);h.ctx.document={querySelectorAll:()=>[{closest:()=>null,getBoundingClientRect:()=>({width:100,height:100,top:10,bottom:110}),hasAttribute:()=>true}]};
h.ctx.innerHeight=600;h.ctx.getComputedStyle=()=>({display:"block",visibility:"visible",opacity:"1"});
assert.equal(h.ctx.agyDesktopOverlayOpen(),true);
h.ctx.window.__AG_DESKTOP_MODE__=false;assert.equal(h.ctx.agyDesktopOverlayOpen(),false);
console.log("PASS actual Core layout: desktop portrait/mobile landscape/desktop landscape/non-Android flags, popup Escape hint scoped to wide Desktop");
