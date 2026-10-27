/** First fragment: one readiness gate for hints, API calls and DOM observers. */
'use strict';
if (window.__agStartupReady && window.__agStartupReady.__installed) return;
var global = window;
var state = { signalled: false, scheduled: false, cleanup: null };
function signal(reason) {
  if (state.signalled || state.scheduled || !hasRenderedUi()) return;
  var proof = renderedUiProof();
  state.scheduled = true;
  // Allow actual layout to reach a paint; never replace proof with a timer.
  global.requestAnimationFrame(function () {
    global.requestAnimationFrame(function () {
      state.scheduled = false;
      var painted = renderedUiProof();
      if (state.signalled || !painted || painted.node !== proof.node || painted.layout !== proof.layout) return;
      state.signalled = true;
      if (state.cleanup) state.cleanup();
      if (global.Android && typeof global.Android.onUiReady === 'function')
        global.Android.onUiReady(global.__AG_NATIVE_BRIDGE_TOKEN__);
    });
  });
}
