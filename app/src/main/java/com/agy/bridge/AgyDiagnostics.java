package com.agy.bridge;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Diagnostic build only: bounded logcat and private rolling file, no chat text. */
public final class AgyDiagnostics {
    private static final String TAG = "AGY_DIAG";
    private static final long MAX_BYTES = 512 * 1024;
    private AgyDiagnostics() {}

    public static synchronized void log(Context context, String event, String attributes) {
        String kind = clean(event, 32);
        String detail = clean(attributes, 180);
        String line = System.currentTimeMillis() + " " + kind + " " + detail;
        Log.i(TAG, line);
        if (context == null) return;
        try {
            File folder = new File(context.getFilesDir(), "logs");
            if (!folder.exists() && !folder.mkdirs()) return;
            File output = new File(folder, "agy-diag.log");
            if (output.exists() && output.length() > MAX_BYTES) {
                File old = new File(folder, "agy-diag.prev.log");
                if (old.exists()) old.delete();
                output.renameTo(old);
            }
            try (FileOutputStream out = new FileOutputStream(output, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // Diagnostic failures must NEVER alter agent behavior.
        }
    }

    private static String clean(String input, int limit) {
        if (input == null) return "-";
        StringBuilder b = new StringBuilder(limit);
        for (int i = 0; i < input.length() && b.length() < limit; i++) {
            char c = input.charAt(i);
            b.append(c >= 32 && c < 127 ? c : '_');
        }
        return b.toString();
    }
}
