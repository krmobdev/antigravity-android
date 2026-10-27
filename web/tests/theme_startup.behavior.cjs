const fs=require("node:fs"),path=require("node:path"),vm=require("node:vm"),assert=require("node:assert/strict");
const dir=path.join(__dirname,"../src/patches/theme_sync");
const source=["config","resolve","notify","install","public-api"].map(n=>fs.readFileSync(path.join(dir,n+".js"),"utf8")).join("\n");
function fixture(starting=true){
 let bodyTheme="light",calls=[],observers=0;
 const body={classList:{contains:n=>n==="theme-"+bodyTheme}};
 const html={style:{},classList:{toggle(){}}};
 const ctx={URLSearchParams,location:{search:"?hostTheme=light"},__AG_HOST_THEME__:"light",
  document:{readyState:"complete",body,documentElement:html},addEventListener(){},
  Android:{setThemeByUser:t=>calls.push(["choice",t]),onVisualThemeChanged:t=>calls.push(["visual",t])},
  matchMedia:()=>({matches:false,addEventListener(){}}),
  MutationObserver:class{constructor(f){this.callback=f;observers++;}observe(){}},
  setTimeout(){throw Error("Theme startup must not release by a timer");}};
 if(starting)ctx.__AG_NATIVE_UI_STARTING__=true;
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext("(function(){"+source+"})();",ctx);
 return{ctx,calls,setBody:t=>bodyTheme=t,get observers(){return observers;}};
}
let h=fixture();
assert.equal(h.calls.length,0,"bootstrap theme is not a user preference write");
h.setBody("dark");h.ctx.__agThemeSync.sync();
assert.equal(h.calls.length,0,"transient Core dark hydration cannot repaint native bars");
h.setBody("light");h.ctx.__agThemeSync.sync();
assert.equal(h.calls.length,0,"native palette stays locked until compositor confirmation");
h.ctx.__agThemeSync.releaseStartup();
assert.deepEqual(h.calls,[["choice","light"],["visual","light"]],"only the final committed theme reaches native");
assert.equal(h.ctx.__AG_NATIVE_UI_STARTING__,false);
h.ctx.__agThemeSync.releaseStartup();h.ctx.__agThemeSync.sync();
assert.equal(h.calls.length,2,"duplicate ready callbacks do not repeat theme writes");
h.setBody("dark");h.ctx.__agThemeSync.sync();
assert.deepEqual(h.calls.slice(-2),[["choice","dark"],["visual","dark"]],"real later dark selection still works");
h.setBody("light");h.ctx.__agThemeSync.sync();
assert.deepEqual(h.calls.slice(-2),[["choice","light"],["visual","light"]],"real later light selection still works");
const before=h.calls.length;vm.runInContext("(function(){"+source+"})();",h.ctx);
assert.equal(h.observers,1);assert.equal(h.calls.length,before,"no duplicate installation/listener");
h=fixture(false);assert.deepEqual(h.calls,[["choice","light"],["visual","light"]],"legacy/non-native documents retain their original sync behavior");
h=fixture();h.setBody("dark");h.ctx.__agThemeSync.sync();
assert.equal(h.calls.length,0,"failed startup/retry cannot leak intermediate native theme writes");
console.log("PASS actual theme sync: Light-Dark-Light hydration never reaches native, compositor release once, later Light/Dark changes, legacy path and duplicate install");
