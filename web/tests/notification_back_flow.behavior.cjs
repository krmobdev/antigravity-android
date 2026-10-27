/**
 * Regression for Home -> completion notification -> Back, with chat A/B.
 * Execute the actual staged v2.4 route bridge; the router/history are fake.
 * Device instrumentation separately covers Activity/WebView retention.
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const bundle = fs.readFileSync(process.argv[2] || 'web/generated/main.js', 'utf8');
const start = bundle.indexOf('let L5=null;function t8b(');
const end = bundle.indexOf('function u8b(', start);
assert(start >= 0 && end > start, 'the actual notification route must be present');
const routeSource = bundle.slice(start, end);
assert(routeSource.includes('AGY_V24_DEEPLINK'), 'run the patched production route, not a mock');

function scenario(initialChat) {
  let entries = ['/', '/c/' + initialChat];
  let index = 1;
  let navigations = 0;
  const notified = [];
  const listeners = new Map();
  const resolved = new Set();
  const win = {
    __AG_NATIVE_BRIDGE_TOKEN__: 'test-only',
    Android: {
      onVisibleConversation: (token,id) => {
        assert.equal(token, 'test-only');
        notified.push(['visible', id]);
      },
      onNotificationNavigationResult: (token,id) => {
        assert.equal(token, 'test-only');
        notified.push(['ack', id]);
      }
    },
    addEventListener(name,fn) {
      if (!listeners.has(name)) listeners.set(name, new Set());
      listeners.get(name).add(fn);
    },
    removeEventListener(name,fn) { listeners.get(name)?.delete(fn); },
    dispatchEvent(event) { for (const fn of [...(listeners.get(event.type) || [])]) fn(event); }
  };
  const router = {
    state: { location: { pathname: entries[index], search: {} } },
    subscribe(type,fn) {
      assert.equal(type,'onResolved');
      resolved.add(fn);
      return () => resolved.delete(fn);
    },
    navigate(options) {
      navigations++;
      const path = '/c/' + options.params.cascadeId;
      if (options.replace) entries[index] = path;
      else { entries = entries.slice(0,index+1); entries.push(path); index++; }
      router.state.location = { pathname:path, search:{} };
      for (const fn of [...resolved]) fn();
      return Promise.resolve();
    }
  };
  const context = { window:win, console };
  vm.createContext(context);
  vm.runInContext(routeSource,context);
  context.qaRouter = router;
  vm.runInContext('L5=qaRouter;window.__AGY_ATTACH_ROUTE_V24__(L5)',context);

  return {
    get path() { return entries[index]; },
    get entries() { return [...entries]; },
    get navigations() { return navigations; },
    get notifications() { return [...notified]; },
    get subscribed() { return resolved.size; },
    pressHome() { /* Android Home backgrounds the app; it does not modify WebView history. */ },
    openNotification(id) {
      win.dispatchEvent({type:'agy:open-conversation',detail:{conversationId:id}});
    },
    resume() { win.dispatchEvent({type:'agy:host-resume'}); },
    back() {
      if (index > 0) index--;
      router.state.location = {pathname:entries[index],search:{}};
      for (const fn of [...resolved]) fn();
      return entries[index];
    },
    dispose() { win.dispatchEvent({type:'pagehide'}); }
  };
}

async function sameChat() {
  const s=scenario('A');
  const previous=s.entries;
  for (let cycle=0;cycle<2;cycle++) {
    s.pressHome();
    s.openNotification('A');
    s.resume();
    await Promise.resolve();
    assert.equal(s.path,'/c/A');
    assert.deepEqual(s.entries,previous,'no new history layer for the same chat');
    assert.equal(s.navigations,0,'same chat never remounts via router navigation');
    assert(s.notifications.some(([kind,id])=>kind==='ack'&&id==='A'),'completion is acknowledged');
  }
  assert.equal(s.back(),'/','one Back should return to chat list');
  assert.equal(s.subscribed,1,'router subscription stays single');
  s.dispose();
  console.log('PASS same chat A: two Home/notification returns, no navigation, one Back -> list');
}

async function otherChat() {
  const s=scenario('B');
  s.pressHome();
  s.openNotification('A');
  await Promise.resolve();
  assert.equal(s.path,'/c/A','notification for A replaces visible B');
  assert.deepEqual(s.entries,['/','/c/A'],'B is replaced, never stacked');
  assert.equal(s.navigations,1);
  assert(s.notifications.some(([kind,id])=>kind==='ack'&&id==='A'));
  s.openNotification('A');
  await Promise.resolve();
  assert.equal(s.navigations,1,'duplicate A must not append history');
  assert.equal(s.back(),'/','one Back returns to list, not B');
  console.log('PASS different chat: background B -> completion A -> one Back -> list');
}

async function invalidAndFocused() {
  const s=scenario('B');
  s.openNotification('../bad');s.openNotification('_new/../../bad');
  assert.equal(s.navigations,0,'invalid target cannot navigate');
  s.openNotification('A');
  await Promise.resolve();
  assert.equal(s.path,'/c/A');
  s.resume();
  assert.equal(s.navigations,1,'host resume cannot navigate a second time');
  assert.equal(s.back(),'/');
  console.log('PASS invalid target + resumed/duplicate notification have no phantom Back layer');
}

(async () => {
  await sameChat();
  await otherChat();
  await invalidAndFocused();
})().catch(e=>{console.error(e);process.exitCode=1});
