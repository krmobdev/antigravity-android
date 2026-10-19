package com.agy.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.agy.bridge.AgentNotifications;
import com.agy.bridge.AgentTaskTracker;
import com.agy.bridge.AgyDiagnostics;

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
    private static final String PREFS = "agy_native_task_observer";
    private static final String ACTIVE = "active_conversations";
    private static volatile NativeAgentStateObserver instance;

    private final Context context;
    private final ExecutorService pool;
    private final Map<String, Watch> watches = new ConcurrentHashMap<>();
    private final AgentNotifications notifications;
    private volatile boolean stopped = false;

    private static final class Watch {
        final String id;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile NativeAgentStreamClient client;
        Watch(String id) { this.id = id; }
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
            observer.watch(id);
        } else if (status == AgentTaskTracker.IDLE && fullyIdle) {
            observer.unwatch(id);
        }
    }

    private static boolean valid(String id) {
        return id != null && id.matches("[A-Za-z0-9_+:-]{1,128}");
    }

    private synchronized void watch(String id) {
        if (stopped || !valid(id) || watches.containsKey(id) ||
                watches.size() >= MAX_WATCHES) return;
        Watch watch = new Watch(id);
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
                client.subscribe(watch.id, (status, fullyIdle) -> {
                    if (stopped || watch.cancelled.get() || watches.get(watch.id) != watch) {
                        return false;
                    }
                    if (status == AgentTaskTracker.IDLE && fullyIdle) {
                        // Same notification tracker as the UI bridge: first arrival
                        // wins, regardless of which source saw completion first.
                        notifications.observe(watch.id, status, true, null);
                        AgyDiagnostics.log(context, "native_observer",
                                "finished id=" + shortId(watch.id));
                        unwatch(watch.id);
                        return false;
                    }
                    if (status == AgentTaskTracker.RUNNING || status == AgentTaskTracker.BUSY) {
                        notifications.observe(watch.id, status, false, null);
                        return true;
                    }
                    return true;
                });
                retries = 0;
                lastError = "";
            } catch (Exception exc) {
                String error = exc.getClass().getSimpleName();
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
