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
  // DOM observations during Core hydration are not a user theme choice.
  // Keep native bars/preferences at their persisted palette until the host
  // confirms the committed usable frame. No timer may release this guard.
  if (window.__AG_NATIVE_UI_STARTING__ === true) return theme;
  try {
    if (window.Android) {
      state.hasAndroid = true;
      if (typeof window.Android.setThemeByUser === 'function') {
        window.Android.setThemeByUser(theme);
      } else if (typeof window.Android.setTheme === 'function') {
        window.Android.setTheme(theme);
      }
      if (typeof window.Android.onVisualThemeChanged === 'function') {
        window.Android.onVisualThemeChanged(theme);
      }
    }
  } catch (e) { /* never throw into JS */ }
  return theme;
}
