package com.agy.ui.webview;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import android.widget.Toast;
import com.agy.core.CoreRuntimeState;

/** Audio only, on user request, restricted to the running Core's exact origin. */
public final class WebAudioPermission {
    public static final int REQUEST_AUDIO = 1003;
    private final Activity activity;
    private PermissionRequest pending;
    public WebAudioPermission(Activity activity) { this.activity = activity; }

    public void request(PermissionRequest request) {
        if (pending != null) cancel(pending);
        boolean audio = false;
        for (String resource : request.getResources())
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audio = true;
        if (!audio || !trusted(request) || activity.isFinishing() || activity.isDestroyed()
                || !activity.hasWindowFocus()) { request.deny(); return; }
        pending = request;
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            finish(true);
        else activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
    }
    private boolean trusted(PermissionRequest request) {
        return WebAudioOrigin.allowed(request.getOrigin().toString(), CoreRuntimeState.serverUrl);
    }
    public void result(int code, int[] grants) {
        if (code != REQUEST_AUDIO || pending == null) return;
        boolean granted = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
        finish(granted);
        if (!granted) Toast.makeText(activity,
                "Для записи голоса разрешите доступ к микрофону в настройках приложения.",
                Toast.LENGTH_LONG).show();
    }
    private void finish(boolean allowed) {
        PermissionRequest request = pending;
        pending = null;
        if (request == null) return;
        if (allowed && trusted(request) && !activity.isFinishing() && !activity.isDestroyed())
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        else request.deny();
    }
    public void cancel(PermissionRequest request) {
        if (pending == request) { pending = null; request.deny(); }
    }
    public void destroy() { if (pending != null) cancel(pending); }
}
