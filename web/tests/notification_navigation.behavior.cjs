const {execFileSync}=require('node:child_process');
const vm=require('node:vm'),assert=require('node:assert/strict');
const effect=execFileSync('python3',['-c',`import runpy; p=runpy.run_path(__import__('sys').argv[1]); print(p['CONTEXT_REPLACEMENT'].split(p['CONTEXT_MARKER'])[1].split('return null};function mMb')[0])`,process.argv[2]],{encoding:'utf8'});
let calls=[],layout,cleanup,listeners=new Map();
let context={window:{__AG_NATIVE_BRIDGE_TOKEN__:'test-token',Android:{onVisibleConversation:(token,id)=>{assert.equal(token,'test-token');calls.push(id)}},addEventListener:(k,f)=>listeners.set(k,f),removeEventListener:(k,f)=>{if(listeners.get(k)===f)listeners.delete(k)}},z:{useLayoutEffect:fn=>{layout=fn}},e:'/c/abc-123',g:'abc-123'};
vm.createContext(context);
vm.runInContext(effect,context);
assert.equal(calls.length,0,'render must not report a chat');
cleanup=layout();assert.equal(calls.pop(),'abc-123');
listeners.get('agy:host-resume')();assert.equal(calls.pop(),'abc-123');
cleanup();assert.equal(listeners.size,0);
function decodeSafe(s){try{return decodeURIComponent(s)}catch(_){return ''}}
for(let route of ['/', '/c/_new','/c/a%2Fb','/c/%','/settings']){context.e=route;context.g=route.startsWith('/c/')?decodeSafe(route.slice(3)):'';vm.runInContext(effect,context);cleanup=layout();assert.equal(calls.pop(),'');cleanup();}
context.e='/c/another-chat';context.g='another-chat';vm.runInContext(effect,context);layout();assert.equal(calls.pop(),'another-chat');
console.log('PASS committed current chat, list, invalid routes, resume and listener cleanup');

context.e='/c/A+B';context.g='B';vm.runInContext(effect,context);layout();assert.equal(calls.pop(),'B','use committed focused chat in split view');
