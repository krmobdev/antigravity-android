package com.agy.ui.webview;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import com.agy.BuildConfig;
import com.agy.bridge.AndroidBridge;
import com.agy.theme.ThemeManager;

/** Creates and hardware-configures the host WebView. */
public final class WebViewFactory {

    private WebViewFactory() {
    }

    public static WebView create(Context context, AndroidBridge bridge, ThemeManager theme) {
        WebView view = new WebView(context);
        view.setBackgroundColor(theme.getBackgroundColor());
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.requestFocus(View.FOCUS_DOWN);
        // Hardware layer: hand compositing to the GPU (manifest enables HW accel).
        // A/B probe: use normal hardware-accelerated WebView compositor rather than
        // forcing a dedicated hardware view layer. Manifest acceleration remains true.
        Log.d("AGY_SCROLL", "WEBVIEW_LAYER=DEFAULT");
        view.addJavascriptInterface(bridge, "Android");
        // Android WebView needs an explicit focus grant for the soft keyboard to show.
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    Log.d("AGY_SCROLL", "NATIVE_TOUCH action=" + action
                            + " t=" + event.getEventTime()
                            + " y=" + Math.round(event.getY())
                            + " pointers=" + event.getPointerCount());
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    ((WebView) v).requestFocus(View.FOCUS_DOWN);
                }
                return false;
            }
        });

        WebSettingsFactory.configure(view);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        return view;
    }
}
