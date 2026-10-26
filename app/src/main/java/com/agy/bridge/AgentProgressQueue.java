package com.agy.bridge;

import java.util.HashMap;
import java.util.Map;

/** Coalesces only observed progress; the timer never creates a stage. */
public final class AgentProgressQueue {
    public interface Clock { long now(); }
    public interface Scheduler { void schedule(Runnable task, long delay); void cancel(Runnable task); }
    public interface Output { void post(AgentTaskTracker.Change change); }
    private final Clock clock;
    private final Scheduler scheduler;
    private final Output output;
    private final long interval;
    private final Map<String, State> states = new HashMap<>();
    private static final class State {
        long postedAt;
        AgentTaskTracker.Change pending;
        Runnable task;
    }
    public AgentProgressQueue(Clock clock, Scheduler scheduler, Output output, long interval) {
        this.clock = clock; this.scheduler = scheduler; this.output = output;
        this.interval = Math.max(0, interval);
    }
    public synchronized void started(AgentTaskTracker.Change change) {
        finished(change.conversationId);
        State state = new State();
        state.postedAt = clock.now();
        states.put(change.conversationId, state);
        output.post(change);
    }
    public synchronized void progress(AgentTaskTracker.Change change) {
        State state = states.get(change.conversationId);
        if (state == null) return;
        state.pending = change;
        if (state.task != null) return;
        state.task = () -> flush(change.conversationId, state);
        scheduler.schedule(state.task, Math.max(0, interval - (clock.now()-state.postedAt)));
    }
    private synchronized void flush(String id, State state) {
        if (states.get(id) != state) return;
        state.task = null;
        AgentTaskTracker.Change current = state.pending;
        state.pending = null;
        if (current != null) {
            state.postedAt = clock.now();
            output.post(current);
        }
    }
    public synchronized void finished(String id) {
        State state = states.remove(id);
        if (state != null && state.task != null) scheduler.cancel(state.task);
    }
}
