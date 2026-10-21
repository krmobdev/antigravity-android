/**
 * patches/theme_sync/notify.js - push the resolved theme to native Android.
 */
function notify() {
  var theme = resolveTheme();
  if (theme === state.last) return theme;
  state.last = theme;
  state.notifications++;
  try {
    var isDark = (theme === 'dark');
    document.documentElement.classList.toggle('dark', isDark);
    document.documentElement.classList.toggle('light', !isDark);
    document.documentElement.style.colorScheme = theme;
  } catch (e) { /* ignore */ }
  try {
    if (window.Android) {
      state.hasAndroid = true;
      // One theme transition must produce ONE native window update.
      // setThemeByUser persists the selected mode and already repaints the
      // Activity. Calling onVisualThemeChanged afterwards redundantly persisted
      // the mode and posted another full window/decor repaint on the UI thread.
      if (typeof window.Android.setThemeByUser === 'function') {
        window.Android.setThemeByUser(theme);
      } else if (typeof window.Android.setTheme === 'function') {
        window.Android.setTheme(theme);
      } else if (typeof window.Android.onVisualThemeChanged === 'function') {
        window.Android.onVisualThemeChanged(theme);
      }
    }
  } catch (e) { /* never throw into JS */ }
  return theme;
}
