const assert=require("node:assert/strict"),fs=require("node:fs"),vm=require("node:vm"),path=require("node:path");
const root=path.join(__dirname,".."),source=fs.readFileSync(path.join(root,"main.js"),"utf8");
const patch=fs.readFileSync(path.join(root,"src/mobile/composer.js"),"utf8");
const start=source.indexOf("var d5a=a=>"),end=source.indexOf(";let NN;",start);
assert.ok(start>0&&end>start);
let recorder=source.slice(start,end).replace("catch(y){x(),f(gp(y)),q()}","catch(y){x(),q(),f(agyAudioError(y))}");
let states=[],stopped=0,attachments=0;
function setup(error,constructorFails=false){
  states=[];stopped=0;attachments=0;
  const stream={getTracks:()=>[{stop(){stopped++;}}]};
  const context={navigator:{mediaDevices:{getUserMedia:async()=>{if(error)throw error;return stream;}}},
    window:{setTimeout:()=>1,clearTimeout(){}},c5a:()=>true,d:v=>v,gp:String,
    MediaRecorder:class{constructor(){if(constructorFails)throw Error("device");}},
    A:{useState(v){let i=states.length;states.push(v);return[v,value=>states[i]=value];},
      useRef:v=>({current:v}),useCallback:f=>f,useEffect:()=>{}}};
  vm.createContext(context);
  vm.runInContext(patch+"\n"+recorder,context);
  return {context,hook:context.d5a(()=>attachments++)};
}
(async()=>{
  for(const name of ["NotAllowedError","NotFoundError","NotReadableError"]){
    const {hook}=setup({name});await hook.startRecording();
    assert.equal(states[0],false);assert.equal(typeof states[1],"string");
    assert.ok(states[1].length>20);assert.equal(attachments,0);
  }
  const {context,hook}=setup(null,true);await hook.startRecording();
  assert.equal(stopped,1);assert.equal(states[0],false);assert.ok(states[1]);assert.equal(attachments,0);
  assert.ok(context.agyAudioError({name:"NotAllowedError"}).includes("Нет доступа"));
  assert.ok(!context.agyAudioError({message:"private transcript"}).includes("private"));
  console.log("PASS mobile audio: denial/device failures remain visible, tracks released, no fake attachment or private error content");
})().catch(e=>{console.error(e);process.exit(1);});
