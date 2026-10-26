/** Observe real content; expiration only releases observers, never signals ready. */
function arm() {
  if (!global.document || state.signalled) return;
  if (state.cleanup) state.cleanup();
  isUiRendered();
  if (state.signalled) return;
  var observer, poller, expiry;
  function cleanup() {
    if (observer) observer.disconnect();
    if (poller) global.clearInterval(poller);
    if (expiry) global.clearTimeout(expiry);
  }
  state.cleanup = cleanup;
  if (global.MutationObserver) {
    observer = new global.MutationObserver(isUiRendered);
    var target = global.document.getElementById('root') || global.document.body || global.document.documentElement;
    if (target) observer.observe(target, {childList: true, subtree: true, attributes: true});
  }
  poller = global.setInterval(isUiRendered, 100);
  expiry = global.setTimeout(cleanup, 60000);
}
