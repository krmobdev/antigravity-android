const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(process.argv[2], 'utf8');
const proof = new Set();
function page() {
  const events = new Map();
  const window = {Android: {wasAgentRecentlyFinished: id => proof.has(id)},
    addEventListener(name, callback) {
      if (!events.has(name)) events.set(name, []);
      events.get(name).push(callback);
    }};
  const document = {visibilityState: 'visible', addEventListener: window.addEventListener.bind(window)};
  vm.runInNewContext(source, {window, document, Map, Set});
  return {window, emit(name) { for (const callback of events.get(name) || []) callback(); }};
}
const id = 'retained-chat', running = 'other-running';
const old = page();
old.window.__AGY_VERIFIED_AGENT_OBSERVE__({conversationId: id, status: 2, fullyIdle: false});
// React retains a snapshot until its external-store subscriber is notified.
let snapshot = old.window.__AGY_VERIFIED_AGENT_GET__(id), updates = 0;
const dispose = old.window.__AGY_VERIFIED_AGENT_SUBSCRIBE__(() => {
  const next = old.window.__AGY_VERIFIED_AGENT_GET__(id);
  if(next !== snapshot) updates++;
  snapshot = next;
});
proof.add(id); // Native completion while the old WebView is in the background.
const notification = page();
assert.equal(notification.window.__AGY_VERIFIED_AGENT_GET__(id), true);
assert.equal(snapshot, false, 'new WebView must not silently mutate the old React snapshot');
old.emit('agy:host-resume');
assert.equal(snapshot, true, 'retained sidebar must receive native idle on first return');
assert.equal(old.window.__AGY_VERIFIED_AGENT_GET__(running), false);
assert.equal(updates, 1);
old.emit('agy:host-resume');
assert.equal(updates, 1, 'unchanged resume must not notify repeatedly');
proof.delete(id);
old.emit('agy:host-resume');
assert.equal(snapshot, false, 'new native run must restore the running row');
assert.equal(updates, 2);
proof.add(id);
old.emit('visibilitychange');
assert.equal(snapshot, true, 'visible page must refresh native proof');
dispose();
old.emit('agy:host-resume');
assert.equal(updates, 3, 'unmounted subscribers must stay removed');
console.log('PASS retained WebView snapshots follow native completion on first resume');
