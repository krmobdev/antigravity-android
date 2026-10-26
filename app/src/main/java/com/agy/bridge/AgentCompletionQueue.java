package com.agy.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Main-thread FIFO for completion alerts; independent of Android for deterministic tests. */
public final class AgentCompletionQueue {
    public static final long ALERT_INTERVAL_MS = 5000;
    public static final int MAX_PENDING = 128;
    public static final class Entry {
        public final String id;
        public final long completedAt;
        public final boolean nativeSource;
        public final int outcome;
        public Entry(String id, long completedAt, boolean nativeSource) {
            this(id, completedAt, nativeSource, 0);
        }
        public Entry(String id, long completedAt, boolean nativeSource, int outcome) {
            this.id = id; this.completedAt = completedAt; this.nativeSource = nativeSource;
            this.outcome = outcome == 1 || outcome == 2 ? outcome : 0;
        }
    }
    public interface Clock { long now(); }
    public interface Scheduler {
        void schedule(Runnable task, long delayMs);
        void cancel(Runnable task);
    }
    public interface Output {
        boolean isVisible(String id);
        boolean post(Entry entry, boolean headsUp);
        void save(List<Entry> pending);
    }
    private final Clock clock;
    private final Scheduler scheduler;
    private final Output output;
    private final LinkedHashMap<String, Entry> pending = new LinkedHashMap<>();
    private final Runnable drainTask = this::drain;
    private long lastAlert;
    private boolean hasAlert;

    public AgentCompletionQueue(Clock clock, Scheduler scheduler, Output output, List<Entry> restored) {
        this.clock = clock; this.scheduler = scheduler; this.output = output;
        for (Entry entry : restored) if (valid(entry) && pending.size() < MAX_PENDING) pending.put(entry.id, entry);
    }
    private static boolean valid(Entry e) {
        return e != null && e.id != null && e.id.matches("[A-Za-z0-9_+:-]{1,128}") && e.completedAt > 0;
    }
    public long remainingAlertDelay() {
        return hasAlert ? Math.max(0, ALERT_INTERVAL_MS - (clock.now() - lastAlert)) : 0;
    }
    public void restoreAlertDelay(long remaining) {
        remaining = Math.max(0, Math.min(ALERT_INTERVAL_MS, remaining));
        if (remaining > 0) { hasAlert = true; lastAlert = clock.now() - ALERT_INTERVAL_MS + remaining; }
    }
    public void enqueue(Entry entry) {
        if (!valid(entry)) return;
        if (!pending.containsKey(entry.id) && pending.size() >= MAX_PENDING) {
            // Preserve existing unacknowledged completions: never drop a queued
            // event because the system currently refuses notification delivery.
            throw new IllegalStateException("Notification backlog limit reached");
        }
        pending.put(entry.id, entry);
        save(); contextChanged();
    }
    /** Enrich a UI-first completion without adding a second FIFO alert. */
    public boolean correctOutcome(String id, int outcome) {
        Entry previous = pending.get(id);
        if (previous == null) return false;
        pending.put(id, new Entry(id, previous.completedAt, true, outcome));
        save();
        return true;
    }
    public void cancel(String id) {
        if (pending.remove(id) != null) { save(); contextChanged(); }
    }
    public void contextChanged() {
        scheduler.cancel(drainTask);
        scheduler.schedule(drainTask, 0);
    }
    private void save() { output.save(new ArrayList<>(pending.values())); }
    private void drain() {
        // A chat opened while an alert waits must receive a non-banner completion immediately.
        for (Entry entry : new ArrayList<>(pending.values())) {
            if (output.isVisible(entry.id)) {
                if (output.post(entry, false)) {
                    pending.remove(entry.id); save();
                }
            }
        }
        if (pending.isEmpty()) return;
        long remaining = remainingAlertDelay();
        if (remaining > 0) { scheduler.schedule(drainTask, remaining); return; }
        Entry entry = pending.values().iterator().next();
        // Recheck at delivery, not when Core originally reported completion.
        boolean headsUp = !output.isVisible(entry.id);
        if (!output.post(entry, headsUp)) {
            // Permission denied or Android service temporarily unavailable:
            // persist event, then retry rather than silently consume it.
            save();
            scheduler.schedule(drainTask, ALERT_INTERVAL_MS);
            return;
        }
        pending.remove(entry.id);
        if (headsUp) { hasAlert = true; lastAlert = clock.now(); }
        save();
        if (!pending.isEmpty()) scheduler.schedule(drainTask, headsUp ? ALERT_INTERVAL_MS : 0);
    }
}
