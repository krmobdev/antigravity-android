package com.agy.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.agy.bridge.AgentNotifications;
import com.agy.bridge.AgentTaskTracker;
import com.agy.bridge.AgyDiagnostics;
import com.agy.ui.MainActivity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service-owned agent state watchers, independent from the Activity or WebView.
 * The WebView is used only to discover a new running conversation ID. Once
 * discovered, the native ConnectRPC stream independently observes completion.
 *
 * This does not launch agents, read chat content or modify the Core database.
 */
public final class NativeAgentStateObserver {
    private static final int MAX_WATCHES = 12;
    private static final long RECENT_NATIVE_FINISH_MS = 4000;
    private static final String PREFS = "agy_native_task_observer";
    private static final String ACTIVE = "active_conversations";
    private static final String FINISHED_PREFIX = "native_completed_at:";
    private static final long FINISHED_VALID_MS = 6L * 60 * 60 * 1000;
    private static volatile NativeAgentStateObserver instance;

    private final Context context;
    private final ExecutorService pool;
    private final Map<String, Watch> watches = new ConcurrentHashMap<>();
    // Bounded per-conversation ordering watermarks survive stream reconnects.
    private final Map<String, CoreAgentProgress> progress = new java.util.LinkedHashMap<>();
    private final Map<String, Long> recentNativeFinish = new ConcurrentHashMap<>();
    private final AgentNotifications notifications;
    private volatile boolean stopped = false;

    private static final class Watch {
        final String id;
        final CoreAgentProgress progress;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile NativeAgentStreamClient client;
        Watch(String id, CoreAgentProgress progress) { this.id = id; this.progress = progress; }
        void cancel() {
            cancelled.set(true);
            NativeAgentStreamClient current = client;
            if (current != null) current.cancel();
        }
    }

    public NativeAgentStateObserver(Context ctx) {
        context = ctx.getApplicationContext();
        notifications = new AgentNotifications(context);
        pool = Executors.newFixedThreadPool(MAX_WATCHES, new ThreadFactory() {
            private int serial;
            @Override public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "AGYNativeObserver-" + (++serial));
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public synchronized void start() {
        if (instance != null) throw new IllegalStateException("Observer already started");
        instance = this;
        Set<String> pending = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(ACTIVE, new HashSet<>());
        for (String id : new HashSet<>(pending)) watch(id);
        AgyDiagnostics.log(context, "native_observer", "started");
    }

    public synchronized void stop() {
        stopped = true;
        if (instance == this) instance = null;
        for (Watch watch : watches.values()) watch.cancel();
        pool.shutdownNow();
        AgyDiagnostics.log(context, "native_observer", "stopped");
    }

    /** Called by the WebView only to add/remove identifiers. */
    public static void onUiState(String id, int status, boolean fullyIdle) {
        NativeAgentStateObserver observer = instance;
        if (observer == null) return;
        if (status == AgentTaskTracker.RUNNING || status == AgentTaskTracker.BUSY) {
            // A stale WebView RUNNING snapshot must not resurrect the watcher
            // that native Core just confirmed as completed.
            Long finishedAt = observer.recentNativeFinish.get(id);
            long now = System.currentTimeMillis();
            if (finishedAt != null && now >= finishedAt &&
                    now - finishedAt < RECENT_NATIVE_FINISH_MS) return;
            observer.watch(id);
        }
        // UI-first IDLE must not cancel the native subscription before its
        // authoritative terminal outcome/error is delivered. Native owns teardown.
    }

    /** Synchronous, read-only cache query used by a fresh WebView after a deep link. */
    public static boolean wasRecentlyFinished(Context app, String id) {
        if (!valid(id)) return false;
        long when = app.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(FINISHED_PREFIX + id, 0L);
        long now = System.currentTimeMillis();
        return when > 0 && now >= when && now - when <= FINISHED_VALID_MS;
    }

    private void setNativeFinished(String id, boolean finished) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (finished) {
            editor.putLong(FINISHED_PREFIX + id, System.currentTimeMillis());
        } else {
            editor.remove(FINISHED_PREFIX + id);
        }
        editor.apply();
        MainActivity.dispatchNativeAgentState(id, finished);
    }

    private static boolean valid(String id) {
        return id != null && id.matches("[A-Za-z0-9_+:-]{1,128}");
    }

    private synchronized void watch(String id) {
        if (stopped || !valid(id) || watches.containsKey(id) ||
                watches.size() >= MAX_WATCHES) return;
        if (recentNativeFinish.size() > 32) {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> item : recentNativeFinish.entrySet()) {
                if (now - item.getValue() >= RECENT_NATIVE_FINISH_MS) {
                    recentNativeFinish.remove(item.getKey(), item.getValue());
                }
            }
        }
        CoreAgentProgress state = progress.get(id);
        if (state == null) {
            if (progress.size() >= 32) {
                for (String key : new java.util.ArrayList<>(progress.keySet())) {
                    if (!watches.containsKey(key)) { progress.remove(key); break; }
                }
            }
            state = new CoreAgentProgress();
            progress.put(id, state);
        }
        Watch watch = new Watch(id, state);
        watches.put(id, watch);
        persist();
        AgyDiagnostics.log(context, "native_observer", "watch id=" + shortId(id));
        pool.execute(() -> observe(watch));
    }

    private synchronized void unwatch(String id) {
        Watch watch = watches.remove(id);
        if (watch == null) return;
        watch.cancel();
        persist();
        AgyDiagnostics.log(context, "native_observer", "unwatch id=" + shortId(id));
    }

    private void persist() {
        Set<String> ids = new HashSet<>(watches.keySet());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(ACTIVE, ids).apply();
    }

    private static String shortId(String id) {
        return id.substring(Math.max(0, id.length()-6)).replaceAll("[^A-Za-z0-9]", "_");
    }

    private void observe(Watch watch) {
        int retries = 0;
        String lastError = "";
        while (!stopped && !watch.cancelled.get()) {
            if (CoreRuntimeState.serverUrl == null) {
                sleep(watch, 1200);
                continue;
            }
            NativeAgentStreamClient client = new NativeAgentStreamClient(context);
            watch.client = client;
            try {
                AgyDiagnostics.log(context, "native_observer",
                        "subscribe id=" + shortId(watch.id));
                final int[] lastState = {-1};
                client.subscribe(watch.id, event -> {
                    if (stopped || watch.cancelled.get() || watches.get(watch.id) != watch) return false;
                    CoreAgentProgress.Snapshot snapshot = watch.progress.accept(event);
                    if (snapshot == null) return true;
                    int status = snapshot.status;
                    boolean fullyIdle = snapshot.fullyIdle;
                    int code = status * 10 + (fullyIdle ? 1 : 0);
                    if (lastState[0] != code) {
                        lastState[0] = code;
                        AgyDiagnostics.log(context, "native_observer",
                                "state id=" + shortId(watch.id) + " status=" + status + " idle=" + fullyIdle);
                    }
                    if (stopped || watch.cancelled.get() || watches.get(watch.id) != watch) {
                        return false;
                    }
                    if (status == AgentTaskTracker.IDLE && fullyIdle) {
                        // Same notification tracker as the UI bridge: first arrival
                        // wins, regardless of which source saw completion first.
                        recentNativeFinish.put(watch.id, System.currentTimeMillis());
                        // Keep verified completion across Activity/WebView recreation.
                        // Clear only on authoritative native RUNNING, not UI replay.
                        setNativeFinished(watch.id, true);
                        notifications.observeNative(watch.id, status, true, snapshot.stage, snapshot.outcome);
                        AgyDiagnostics.log(context, "native_observer",
                                "finished id=" + shortId(watch.id));
                        unwatch(watch.id);
                        return false;
                    }
                    if (status == AgentTaskTracker.RUNNING || status == AgentTaskTracker.BUSY || status == 3) {
                        if (wasRecentlyFinished(context, watch.id)) {
                            setNativeFinished(watch.id, false);
                            AgyDiagnostics.log(context, "native_observer", "new_run id=" + shortId(watch.id));
                        }
                        notifications.observeNative(watch.id, status, false, snapshot.stage, snapshot.outcome);
                        return true;
                    }
                    return true;
                });
                retries = 0;
                lastError = "";
            } catch (Exception exc) {
                if (stopped || watch.cancelled.get()) break;
                String error = exc.getClass().getSimpleName();
                if (exc instanceof java.io.IOException) {
                    String detail = exc.getMessage();
                    if (detail != null && detail.matches("(?:HTTP_[0-9]{3}|CONNECT_[a-z_]{1,32}|MIME_MISMATCH)")) {
                        error = detail;
                    }
                }
                // Do not spam private log files, leak tokens or log chat content.
                if (!error.equals(lastError)) {
                    AgyDiagnostics.log(context, "native_observer", "stream_error=" + error);
                    lastError = error;
                }
                retries++;
            } finally {
                watch.client = null;
                client.cancel();
            }
            if (!watch.cancelled.get()) {
                sleep(watch, Math.min(15000, 900L * (1L << Math.min(4, retries))));
            }
        }
    }

    private void sleep(Watch watch, long millis) {
        long stopAt = System.currentTimeMillis() + millis;
        while (!stopped && !watch.cancelled.get()) {
            long remain = stopAt - System.currentTimeMillis();
            if (remain <= 0) break;
            try {
                Thread.sleep(Math.min(remain, 300));
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
