package com.agy.core;

/** Observable startup milestones only; no elapsed-time or invented percentage. */
public final class CoreStartupProgress {
    public static final int PREPARING = 0, STARTING = 1, CONNECTING = 2;
    public static final class Snapshot {
        public final long attempt;
        public final int stage;
        private Snapshot(long attempt, int stage) { this.attempt = attempt; this.stage = stage; }
    }
    private long attempt;
    private int stage = PREPARING;
    public synchronized long begin() { stage = PREPARING; return ++attempt; }
    public synchronized void advance(long id, int next) {
        if (id != attempt || next < stage || next > CONNECTING) return;
        stage = next;
    }
    public synchronized Snapshot snapshot() { return new Snapshot(attempt, stage); }
}
