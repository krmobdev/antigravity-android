package com.agy.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.agy.ui.MainActivity;

/**
 * Native task notification output. No Shizuku or WebView UI thread required to
 * POST an already observed task transition.
 *
 * IMPORTANT: events currently arrive from WebView; this class alone cannot
 * observe a completion while Chromium is completely suspended. See design doc.
 */
public final class AgentNotifications {
    private static final String TAG = "AGYAgentNotify";
    private static final String CHANNEL_WORKING = "agy_agent_working_v1";
    private static final String CHANNEL_FINISHED = "agy_agent_finished_v1";
    private static final int RUNNING_ID = 21001;
    private static final int FINISHED_ID = 21002;
    private static final AgentTaskTracker TRACKER = new AgentTaskTracker(5000);

    private final Context context;
    private final NotificationManager notifications;
    private static final Map<String, String> TITLES = new LinkedHashMap<>();

    public AgentNotifications(Context ctx) {
        context = ctx.getApplicationContext();
        notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannels();
    }

    public void observe(String conversationId, int status, boolean fullyIdle, String stage) {
        emit(conversationId, status, fullyIdle, stage, false);
    }

    public void observeNative(String conversationId, int status, boolean fullyIdle, String stage) {
        emit(conversationId, status, fullyIdle, stage, true);
    }

    private void emit(String conversationId, int status, boolean fullyIdle,
                      String stage, boolean nativeSource) {
        synchronized (TRACKER) {
        if (notifications == null || !notifications.areNotificationsEnabled()) return;
        // Record exactly when a Core/UI transition reached Android. This is
        // an observed completion, not the agent's internal final-token time.
        final long detectedAt = System.currentTimeMillis();
        AgentTaskTracker.Change update = TRACKER.observe(
                conversationId, status, fullyIdle, stage, detectedAt, nativeSource);
        if (update.event == AgentTaskTracker.Event.NONE) return;
        AgyDiagnostics.log(context, "notification", "type=" + update.event + " id=" + shortId(update.conversationId)
                + " detected_at_ms=" + detectedAt + " source=" + (nativeSource ? "native" : "webview"));
        try {
            if (update.event == AgentTaskTracker.Event.FINISHED) {
                notifications.cancel(update.conversationId, RUNNING_ID);
                notifications.notify(update.conversationId, FINISHED_ID, finished(update, detectedAt, nativeSource));
                AgyDiagnostics.log(context, "notification_posted", "type=FINISHED id=" + shortId(update.conversationId)
                        + " detected_at_ms=" + detectedAt + " posted_at_ms=" + System.currentTimeMillis());
            } else {
                notifications.notify(update.conversationId, RUNNING_ID, working(update));
            }
        } catch (SecurityException | IllegalArgumentException exc) {
            // The system can deny notifications at any time. Never affect agent state.
            Log.w(TAG, "Notification unavailable: " + exc.getClass().getSimpleName());
        }
        }
    }

    /** Display title from the canonical sidebar summary, never from chat text. */
    public void updateTitle(String id, String title) {
        if (id == null || !id.matches("[a-zA-Z0-9_+:-]{1,128}") || title == null) return;
        String cleaned = title.replaceAll("[\\r\\n\\t]", " ").trim();
        if (cleaned.isEmpty()) return;
        if (cleaned.length() > 72) cleaned = cleaned.substring(0, 72);
        synchronized (TITLES) {
            TITLES.put(id, cleaned);
            if (TITLES.size() > 128) TITLES.remove(TITLES.keySet().iterator().next());
        }
    }

    private String title(String id) {
        synchronized (TITLES) {
            String known = TITLES.get(id);
            return known == null ? "Antigravity" : known;
        }
    }

    private static String shortId(String id) {
        return id.substring(Math.max(0, id.length() - 6));
    }

    private void ensureChannels() {
        if (notifications == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel progress = new NotificationChannel(
                CHANNEL_WORKING, "Agent progress", NotificationManager.IMPORTANCE_LOW);
        progress.setDescription("Тихий статус длительных задач Antigravity");
        progress.setSound(null, null);
        progress.enableVibration(false);
        notifications.createNotificationChannel(progress);

        NotificationChannel finished = new NotificationChannel(
                CHANNEL_FINISHED, "Agent completed", NotificationManager.IMPORTANCE_HIGH);
        finished.setDescription("Агент закончил работу или требует внимания");
        finished.enableVibration(true);
        finished.setVibrationPattern(new long[] {0, 250, 150, 250});
        notifications.createNotificationChannel(finished);
    }

    private PendingIntent openConversation(String id) {
        Intent launch = new Intent(context, MainActivity.class);
        launch.setAction(MainActivity.ACTION_OPEN_AGENT_CONVERSATION);
        launch.setData(Uri.parse("agy://conversation/" + Uri.encode(id)));
        launch.putExtra(MainActivity.EXTRA_AGENT_CONVERSATION_ID, id);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, id.hashCode(), launch, flags);
    }

    private int notificationIcon() {
        int id = context.getResources().getIdentifier(
                "ic_notification", "drawable", context.getPackageName());
        return id != 0 ? id : android.R.drawable.stat_notify_sync;
    }

    private Notification.Builder builder(String channelId) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channelId)
                : new Notification.Builder(context);
    }

    private Notification working(AgentTaskTracker.Change event) {
        Notification.Builder b = builder(CHANNEL_WORKING)
                .setSmallIcon(notificationIcon())
                .setContentTitle(title(event.conversationId) + " · выполняется")
                .setContentText(event.stage)
                .setStyle(new Notification.BigTextStyle().bigText(event.stage))
                .setContentIntent(openConversation(event.conversationId))
                .setWhen(event.startedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_STATUS);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            b.setPriority(Notification.PRIORITY_LOW).setSound(null);
        }
        return b.build();
    }

    private Notification finished(AgentTaskTracker.Change event, long detectedAt, boolean nativeSource) {
        // Explicit seconds remain visible even when Android shows only "Сейчас".
        String completionTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date(detectedAt));
        String message = "Завершено в " + completionTime + " (" + (nativeSource ? "ядро" : "WebView") + "). Открыть диалог.";
        Notification.Builder b = builder(CHANNEL_FINISHED)
                .setSmallIcon(notificationIcon())
                .setContentTitle(title(event.conversationId) + " · завершена")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(openConversation(event.conversationId))
                .setAutoCancel(true)
                .setWhen(detectedAt)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_EVENT);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            b.setPriority(Notification.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        }
        return b.build();
    }
}
