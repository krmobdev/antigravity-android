package com.agy.ui.splash;

import android.app.Activity;
import android.os.Build;
import android.view.View;

/**
 * API31+: fade the system splash above the opaque native startup surface.
 * The WebView stays covered until a committed usable UI frame is available.
 */
public final class SplashExitInstaller {

    private SplashExitInstaller() {
    }

    public static void install(Activity activity, View dotSplash) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashAnim31.install(activity, dotSplash);
        }
    }
}
