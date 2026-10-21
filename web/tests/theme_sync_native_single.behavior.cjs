'use strict';
// Run directly against the shipped theme_sync/notify.js fragment, not a copy.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const source = fs.readFileSync(process.argv[2] || 'web/src/patches/theme_sync/notify.js', 'utf8');
function runScenario(api) {
  const actions = [];
  const storage = {};
  let theme = 'dark';
  let actualClass = {dark: false,light: false};
  let cssScheme = null;
  const html = {
    classList: {
      toggle(name, enabled) {
        actualClass[name] = enabled;
        actions.push('class-' + name + '=' + enabled);
      }
    },
    style: {set colorScheme(value) {
      cssScheme = value;
      actions.push('css=' + value);
    }}
  };
  const ctx = vm.createContext({
    state: {last: null, notifications: 0, hasAndroid: false},
    resolveTheme: () => theme,
    document: {documentElement: html},
    window: {Android: api}
  });
  vm.runInContext(source + '\nthis.executeNotify = notify;', ctx);
  return {notify: () => ctx.executeNotify(),actions,
    theme: v => {theme = v;}, state: ctx.state,
    get dom(){return {cssScheme,...actualClass};}, storage};
}
{
  let persisted = [], visual = [];
  const t = runScenario({
    setThemeByUser(theme) {persisted.push(theme);},
    onVisualThemeChanged(theme) {visual.push(theme);}
  });
  t.notify();
  assert.deepEqual(persisted, ['dark']);
  assert.deepEqual(visual, [], 'never double-call native visual callback');
  assert.deepEqual(t.dom,{cssScheme:'dark',dark:true,light:false});
  t.notify();
  assert.deepEqual(persisted, ['dark'], 'duplicate MutationObserver event is a no-op');
  t.theme('light'); t.notify();
  assert.deepEqual(persisted, ['dark','light'], 'one write per actual theme transition');
  assert.deepEqual(visual, []);
  assert.deepEqual(t.dom,{cssScheme:'light',dark:false,light:true});
  t.theme('dark'); t.notify();
  assert.deepEqual(persisted, ['dark','light','dark']);
  assert.equal(t.state.notifications,3);
}
{
  let legacy=[], visual=[];
  const t=runScenario({
    setTheme(mode) {legacy.push(mode);},
    onVisualThemeChanged(mode) {visual.push(mode);}
  });
  t.notify();
  assert.deepEqual(legacy,['dark'], 'legacy bridge still uses one method');
  assert.deepEqual(visual, []);
}
{
  let visual=[];
  const t=runScenario({onVisualThemeChanged(mode) {visual.push(mode);}});
  t.notify();
  assert.deepEqual(visual,['dark'], 'fallback supported if visual-only bridge');
}
{
  const t=runScenario({});
  t.notify();
  assert.equal(t.state.hasAndroid,true);
  assert.equal(t.dom.cssScheme,'dark');
}
{
  const t=runScenario(null);
  t.notify();
  assert.equal(t.state.hasAndroid,false);
  assert.equal(t.dom.cssScheme,'dark');
}
{
  const t=runScenario({setThemeByUser() {throw new Error('native unavailable');}});
  assert.doesNotThrow(()=>t.notify(), 'native error never breaks JS theme rendering');
  assert.equal(t.dom.cssScheme,'dark');
}
console.log('PASS theme sync: exactly one native update, no duplicate, fallback and rendering preserved');
