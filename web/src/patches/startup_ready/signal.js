/** First fragment: one readiness gate for hints, API calls and DOM observers. */
'use strict';
if (window.__agStartupReady && window.__agStartupReady.__installed) return;
var global = window;
var state = { signalled: false, scheduled: false, cleanup: null };
function signal(reason) {
  if (state.signalled || state.scheduled || !hasRenderedUi()) return;
  state.scheduled = true;
  // Allow actual layout to reach a paint; never replace proof with a timer.
  global.requestAnimationFrame(function () {
    global.requestAnimationFrame(function () {
      state.scheduled = false;
      if (state.signalled || !hasRenderedUi()) return;
      state.signalled = true;
      if (state.cleanup) state.cleanup();
      if (global.Android && typeof global.Android.onUiReady === 'function')
        global.Android.onUiReady(global.__AG_NATIVE_BRIDGE_TOKEN__);
    });
  });
}
