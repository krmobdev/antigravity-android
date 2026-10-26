/** Require visible usable UI, rather than an empty React root or spinner. */
function hasRenderedUi() {
  var doc = global.document, root = doc && doc.getElementById('root');
  if (!root) return false;
  var nodes = root.querySelectorAll(
    'textarea,[contenteditable="true"],[role="textbox"],button:not(:disabled),a[href],input:not([type="hidden"]),[role="button"],[data-turn-id],.message-row,.prose,[data-message-id]');
  for (var i = 0; i < nodes.length; i++) {
    var node = nodes[i];
    if (node.closest('[aria-hidden="true"],[inert]')) continue;
    var rect = node.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0 || rect.right <= 0 || rect.bottom <= 0
        || rect.left >= global.innerWidth || rect.top >= global.innerHeight) continue;
    var visible = true, parent = node;
    while (parent) {
      var style = global.getComputedStyle(parent);
      if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) {
        visible = false; break;
      }
      if (parent === root) break;
      parent = parent.parentElement;
    }
    if (visible) return true;
  }
  return false;
}
function isUiRendered() {
  if (hasRenderedUi()) signal('visible-ui-painted');
  return state.signalled;
}
