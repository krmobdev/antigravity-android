package com.agy.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transition filter for agent notifications. Intentionally Android-free so it
 * can be tested with javac and does not mistake initial IDLE for completion.
 */
public final class AgentTaskTracker {
    public static final int IDLE = 1;
    public static final int RUNNING = 2;
    public static final int BUSY = 4;
    public static final int MAX_TASKS = 16;
    // Stale WebView RUNNING updates may arrive milliseconds after native FINISHED.
    private static final long FINISHED_GRACE_MS = 4000;

    public enum Event { NONE, STARTED, PROGRESS, FINISHED }

    public static final class Change {
        public final Event event;
        public final String conversationId;
        public final String stage;
        public final long startedAt;

        Change(Event event, String id, String stage, long startedAt) {
            this.event = event;
            this.conversationId = id;
            this.stage = stage;
            this.startedAt = startedAt;
        }
    }

    private static final class Task {
        final long startedAt;
        String stage;
        long updatedAt;
        Task(long startedAt, String stage) {
            this.startedAt = startedAt;
            this.updatedAt = startedAt;
            this.stage = stage;
        }
    }

    private final Map<String, Task> running = new LinkedHashMap<>();
    private final Map<String, Long> recentlyFinished = new LinkedHashMap<>();
    private final long minUpdateMs;

    public AgentTaskTracker(long minUpdateMs) {
        this.minUpdateMs = Math.max(0L, minUpdateMs);
    }

    public synchronized Change observe(String id, int status, boolean fullyIdle,
                                       String rawStage, long nowMs) {
        return observe(id, status, fullyIdle, rawStage, nowMs, false);
    }

    /** A genuine native RUNNING event can start a new task without waiting for UI cooldown. */
    public synchronized Change observe(String id, int status, boolean fullyIdle,
                                       String rawStage, long nowMs, boolean nativeSource) {
        if (id == null || id.isEmpty() || id.length() > 128 ||
                !id.matches("[A-Za-z0-9_+:-]+")) {
            return new Change(Event.NONE, "", "", 0);
        }
        Task existing = running.get(id);
        if (status == IDLE && fullyIdle) {
            if (existing == null) {
                return new Change(Event.NONE, id, "", 0);
            }
            running.remove(id);
            recentlyFinished.put(id, nowMs);
            if (recentlyFinished.size() > MAX_TASKS * 2) {
                recentlyFinished.remove(recentlyFinished.keySet().iterator().next());
            }
            return new Change(Event.FINISHED, id, existing.stage, existing.startedAt);
        }
        if (status != RUNNING && status != BUSY) {
            return new Change(Event.NONE, id, "", 0);
        }

        String stage = sanitize(rawStage);
        if (existing == null) {
            Long lastFinish = recentlyFinished.get(id);
            if (!nativeSource && lastFinish != null && nowMs >= lastFinish &&
                    nowMs - lastFinish < FINISHED_GRACE_MS) {
                // Ignore a delayed UI RUNNING snapshot from the just-finished task.
                return new Change(Event.NONE, id, "", 0);
            }
            if (running.size() >= MAX_TASKS) {
                // Avoid eviction: dropping a running task could lead to a false
                // completion alert when its old state is replayed.
                return new Change(Event.NONE, id, "", 0);
            }
            recentlyFinished.remove(id);
            running.put(id, new Task(nowMs, stage));
            return new Change(Event.STARTED, id, stage, nowMs);
        }

        if (!stage.equals(existing.stage) && nowMs - existing.updatedAt >= minUpdateMs) {
            existing.stage = stage;
            existing.updatedAt = nowMs;
            return new Change(Event.PROGRESS, id, stage, existing.startedAt);
        }
        return new Change(Event.NONE, id, existing.stage, existing.startedAt);
    }

    private static String sanitize(String s) {
        if (s == null || s.trim().isEmpty()) return "Агент выполняет задачу";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 120; i++) {
            char ch = s.charAt(i);
            if (ch >= 32 && ch != 127) sb.append(ch);
            else sb.append(' ');
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "Агент выполняет задачу" : result;
    }
}
