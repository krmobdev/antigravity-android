package com.agy.ui.webview;

import android.content.Context;
import android.util.Log;
import android.content.SharedPreferences;
import android.webkit.WebView;

import com.agy.bridge.AndroidBridge;
import com.agy.theme.ThemeManager;
import com.agy.util.AssetReader;

/**
 * Loads our asset UI (assets/web/index.html) with the app config injected ahead
 * of every app script, based at the core's https origin so relative assets and
 * ConnectRPC are same-origin.
 */
public final class AppLoader {

    private static final String TAG = "WebViewHost";
    private static final String APP_ASSET = "web/index.html";
    private static final String CACHE_STATE = "agy_webview_static_cache";
    private static final String CACHE_PACKAGE_UPDATE = "last_apk_update";

    /**
     * A release replaces local JS/CSS at the same paths. Older builds served
     * these as immutable for a year, so WebView can otherwise run stale scripts
     * after a successful APK update. Only clear HTTP/resource cache, never
     * cookies, web storage, auth data or user projects.
     */
    private static void refreshResourceCacheOnApkUpdate(Context context, WebView webView) {
        try {
            long installedAt = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
            SharedPreferences state = context.getSharedPreferences(CACHE_STATE, Context.MODE_PRIVATE);
            if (state.getLong(CACHE_PACKAGE_UPDATE, -1L) != installedAt) {
                webView.clearCache(true);
                state.edit().putLong(CACHE_PACKAGE_UPDATE, installedAt).apply();
                Log.i(TAG, "cleared stale WebView resource cache after APK update");
            }
        } catch (Exception e) {
            // Never block launch if package metadata or cache cleanup fails.
            Log.w(TAG, "cannot check WebView cache freshness: " + e.getClass().getSimpleName());
        }
    }

    private AppLoader() {
    }

    public static void load(Context context, WebView webView, ThemeManager theme,
                            AndroidBridge bridge) {
        String html = AssetReader.readString(context, APP_ASSET);
        if (html == null) {
            Log.e(TAG, "cannot read " + APP_ASSET + "; UI not loaded");
            return;
        }
        refreshResourceCacheOnApkUpdate(context, webView);
        int port = bridge.getEnginePort();
        // Paint the WebView in the persisted theme BEFORE loading so any frame
        // shown under the fading splash matches the in-app background (no
        // white/dark mismatch flash).
        if (theme != null) {
            theme.applyToWebView(webView);
        }
        String hostTheme;
        if (theme != null && theme.mode() == com.agy.theme.ThemeColors.MODE_DARK) {
            hostTheme = "dark";
        } else if (theme != null && theme.mode() == com.agy.theme.ThemeColors.MODE_LIGHT) {
            hostTheme = "light";
        } else {
            hostTheme = "system";
        }
        String origin = "https://127.0.0.1:" + port + "/";
        String historyUrl = origin + "?hostTheme=" + hostTheme;
        webView.loadDataWithBaseURL(origin, AppHtmlInjector.inject(html, bridge, theme),
                "text/html", "UTF-8", historyUrl);
    }
}
