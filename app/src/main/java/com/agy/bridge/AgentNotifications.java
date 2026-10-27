package com.agy.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import com.agy.ui.MainActivity;

/**
 * Native task notification output. No Shizuku or WebView UI thread required to
 * POST an already observed task transition.
 *
 * NativeAgentStateObserver supplies completions independently of the WebView.
 */
public final class AgentNotifications {
    private static final String TAG = "AGYAgentNotify";
    private static final String CHANNEL_WORKING = "agy_agent_working_silent_v2";
    private static final String CHANNEL_FINISHED = "agy_agent_finished_v1";
    private static final String CHANNEL_IN_CHAT = "agy_agent_finished_in_chat_v1";
    private static CompletionDispatcher dispatcher;
    private static final int RUNNING_ID = 21001;
    private static final int FINISHED_ID = 21002;
    private static final AgentTaskTracker TRACKER = new AgentTaskTracker(0);

    private static AgentProgressQueue progressQueue;
    private final Context context;
    private final NotificationManager notifications;
    private static final Map<String, String> TITLES = new LinkedHashMap<>();

    public AgentNotifications(Context ctx) {
        context = ctx.getApplicationContext();
        notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannels();
        completionDispatcher();
    }

    public void observe(String conversationId, int status, boolean fullyIdle, String stage) {
        emit(conversationId, status, fullyIdle, null, false, 0);
    }

    public void observeNative(String conversationId, int status, boolean fullyIdle, String stage) {
        observeNative(conversationId, status, fullyIdle, stage, 0);
    }

    public void observeNative(String conversationId, int status, boolean fullyIdle, String stage, int outcome) {
        emit(conversationId, status, fullyIdle, stage, true, outcome);
    }

    private void emit(String conversationId, int status, boolean fullyIdle,
                      String stage, boolean nativeSource, int outcome) {
        synchronized (TRACKER) {
        // Permission denial does not discard a Core-observed completion.
        // Record exactly when a Core/UI transition reached Android. This is
        // an observed completion, not the agent's internal final-token time.
        final long detectedAt = System.currentTimeMillis();
        AgentTaskTracker.Change update = TRACKER.observe(
                conversationId, status, fullyIdle, stage, detectedAt, nativeSource);
        if (update.event == AgentTaskTracker.Event.NONE) {
            if (nativeSource && status == 1 && fullyIdle && outcome != 0) {
                CompletionDispatcher delivery = completionDispatcher();
                delivery.handler.post(() -> correctOutcome(conversationId, outcome, delivery));
            }
            return;
        }
        AgyDiagnostics.log(context, "notification", "type=" + update.event + " id=" + shortId(update.conversationId)
                + " detected_at_ms=" + detectedAt + " source=" + (nativeSource ? "native" : "webview"));
        try {
            if (update.event == AgentTaskTracker.Event.FINISHED) {
                progressQueue().finished(update.conversationId);
                if (notifications != null) notifications.cancel(update.conversationId, RUNNING_ID);
                CompletionDispatcher delivery = completionDispatcher();
                delivery.handler.post(() -> {
                    try {
                        delivery.queue.enqueue(new AgentCompletionQueue.Entry(
                                update.conversationId, detectedAt, nativeSource, outcome));
                    } catch (IllegalStateException full) {
                        Log.e(TAG, "Notification backlog full; retaining existing unacknowledged events", full);
                    }
                });
            } else {
                if (update.event == AgentTaskTracker.Event.STARTED) {
                    CompletionDispatcher delivery = completionDispatcher();
                    delivery.handler.post(() -> delivery.queue.cancel(update.conversationId));
                    notifications.cancel(update.conversationId, FINISHED_ID);
                }
                if (update.event == AgentTaskTracker.Event.STARTED) progressQueue().started(update);
                else progressQueue().progress(update);
            }
        } catch (SecurityException | IllegalArgumentException exc) {
            // The system can deny notifications at any time. Never affect agent state.
            Log.w(TAG, "Notification unavailable: " + exc.getClass().getSimpleName());
        }
        }
    }

    private AgentProgressQueue progressQueue() {
        synchronized (AgentNotifications.class) {
            if (progressQueue == null) {
                Handler handler = new Handler(Looper.getMainLooper());
                progressQueue = new AgentProgressQueue(SystemClock::uptimeMillis,
                        new AgentProgressQueue.Scheduler() {
                            public void schedule(Runnable task, long delay) { handler.postDelayed(task, delay); }
                            public void cancel(Runnable task) { handler.removeCallbacks(task); }
                        }, event -> {
                            try {
                                if (notifications != null && notifications.areNotificationsEnabled())
                                    notifications.notify(event.conversationId, RUNNING_ID, working(event));
                            } catch (SecurityException | IllegalArgumentException denied) {
                                Log.w(TAG, "Progress notification unavailable");
                            }
                        }, 1000);
            }
            return progressQueue;
        }
    }

    // A UI-first completion may beat the native terminal event. Enrich that
    // existing event without a second FIFO alert or resurrecting a dismissed one.
    private void correctOutcome(String id, int outcome, CompletionDispatcher delivery) {
        if (delivery.queue.correctOutcome(id, outcome) || notifications == null) return;
        try {
            for (android.service.notification.StatusBarNotification active : notifications.getActiveNotifications()) {
                if (FINISHED_ID != active.getId() || !id.equals(active.getTag())) continue;
                Notification old = active.getNotification();
                String text = completionText(old.when, outcome);
                Notification corrected = Notification.Builder.recoverBuilder(context, old)
                        .setContentTitle(title(id) + completionSuffix(outcome))
                        .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                        .setOnlyAlertOnce(true).build();
                notifications.notify(id, FINISHED_ID, corrected);
                return;
            }
        } catch (SecurityException | IllegalArgumentException denied) {
            Log.w(TAG, "Completion correction unavailable");
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
        progress.setDescription("Silent progress for Antigravity tasks");
        progress.setSound(null, null);
        progress.enableVibration(false);
        progress.setShowBadge(false);
        notifications.createNotificationChannel(progress);

        NotificationChannel finished = new NotificationChannel(
                CHANNEL_FINISHED, "Agent completed", NotificationManager.IMPORTANCE_HIGH);
        finished.setDescription("Agent finished or needs attention");
        finished.enableVibration(true);
        finished.setVibrationPattern(new long[] {0, 250, 150, 250});
        notifications.createNotificationChannel(finished);

        // DEFAULT permits the user's sound but does not request heads-up alerts.
        // Keep a separate channel: Android persists importance per channel.
        NotificationChannel inChat = new NotificationChannel(
                CHANNEL_IN_CHAT, "Agent completed in the open chat", NotificationManager.IMPORTANCE_DEFAULT);
        inChat.setDescription("Completion in the open chat: notification shade without a heads-up banner");
        inChat.enableVibration(false);
        notifications.createNotificationChannel(inChat);
    }

    private PendingIntent openConversation(String id) {
        return com.agy.ui.NotificationActivityRoute.pending(context, id);
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
                .setContentTitle(title(event.conversationId) + " · working")
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
        // Progress updates must never alert; completion alerts use separate channels.
        b.setDefaults(0).setSound(null).setVibrate(null)
                .setPriority(Notification.PRIORITY_LOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Runtime supports setSilent; the Termux compile SDK is older.
            try {
                Notification.Builder.class.getMethod("setSilent", boolean.class).invoke(b, true);
            } catch (ReflectiveOperationException unavailable) {
                // The low-importance no-sound channel remains the fallback.
            }
        }
        return b.build();
    }

    private Notification finished(String conversationId, long detectedAt, boolean nativeSource, boolean headsUp, int outcome) {
        // Explicit seconds remain visible even when Android shows only "Now".
        String message = completionText(detectedAt, outcome);
        Notification.Builder b = builder(headsUp ? CHANNEL_FINISHED : CHANNEL_IN_CHAT)
                .setSmallIcon(notificationIcon())
                .setContentTitle(title(conversationId) + completionSuffix(outcome))
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(openConversation(conversationId))
                .setAutoCancel(true)
                .setWhen(detectedAt)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_EVENT);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            b.setPriority(headsUp ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT)
                    .setDefaults(Notification.DEFAULT_SOUND | (headsUp ? Notification.DEFAULT_VIBRATE : 0));
        }
        return b.build();
    }

    private static String completionSuffix(int outcome) {
        return outcome == 1 ? " · error" : outcome == 2 ? " · canceled" : " · completed";
    }

    private static String completionText(long at, int outcome) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(at));
        String status = outcome == 1 ? "Error" : outcome == 2 ? "Canceled" : "Completed";
        return status + " at " + time + ". Open conversation.";
    }

    /** Must be called after Activity visibility or its committed conversation changes. */
    public static synchronized void contextChanged() {
        CompletionDispatcher current = dispatcher;
        if (current != null) current.handler.post(current.queue::contextChanged);
    }

    private CompletionDispatcher completionDispatcher() {
        synchronized (AgentNotifications.class) {
            if (dispatcher == null) dispatcher = new CompletionDispatcher(this);
            return dispatcher;
        }
    }

    private static final class CompletionDispatcher implements AgentCompletionQueue.Output {
        final Handler handler = new Handler(Looper.getMainLooper());
        final AgentNotifications owner;
        final android.content.SharedPreferences preferences;
        final AgentCompletionQueue queue;
        CompletionDispatcher(AgentNotifications owner) {
            this.owner = owner;
            preferences = owner.context.getSharedPreferences("agy_completion_queue", Context.MODE_PRIVATE);
            List<AgentCompletionQueue.Entry> restored = new ArrayList<>();
            try {
                JSONArray entries = new JSONArray(preferences.getString("pending", "[]"));
                for (int i = 0; i < Math.min(entries.length(), AgentCompletionQueue.MAX_PENDING); i++) {
                    JSONObject e = entries.getJSONObject(i);
                    restored.add(new AgentCompletionQueue.Entry(e.getString("id"), e.getLong("at"), e.optBoolean("native"), e.optInt("outcome", 0)));
                }
            } catch (Exception ignored) { /* Ignore an incomplete/corrupt saved queue. */ }
            queue = new AgentCompletionQueue(SystemClock::uptimeMillis, new AgentCompletionQueue.Scheduler() {
                public void schedule(Runnable task, long delay) { handler.postDelayed(task, delay); }
                public void cancel(Runnable task) { handler.removeCallbacks(task); }
            }, this, restored);
            queue.restoreAlertDelay(preferences.getLong("next_alert_at", 0) - System.currentTimeMillis());
            handler.post(queue::contextChanged);
        }
        public boolean isVisible(String id) {
            MainActivity activity = MainActivity.activeActivity;
            return activity != null && !activity.isDestroyed() && !activity.isFinishing()
                    && activity.isConversationVisible(id);
        }
        public boolean post(AgentCompletionQueue.Entry entry, boolean headsUp) {
            if (owner.notifications == null || !owner.notifications.areNotificationsEnabled()) return false;
            try {
                owner.notifications.notify(entry.id, FINISHED_ID,
                        owner.finished(entry.id, entry.completedAt, entry.nativeSource, headsUp, entry.outcome));
                AgyDiagnostics.log(owner.context, "notification_posted", "type=FINISHED id=" + shortId(entry.id)
                        + " popup=" + headsUp + " detected_at_ms=" + entry.completedAt
                        + " posted_at_ms=" + System.currentTimeMillis());
                return true;
            } catch (SecurityException | IllegalArgumentException e) {
                Log.w(TAG, "Completion notification unavailable: " + e.getClass().getSimpleName());
                return false;
            }
        }
        public void save(List<AgentCompletionQueue.Entry> pending) {
            JSONArray entries = new JSONArray();
            try {
                for (AgentCompletionQueue.Entry e : pending) entries.put(new JSONObject()
                        .put("id", e.id).put("at", e.completedAt).put("native", e.nativeSource).put("outcome", e.outcome));
                preferences.edit().putString("pending", entries.toString())
                        .putLong("next_alert_at", System.currentTimeMillis() + queue.remainingAlertDelay()).apply();
            } catch (org.json.JSONException ignored) { /* Validated primitive fields only. */ }
        }
    }
}
