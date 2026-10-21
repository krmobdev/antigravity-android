'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
let time = 1000;
const timers = [];
const logs = [];
const frames = [];
class FakeElement {
  constructor() {
    this._top = 0;
    this.scrollHeight = 6000;
    this.clientHeight = 800;
    this.isConnected = true;
    this.firstElementChild = null;
    this.listeners = {};
    this.nodeType = 1;
  }
  get scrollTop() { return this._top; }
  set scrollTop(value) { this._top = value; }
  scrollTo(options) { this.scrollTop = options.top; }
  scrollBy(options) { this.scrollTop += options.top; }
  scrollIntoView() {}
  addEventListener(type, handler) { (this.listeners[type] ||= []).push(handler); }
  dispatch(type, y = 200) {
    for (const callback of this.listeners[type] || []) callback({
      type, changedTouches: [{ clientY: y }]
    });
  }
  contains(node) { return node === this; }
}
class FakeResizeObserver {
  observe() {}
  disconnect() {}
}
const element = new FakeElement();
const ctx = {
  window: {},
  document: { querySelector: () => element },
  Element: FakeElement, ResizeObserver: FakeResizeObserver,
  performance: { now: () => time },
  console: { info: line => logs.push(line) },
  setInterval: callback => { timers.push(callback); return timers.length; },
  requestAnimationFrame: callback => { frames.push(callback); return frames.length; },
  Number, Math, Object, Error, JSON
};
const src = fs.readFileSync('web/scroll_diagnostics.js', 'utf8');
vm.runInNewContext(src, ctx);
assert.match(logs[0], /ready: numeric telemetry only/);
timers[0]();                       // discovers main chat
element.dispatch('touchstart');
time += 75;
element.dispatch('touchmove', 170);
element.scrollTop = 100;          // assignment must be traced
element.scrollTo({ top: 240 });   // JS scrolling must be traced
element.dispatch('touchend', 170);
time += 30;
frames.splice(0).forEach(fn => fn());
timers[1]();                       // flush numeric batch
const combined = logs.join('\n');
for (const event of ['attached', 'setter-active', 'touchstart',
  'touchmove', 'touchend', 'js-setScrollTop', 'js-scrollTo']) {
  assert.ok(combined.includes('"' + event + '"'), 'missing ' + event);
}
assert.equal(combined.includes('user-message-text'), false);
assert.equal(ctx.window.__AGY_SCROLL_DIAG_V1__, true);
console.log('PASS: scroll diagnostics emit touch, position and JS write events without message text');
