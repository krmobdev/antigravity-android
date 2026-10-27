/**
 * patches/theme_sync/public-api.js - expose probe and start.
 */
window.__agThemeSync = {
  __installed: true,
  getTheme: resolveTheme,
  sync: notify,
  releaseStartup: function () {
    if (window.__AG_NATIVE_UI_STARTING__ !== true) return notify();
    window.__AG_NATIVE_UI_STARTING__ = false;
    state.last = null; // Flush the final observed theme exactly once.
    return notify();
  },
  probe: function () {
    return {
      installed: state.installed,
      theme: resolveTheme(),
      last: state.last,
      notifications: state.notifications,
      hostTheme: hostTheme,
      hasAndroid: state.hasAndroid,
      observers: state.observer ? 1 : 0
    };
  }
};

if (doc && doc.readyState === 'loading') {
  doc.addEventListener('DOMContentLoaded', install);
} else {
  install();
}
