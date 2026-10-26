package com.agy.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Local user-initiated Android microphone recording, used only if browser
 * getUserMedia fails. Never starts from an automated task or page load.
 * Output is an AAC/MP4 attachment for the existing MediaSchema API.
 */
public final class NativeVoiceRecorder {
    private static final long MAX_BYTES = 15L * 1024L * 1024L;
    private final Context app;
    private MediaRecorder recorder;
    private File output;
    private long began;

    public NativeVoiceRecorder(Context context) {
        app = context.getApplicationContext();
    }

    public synchronized String start(Activity activity) {
        if (recorder != null) return "ERR_ALREADY_RECORDING";
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !activity.hasWindowFocus()) return "ERR_NOT_FOREGROUND";
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return "ERR_NO_PERMISSION";
        File file = null;
        MediaRecorder next = null;
        try {
            File folder = new File(app.getCacheDir(), "voice");
            if (!folder.isDirectory() && !folder.mkdirs()) return "ERR_CACHE";
            file = File.createTempFile("agy_voice_", ".m4a", folder);
            next = new MediaRecorder();
            next.setAudioSource(MediaRecorder.AudioSource.MIC);
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            next.setAudioEncodingBitRate(64000);
            next.setAudioSamplingRate(44100);
            next.setMaxDuration(5 * 60 * 1000);
            next.setOutputFile(file.getAbsolutePath());
            next.prepare();
            next.start();
            recorder = next;
            output = file;
            began = SystemClock.elapsedRealtime();
            return "OK";
        } catch (SecurityException e) {
            return releaseFailed(next, file, "ERR_PERMISSION_DENIED");
        } catch (IOException e) {
            return releaseFailed(next, file, "ERR_PREPARE_IO");
        } catch (RuntimeException e) {
            return releaseFailed(next, file, "ERR_START_FAILED");
        }
    }

    private String releaseFailed(MediaRecorder failed, File file, String reason) {
        if (failed != null) {
            try { failed.reset(); } catch (RuntimeException ignored) {}
            try { failed.release(); } catch (RuntimeException ignored) {}
        }
        if (file != null) file.delete();
        return reason;
    }

    /** Results contain only local recorder output, never an arbitrary file path. */
    public synchronized String stop() {
        if (recorder == null) return error("ERR_NOT_RECORDING");
        MediaRecorder active = recorder;
        File file = output;
        long durationMs = SystemClock.elapsedRealtime() - began;
        recorder = null;
        output = null;
        began = 0;
        try {
            active.stop();
        } catch (RuntimeException e) {
            try { active.release(); } catch (RuntimeException ignored) {}
            if (file != null) file.delete();
            return error("ERR_STOP_FAILED");
        }
        try { active.release(); } catch (RuntimeException ignored) {}
        try {
            if (file == null || file.length() < 128) return error("ERR_EMPTY_AUDIO");
            if (file.length() > MAX_BYTES) return error("ERR_AUDIO_TOO_LARGE");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) file.length());
            byte[] chunk = new byte[16 * 1024];
            try (FileInputStream in = new FileInputStream(file)) {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (buffer.size() + n > MAX_BYTES) return error("ERR_AUDIO_TOO_LARGE");
                    buffer.write(chunk, 0, n);
                }
            }
            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("mimeType", "audio/mp4");
            result.put("durationSeconds", Math.max(0.1, durationMs / 1000.0));
            result.put("data", Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP));
            return result.toString();
        } catch (Exception e) {
            return error("ERR_READ_AUDIO");
        } finally {
            if (file != null) file.delete();
        }
    }

    public synchronized void cancel() {
        MediaRecorder active = recorder;
        File file = output;
        recorder = null;
        output = null;
        began = 0;
        if (active != null) {
            try { active.stop(); } catch (RuntimeException ignored) {}
            try { active.release(); } catch (RuntimeException ignored) {}
        }
        if (file != null) file.delete();
    }

    private static String error(String code) {
        try { return new JSONObject().put("ok", false).put("error", code).toString(); }
        catch (Exception impossible) { return "{\"ok\":false,\"error\":\"ERR_NATIVE\"}"; }
    }
}
