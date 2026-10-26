package com.agy.bridge;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one capture generation. No Android device or stored audio in this class. */
final class PcmCaptureSession {
    interface Source {
        int read(byte[] buffer);
        void stop();
        void release();
    }
    interface Clock { long millis(); }
    static final int FRAME_BYTES = 1280;
    private static final int MAX_FRAMES = 20;
    private static final long LIMIT_MS = 4 * 60 * 1000L;
    private final Clock clock;
    private Session current;

    private static final class Session {
        final Source source;
        final long startedAt;
        final ArrayDeque<byte[]> frames = new ArrayDeque<>();
        final AtomicBoolean released = new AtomicBoolean();
        volatile boolean running = true;
        String failure;
        Session(Source source, long startedAt) { this.source = source; this.startedAt = startedAt; }
        void close() {
            if (!released.compareAndSet(false, true)) return;
            try { source.stop(); } catch (RuntimeException ignored) {}
            try { source.release(); } catch (RuntimeException ignored) {}
        }
    }
    PcmCaptureSession(Clock clock) { this.clock = clock; }
    synchronized boolean isRecording() { return current != null && current.running; }

    /** Caller initializes the hardware first; ownership transfers here. */
    synchronized String start(Source source) {
        if (isRecording()) { // Do not leak a source passed by a racing caller.
            try { source.stop(); } catch (RuntimeException ignored) {}
            try { source.release(); } catch (RuntimeException ignored) {}
            return "ERR_ALREADY_RECORDING";
        }
        if (current != null) current.close();
        final Session session = new Session(source, clock.millis());
        current = session;
        try {
            Thread worker = new Thread(() -> pump(session), "agy-native-pcm-capture");
            worker.setDaemon(true);
            worker.start();
            return "OK";
        } catch (RuntimeException failure) {
            session.running = false;
            session.failure = "ERR_PCM_START";
            session.close();
            return session.failure;
        }
    }

    private void pump(Session session) {
        byte[] buffer = new byte[FRAME_BYTES];
        try {
            while (session.running) {
                if (clock.millis() - session.startedAt > LIMIT_MS) {
                    fail(session, "ERR_PCM_LIMIT");
                    break;
                }
                int read;
                try { read = session.source.read(buffer); }
                catch (RuntimeException error) { fail(session, "ERR_PCM_READ"); break; }
                synchronized (this) {
                    // A stopped worker may return after a new recording started.
                    if (current != session || !session.running) break;
                    if (read < 0 || read > buffer.length || (read & 1) != 0) {
                        fail(session, "ERR_PCM_READ");
                        break;
                    }
                    if (read == 0) continue;
                    if (session.frames.size() >= MAX_FRAMES) {
                        // Never splice missing speech silently into the transcript.
                        fail(session, "ERR_PCM_OVERFLOW");
                        break;
                    }
                    session.frames.addLast(Arrays.copyOf(buffer, read));
                }
            }
        } finally {
            session.running = false;
            session.close(); // Includes read/limit/overflow failures.
        }
    }
    private synchronized void fail(Session session, String code) {
        if (current != session || !session.running) return;
        session.failure = code;
        session.running = false;
        session.frames.clear();
    }
    synchronized String failure() {
        return current == null ? "ERR_PCM_NOT_RECORDING" : current.failure;
    }
    synchronized byte[] poll() {
        if (current == null || current.failure != null || !current.running) return null;
        int count = 0, bytes = 0;
        for (byte[] frame : current.frames) {
            if (count++ == 8) break;
            bytes += frame.length;
        }
        byte[] result = new byte[bytes];
        int offset = 0;
        while (offset < bytes) {
            byte[] frame = current.frames.removeFirst();
            System.arraycopy(frame, 0, result, offset, frame.length);
            offset += frame.length;
        }
        return result;
    }
    void stop() {
        Session session;
        synchronized (this) {
            session = current;
            current = null;
            if (session != null) { session.running = false; session.frames.clear(); }
        }
        if (session != null) session.close();
    }
}
