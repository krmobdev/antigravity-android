/* KR SCROLL_DIAG_20261021 - temporary diagnostic only.
 * Records numeric scroll/touch/resize data, never message text or URLs.
 * Instrumentation is confined to the main chat viewport. */
(function () {
  'use strict';
  if (window.__AGY_SCROLL_DIAG_V1__) return;
  window.__AGY_SCROLL_DIAG_V1__ = true;

  var TAG = 'AGY_SCROLL_DIAG_V1';
  var viewport = null;
  var contentObserver = null;
  var events = [];
  var latest = null;
  var down = false;
  var lastTouch = -1;
  var lastScroll = -1;
  var frameId = 0;
  var trackingUntil = 0;
  var lastRecord = -1;
  var flushTimer = 0;
  var installedAt = performance.now();
  var patched = false;
  var originalScrollTo = Element.prototype.scrollTo;
  var originalScrollBy = Element.prototype.scrollBy;
  var originalIntoView = Element.prototype.scrollIntoView;

  function now() { return Math.round(performance.now()); }
  function num(n) { return Number.isFinite(n) ? Math.round(n * 10) / 10 : null; }
  function viewState() {
    if (!viewport) return null;
    return [num(viewport.scrollTop), num(viewport.scrollHeight),
      num(viewport.clientHeight)];
  }
  function record(type, value) {
    var state = viewState();
    if (!state) return;
    // Fixed fields: msSinceBoot, type, scrollTop, scrollHeight, clientHeight,
    // optional numerical parameters. No source HTML, page text or conversation IDs.
    events.push([now(), type, state[0], state[1], state[2], value == null ? null : value]);
    if (events.length >= 45) flush();
  }
  function flush() {
    if (!events.length) return;
    var batch = events.splice(0, 45);
    try {
      console.info(TAG + ' ' + JSON.stringify(batch));
    } catch (_) { /* diagnostics must never affect chat */ }
  }
  function positionFromArgs(args, axis) {
    var arg = args && args[0];
    if (typeof arg === 'number') return axis === 'top' ? num(args[1]) : num(arg);
    if (arg && typeof arg === 'object') return num(arg[axis]);
    return null;
  }
  function inViewport(node) {
    return !!viewport && !!node &&
      (node === viewport || (node.nodeType === 1 && viewport.contains(node)));
  }
  function calledAt() {
    try {
      var match = (new Error().stack || '').match(/main\.js:\d+:\d+/);
      return match ? match[0] : '?';
    } catch (_) { return '?'; }
  }
  function patchMethods() {
    if (patched) return;
    patched = true;
    if (typeof originalScrollTo === 'function') {
      Element.prototype.scrollTo = function () {
        if (this === viewport) record('js-scrollTo', [positionFromArgs(arguments, 'top'), calledAt()]);
        return originalScrollTo.apply(this, arguments);
      };
    }
    if (typeof originalScrollBy === 'function') {
      Element.prototype.scrollBy = function () {
        if (this === viewport) record('js-scrollBy', [positionFromArgs(arguments, 'top'), calledAt()]);
        return originalScrollBy.apply(this, arguments);
      };
    }
    if (typeof originalIntoView === 'function') {
      Element.prototype.scrollIntoView = function () {
        if (inViewport(this)) record('js-scrollIntoView', calledAt());
        return originalIntoView.apply(this, arguments);
      };
    }
  }
  function patchScrollTop(element) {
    var p = element, descriptor = null;
    while ((p = Object.getPrototypeOf(p))) {
      descriptor = Object.getOwnPropertyDescriptor(p, 'scrollTop');
      if (descriptor) break;
    }
    if (!descriptor || !descriptor.get || !descriptor.set) {
      record('setter-unavailable');
      return;
    }
    try {
      Object.defineProperty(element, 'scrollTop', {
        configurable: true, enumerable: descriptor.enumerable,
        get: function () { return descriptor.get.call(this); },
        set: function (value) {
          record('js-setScrollTop', [num(Number(value)), calledAt()]);
          return descriptor.set.call(this, value);
        }
      });
      record('setter-active');
    } catch (_) { record('setter-unavailable'); }
  }
  function frameTick() {
    frameId = 0;
    if (!viewport || !viewport.isConnected) return;
    var time = performance.now();
    var state = viewState();
    if (!latest || state[0] !== latest[0] || state[1] !== latest[1] ||
        state[2] !== latest[2]) {
      if (lastRecord < 0 || time - lastRecord >= 35 ||
          (latest && Math.abs(state[0] - latest[0]) > 120)) {
        record('frame', latest ? [num(state[0] - latest[0]), num(time - lastRecord)] : null);
        lastRecord = time;
      }
      latest = state;
    }
    if (down || time < trackingUntil) frameId = requestAnimationFrame(frameTick);
  }
  function track(milliseconds) {
    trackingUntil = Math.max(trackingUntil, performance.now() + milliseconds);
    if (!frameId) frameId = requestAnimationFrame(frameTick);
  }
  function handleTouch(e) {
    if (!viewport) return;
    var y = e.changedTouches && e.changedTouches[0] ?
      num(e.changedTouches[0].clientY) : null;
    if (e.type === 'touchstart') {
      down = true;
      record('touchstart', y);
      track(1800);
    } else if (e.type === 'touchmove') {
      if (now() - lastTouch >= 60) {
        record('touchmove', y);
        lastTouch = now();
      }
      track(1800);
    } else {
      down = false;
      record(e.type, y);
      track(1700);
    }
  }
  function onScroll() {
    var t = now();
    if (t - lastScroll >= 60) {
      record('scroll');
      lastScroll = t;
    }
    track(1400);
  }
  function attach(element) {
    if (element === viewport) return;
    if (contentObserver) contentObserver.disconnect();
    viewport = element;
    latest = null;
    lastRecord = -1;
    record('attached', num(installedAt));
    patchMethods();
    patchScrollTop(element);
    ['touchstart', 'touchmove', 'touchend', 'touchcancel'].forEach(function (event) {
      element.addEventListener(event, handleTouch, { capture: true, passive: true });
    });
    element.addEventListener('scroll', onScroll, { passive: true });
    element.addEventListener('scrollend', function () { record('scrollend'); }, { passive: true });
    contentObserver = new ResizeObserver(function () {
      record('resize');
      track(750);
    });
    contentObserver.observe(element);
    if (element.firstElementChild) contentObserver.observe(element.firstElementChild);
    if (element.firstElementChild && element.firstElementChild.firstElementChild) {
      contentObserver.observe(element.firstElementChild.firstElementChild);
    }
    track(500);
  }
  // 750ms discovery check avoids a document-wide mutation observer on every chat render.
  setInterval(function () {
    var element = document.querySelector('[data-testid="autoscroll-viewport"]');
    if (element && element !== viewport) attach(element);
  }, 750);
  flushTimer = setInterval(flush, 250);
  console.info(TAG + ' ready: numeric telemetry only');
})();