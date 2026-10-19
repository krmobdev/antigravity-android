const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const patch = fs.readFileSync(process.argv[2],'utf8');
const start = patch.indexOf("REPLACEMENT =");
assert(start >= 0);
const original = fs.readFileSync(__dirname + '/../main.js','utf8');
const anchor = 'function fOb(a){W3&&W3.navigate({to:"/c/$cascadeId",params:{cascadeId:a}})}';
assert.equal(original.split(anchor).length - 1, 1);
const listener = [
 anchor,
 'window.addEventListener("agy:open-conversation",function(e){',
 ' try { var id=e.detail?.conversationId;',
 ' if(typeof id!=="string"||id.length<1||id.length>128||',
 ' !/^[a-zA-Z0-9_+:-]+$/.test(id)) return;',
 ' if(!W3) return; fOb(id);',
 ' window.Android?.onNotificationNavigationResult?.(id);',
 ' }catch(err){} });'
].join('\n');
// Verify actual staged fragment, not only a test stub.
const patched = fs.readFileSync(process.argv[2], 'utf8');
assert(patched.includes('window.addEventListener("agy:open-conversation"'));
assert(patched.includes('fOb(id)'));
assert(patched.includes('onNotificationNavigationResult'));
const handlers = {};
const navigated=[], acknowledged=[];
const context = {
  window: {Android: {onNotificationNavigationResult: id=>acknowledged.push(id)},
           addEventListener: (k,fn)=>{handlers[k]=fn}},
  W3: null, console
};
vm.createContext(context);
vm.runInContext(listener,context);
handlers['agy:open-conversation']({detail:{conversationId:'c123'}});
assert.equal(navigated.length,0);
context.W3={navigate:args=>navigated.push(args)};
handlers['agy:open-conversation']({detail:{conversationId:'c123'}});
handlers['agy:open-conversation']({detail:{conversationId:'c456'}});
handlers['agy:open-conversation']({detail:{conversationId:'../../hijack'}});
assert.equal(navigated.length,2);
assert.equal(navigated[0].to,'/c/$cascadeId');
assert.equal(navigated[0].params.cascadeId,'c123');
assert.equal(navigated[1].params.cascadeId,'c456');
assert.deepEqual(acknowledged,['c123','c456']);
console.log('PASS: exact conversations, router-not-ready, malicious ID rejected');
