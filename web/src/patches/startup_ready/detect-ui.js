/** A header alone is not an entry screen. Wait for content and styles. */
function renderedUiProof() {
  var doc = global.document, root = doc && doc.getElementById('root');
  if (!root || doc.readyState !== 'complete') return null;
  var shell = root.querySelector('[data-testid="mobile-open-settings"],[data-testid="mobile-back-to-home"],[data-testid="app-icon-new-conversation-button"],[data-testid="sidebar-header-logo"]');
  var primary = 'textarea,[contenteditable="true"],[role="textbox"],input:not([type="hidden"]),[data-turn-id],.message-row,.prose,[data-message-id]';
  // Auth/onboarding may have just buttons. Authenticated Core chrome cannot
  // dismiss startup while its editor/content is still being replaced.
  var nodes = root.querySelectorAll(primary + (shell ? '' : ',button:not(:disabled),a[href],[role="button"]'));
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
    if (visible) {
      var r = shell ? shell.getBoundingClientRect() : rect;
      var color = global.getComputedStyle(root);
      return {node:node, layout:[global.innerWidth,global.innerHeight,
        r.left,r.top,r.width,r.height,color.backgroundColor,color.color].join('|')};
    }
  }
  return null;
}
function hasRenderedUi() { return !!renderedUiProof(); }
function isUiRendered() {
  if (hasRenderedUi()) signal('visible-ui-painted');
  return state.signalled;
}
