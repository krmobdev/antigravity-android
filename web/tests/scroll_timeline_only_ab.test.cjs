#!/usr/bin/env node
// A/B contract: only the OverlayScrollbar feature probe is disabled.
// This tests code behavior, not Android compositor performance.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const original = fs.readFileSync(path.join(root, 'main.js'), 'utf8');
const generated = fs.readFileSync(process.argv[2] || path.join(root, 'generated/main.js'), 'utf8');
const oldFn = 'function G$a(){var a=globalThis.ScrollTimeline;return typeof a==="function"?a:null}';
const newFn = 'function G$a(){return null/* AGY_SCROLL_TIMELINE_ONLY_AB */}';
assert.equal(original.split(oldFn).length - 1, 1, 'original v2.4 source must have exactly one feature probe');
assert.equal(generated.split(newFn).length - 1, 1, 'generated bundle must contain exactly one isolated patch');
assert.equal(generated.includes(oldFn), false, 'generated bundle must not include original probe');
assert.equal(original.includes(newFn), false, 'upstream source must remain unchanged');
assert.ok(generated.includes('timeline:new qa({source:ba,axis:e?"x":"y"})'), 'leave stock overlay animation code intact');
assert.ok(generated.includes('m||R()'), 'retain stock fallback for scrollbar thumb positioning');
for (const [label, snippet, want] of [
  ['original', oldFn, true],
  ['diagnostic', newFn, false],
]) {
  const ctx = { ScrollTimeline: function FakeScrollTimeline() {} };
  vm.createContext(ctx);
  const result = vm.runInContext(snippet + '\nG$a()', ctx);
  assert.equal(typeof result === 'function', want, label + ' with fake global ScrollTimeline');
  assert.equal(typeof ctx.ScrollTimeline, 'function', 'must not tamper with global API');
}
console.log('PASS: exact v2.4 G$a anchor; only generated code modified');
console.log('PASS: ScrollTimeline disabled in OverlayScrollbar, global API remains available');
console.log('PASS: native scroll and stock scrollbar fallback code preserved');
console.log('NOTE: no on-device fling regression is asserted by this source-level test');
