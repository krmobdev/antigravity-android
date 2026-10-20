package com.agy.gateway;

import com.agy.util.AtomicFileWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Private bearer capability for privileged localhost gateway operations.
 * Android's loopback bind is not an authentication boundary between apps.
 * Never log this token or expose it through WebView/JavaScript.
 */
public final class GatewayAuthToken {
    public static final String HEADER = "x-agy-gateway-token";
    public static final String ENV = "AGY_GATEWAY_TOKEN";
    private static volatile String cached;

    private GatewayAuthToken() {}

    public static synchronized String getOrCreate(File filesDir) {
        if (cached != null) return cached;
        try {
            File path = new File(filesDir, "gateway_auth.token");
            if (!path.getCanonicalFile().equals(new File(filesDir.getCanonicalFile(), path.getName()))) {
                throw new SecurityException("Gateway token file is a symbolic link");
            }
            if (!path.exists()) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                char[] digits = "0123456789abcdef".toCharArray();
                StringBuilder hex = new StringBuilder(64);
                for (byte b : random) {
                    int value = b & 255;
                    hex.append(digits[value >>> 4]).append(digits[value & 15]);
                }
                AtomicFileWriter.writeText(path, hex.toString());
            }
            // Only the Android application UID can read this capability.
            path.setReadable(false, false);
            path.setReadable(true, true);
            path.setWritable(false, false);
            path.setWritable(true, true);
            byte[] bytes = new byte[65];
            int n;
            try (FileInputStream in = new FileInputStream(path)) {
                n = in.read(bytes);
                if (n != 64 || in.read() != -1)
                    throw new SecurityException("Invalid gateway capability length");
            }
            String value = new String(bytes, 0, n, StandardCharsets.US_ASCII);
            if (!value.matches("[0-9a-f]{64}")) {
                throw new SecurityException("Invalid stored gateway capability");
            }
            cached = value;
            return value;
        } catch (Exception err) {
            throw new IllegalStateException("Private gateway capability unavailable", err);
        }
    }

    public static boolean authorized(File filesDir, String candidate) {
        if (candidate == null || candidate.length() != 64) return false;
        try {
            return MessageDigest.isEqual(
                    getOrCreate(filesDir).getBytes(StandardCharsets.US_ASCII),
                    candidate.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException cannotRead) {
            return false;
        }
    }
}
