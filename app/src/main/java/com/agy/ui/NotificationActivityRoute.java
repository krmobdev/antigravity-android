package com.agy.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

/** Notifications reuse the live launcher host rather than create a second WebView. */
public final class NotificationActivityRoute {
    private NotificationActivityRoute() {}

    public static Intent intent(Context context, String id) {
        if (id == null || !id.matches("[A-Za-z0-9_+:-]{1,128}"))
            throw new IllegalArgumentException("Invalid conversation ID");
        MainActivity live = MainActivity.activeActivity;
        Intent launch = null;
        if (live != null && !live.isFinishing() && !live.isDestroyed()
                && enabled(context, live.getComponentName())) {
            launch = new Intent().setComponent(live.getComponentName());
        }
        if (launch == null)
            launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) launch = new Intent(context, MainActivity.class);
        launch.setAction(MainActivity.ACTION_OPEN_AGENT_CONVERSATION);
        launch.setData(Uri.parse("agy://conversation/" + Uri.encode(id)));
        launch.putExtra(MainActivity.EXTRA_AGENT_CONVERSATION_ID, id);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return launch;
    }

    public static PendingIntent pending(Context context, String id) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        Intent launch = intent(context, id);
        return PendingIntent.getActivity(context, id.hashCode(), launch, flags);
    }

    private static boolean enabled(Context context, ComponentName component) {
        if (!context.getPackageName().equals(component.getPackageName())) return false;
        String name = component.getClassName();
        if (!name.equals(MainActivity.class.getName())
                && !name.equals("com.agy.ui.launcher.LauncherLightActivity")
                && !name.equals("com.agy.ui.launcher.LauncherDarkActivity")) return false;
        try {
            PackageManager pm = context.getPackageManager();
            int state = pm.getComponentEnabledSetting(component);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true;
            if (state != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) return false;
            ActivityInfo info = pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS);
            return info.enabled && info.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException unavailable) {
            return false;
        }
    }

    /** A resumed legacy entry forwards through Android's normal singleTask routing. */
    static boolean activate(Activity sender, MainActivity host, Intent original) {
        if (enabled(sender, host.getComponentName())) {
            try {
                sender.startActivity(new Intent(original).setComponent(host.getComponentName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                return true;
            } catch (RuntimeException unavailable) {
                // An icon switch can disable the component between lookup/start.
            }
        }
        host.onNewIntent(new Intent(original));
        return bringToFront(host);
    }

    /** Own tasks only; does not require controlling any other application. */
    static boolean bringToFront(Activity host) {
        try {
            ActivityManager manager = (ActivityManager) host.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return false;
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                if (task.getTaskInfo().id == host.getTaskId()) {
                    task.moveToFront();
                    return true;
                }
            }
        } catch (RuntimeException unavailable) {
            // If the old task is gone, normal cold-start handling remains available.
        }
        return false;
    }
}
