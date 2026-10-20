package com.agy.gateway;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pure-JVM regression for private token and authenticated localhost requests. */
public final class GatewayAuthTokenTest {
    private static int assertions;
    private static void check(boolean ok, String why) {
        assertions++;
        if (!ok) throw new AssertionError(why);
    }
    private static void resetCache() throws Exception {
        Field cached = GatewayAuthToken.class.getDeclaredField("cached");
        cached.setAccessible(true);
        cached.set(null, null);
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("agy-gateway-auth-test");
        try {
            File root = dir.toFile();
            check(!GatewayAuthToken.authorized(root, null), "missing header denied");
            check(!GatewayAuthToken.authorized(root, ""), "empty header denied");
            String token = GatewayAuthToken.getOrCreate(root);
            check(token.matches("[0-9a-f]{64}"), "32-byte random capability encoded as 64 hex");
            check(GatewayAuthToken.authorized(root, token), "correct token accepted");
            check(!GatewayAuthToken.authorized(root, token.substring(1)), "truncated token denied");
            check(!GatewayAuthToken.authorized(root, "0".repeat(64).equals(token)
                    ? "1".repeat(64) : "0".repeat(64)), "wrong capability denied");
            File stored = new File(root, "gateway_auth.token");
            check(stored.isFile(), "token stored only in application private files root");
            check(new String(Files.readAllBytes(stored.toPath()), StandardCharsets.UTF_8)
                    .equals(token), "persisted token identical");
            resetCache();
            check(GatewayAuthToken.getOrCreate(root).equals(token), "same token after process restart");
            Path link = dir.resolve("bad");
            Files.createDirectory(link);
            Path other = dir.resolve("other");
            Files.createDirectory(other);
            Files.createSymbolicLink(link.resolve("gateway_auth.token"), stored.toPath());
            resetCache();
            boolean rejected = false;
            try { GatewayAuthToken.getOrCreate(link.toFile()); }
            catch (IllegalStateException expected) { rejected = true; }
            check(rejected, "symbolic link refused");
            resetCache();
            System.out.println("PASS GatewayAuthToken " + assertions + " assertions");
        } finally {
            resetCache();
            Files.deleteIfExists(dir.resolve("bad").resolve("gateway_auth.token"));
            Files.deleteIfExists(dir.resolve("bad"));
            Files.deleteIfExists(dir.resolve("other"));
            Files.deleteIfExists(dir.resolve("gateway_auth.token"));
            Files.deleteIfExists(dir);
        }
    }
}
