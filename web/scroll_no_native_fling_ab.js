/* KR custom fling A/B. Keep the proven direct touch drag; implement momentum
 * in requestAnimationFrame, independent of Chromium's delayed native fling.
 * Only the main chat scroll viewport is affected. No chat data is read.
 */
(function () {
  'use strict';
  if (window.__AGY_FLING_AB_INSTALLED__) return;
  window.__AGY_FLING_AB_INSTALLED__ = true;

  var style = document.createElement('style');
  style.textContent = '[data-testid="autoscroll-viewport"]{touch-action:none!important;overscroll-behavior:contain!important;}';
  (document.head || document.documentElement).appendChild(style);

  var node = null;
  var gesture = null;
  var raf = 0;
  var fling = null;
  // TODO(settings/scroll): expose validated momentum strength/decay in
  // Settings > Chat scrolling, with reset-to-default and persisted preferences.
  // Keep these proven defaults untouched until settings UI + regression tests land.
  // Speed is in CSS px/ms; decay is frame-rate independent (60Hz/120Hz).
  var MAX_SPEED = 3.2;
  var MIN_SPEED = 0.11;
  var DECAY_MS = 380;
  var STOP_SPEED = 0.025;

  function log(type, value) {
    try { console.info('AGY_FLING_AB ' + type + (value == null ? '' : ' ' + value)); } catch (_) {}
  }
  function maxTop() {
    return node ? Math.max(0, node.scrollHeight - node.clientHeight) : 0;
  }
  function cancelFling(reason) {
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
    if (fling) {
      fling = null;
      if (reason) log('FLING_CANCEL', reason);
    }
  }
  function frame(now) {
    raf = 0;
    if (!fling || !node || !node.isConnected || document.hidden) {
      cancelFling(null);
      return;
    }
    var elapsed = Math.max(0, Math.min(34, now - fling.lastTime));
    fling.lastTime = now;
    if (elapsed === 0) { raf = requestAnimationFrame(frame); return; }
    // Integrate exponentially damped velocity. Capped dt avoids long jumps
    // when JavaScript is temporarily starved by rendering or app lifecycle.
    var decay = Math.exp(-elapsed / DECAY_MS);
    var distance = fling.velocity * DECAY_MS * (1 - decay);
    fling.velocity *= decay;
    var before = node.scrollTop;
    var limit = maxTop();
    var target = Math.max(0, Math.min(limit, before + distance));
    node.scrollTop = target;
    if (Math.abs(fling.velocity) < STOP_SPEED ||
        Math.abs(node.scrollTop - before) < 0.01 ||
        target === 0 || target === limit) {
      cancelFling(null);
      log('FLING_END', Math.round(node.scrollTop));
      return;
    }
    raf = requestAnimationFrame(frame);
  }
  function beginFling(velocity) {
    cancelFling(null);
    velocity = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, velocity));
    if (!node || Math.abs(velocity) < MIN_SPEED) return;
    var top = node.scrollTop, end = maxTop();
    if ((top <= 0 && velocity < 0) || (top >= end && velocity > 0)) return;
    fling = { velocity: velocity, lastTime: performance.now() };
    log('FLING_START', Math.round(velocity * 1000));
    raf = requestAnimationFrame(frame);
  }
  function savePoint(t, y) {
    if (!gesture) return;
    gesture.samples.push({ t: t, y: y });
    while (gesture.samples.length > 2 &&
           gesture.samples[0].t < t - 125) gesture.samples.shift();
  }
  function onStart(e) {
    cancelFling('new-touch');
    if (e.touches.length !== 1) { gesture = null; return; }
    var touch = e.touches[0];
    var t = performance.now();
    gesture = { lastY: touch.clientY, startY: touch.clientY,
      startX: touch.clientX, lastMove: t, locked: false,
      samples: [{ t: t, y: touch.clientY }] };
  }
  function onMove(e) {
    if (!gesture || e.touches.length !== 1 || !node) return;
    var touch = e.touches[0];
    if (!gesture.locked) {
      var dy = touch.clientY - gesture.startY;
      var dx = touch.clientX - gesture.startX;
      if (Math.abs(dy) < 6) return;
      if (Math.abs(dx) > Math.abs(dy) * 1.25) { gesture = null; return; }
      gesture.locked = true;
    }
    if (e.cancelable) e.preventDefault();
    var delta = touch.clientY - gesture.lastY;
    gesture.lastY = touch.clientY;
    var before = node.scrollTop;
    node.scrollTop = Math.max(0, Math.min(maxTop(), before - delta));
    // A gesture pushing into a boundary must not build a large fling.
    if (Math.abs(node.scrollTop - before) > 0.01) {
      var t = performance.now();
      savePoint(t, touch.clientY);
      gesture.lastMove = t;
    }
  }
  function onEnd(e) {
    var old = gesture;
    gesture = null;
    if (!old || !old.locked || !node) return;
    if (e.type === 'touchcancel') return;
    var t = performance.now();
    // Finger pause before release intentionally suppresses inertia.
    if (t - old.lastMove > 100 || old.samples.length < 2) return;
    var last = old.samples[old.samples.length - 1];
    var first = old.samples[0];
    for (var i = old.samples.length - 2; i >= 0; i--) {
      if (last.t - old.samples[i].t > 110) break;
      first = old.samples[i];
    }
    var dt = last.t - first.t;
    if (dt < 8) return;
    var velocity = -(last.y - first.y) / dt;
    beginFling(velocity);
  }
  function attach(el) {
    if (node === el) return;
    cancelFling(null);
    gesture = null;
    if (node) {
      node.removeEventListener('touchstart', onStart, true);
      node.removeEventListener('touchmove', onMove, true);
      node.removeEventListener('touchend', onEnd, true);
      node.removeEventListener('touchcancel', onEnd, true);
    }
    node = el;
    node.addEventListener('touchstart', onStart, { capture: true, passive: true });
    node.addEventListener('touchmove', onMove, { capture: true, passive: false });
    node.addEventListener('touchend', onEnd, { capture: true, passive: true });
    node.addEventListener('touchcancel', onEnd, { capture: true, passive: true });
    log('ATTACHED');
  }
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) { cancelFling('hidden'); gesture = null; }
  });
  // Small periodic lookup; no MutationObserver and no page-wide touch listener.
  setInterval(function () {
    var el = document.querySelector('[data-testid="autoscroll-viewport"]');
    if (el && el !== node) attach(el);
  }, 450);
  log('READY_CUSTOM_FLING');
})();
