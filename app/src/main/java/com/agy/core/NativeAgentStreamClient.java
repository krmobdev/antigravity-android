package com.agy.core;

import android.content.Context;
import android.util.Log;

import com.agy.bridge.AgentNotifications;
import com.agy.bridge.AgyDiagnostics;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Read-only Core ConnectRPC subscription owned by a foreground Android service.
 *
 * Unlike the UI subscriber this continues receiving status while the WebView
 * renderer is suspended. The certificate is PINNED to the observed 2.3.0
 * bundled language_server. Never fall back to a trust-all TLS connection.
 */
public final class NativeAgentStreamClient {
    private static final String TAG = "AGYNativeStream";
    private static final String PINNED_CERT_SHA256 =
            "f8bbabd57a2dff32992f205ede841f9ba8d95cdf45982e1d0e73aa5834e9cf94";
    private static final String METHOD =
            "/exa.language_server_pb.LanguageServerService/StreamAgentStateUpdates";

    public interface Listener {
        /** Return false to stop the subscription. */
        boolean onState(int status, boolean fullyIdle);
    }

    private final Context context;
    private volatile HttpsURLConnection connection;

    public NativeAgentStreamClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public void cancel() {
        HttpsURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    private static SSLContext pinnedLocalTls() throws Exception {
        X509TrustManager pin = new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                throw new CertificateException("Client certificates not supported");
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                if (chain == null || chain.length == 0 || chain[0] == null) {
                    throw new CertificateException("Missing local Core certificate");
                }
                chain[0].checkValidity();
                try {
                    byte[] digest = MessageDigest.getInstance("SHA-256")
                            .digest(chain[0].getEncoded());
                    StringBuilder h = new StringBuilder(64);
                    for (byte b : digest) h.append(String.format(java.util.Locale.ROOT,
                            "%02x", b & 255));
                    if (!PINNED_CERT_SHA256.equals(h.toString())) {
                        throw new CertificateException("Unexpected local Core certificate");
                    }
                } catch (CertificateException exc) {
                    throw exc;
                } catch (Exception exc) {
                    throw new CertificateException("Cannot verify local Core certificate", exc);
                }
            }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[] {pin}, null);
        return ssl;
    }

    public void subscribe(String conversationId, Listener listener) throws Exception {
        if (!conversationId.matches("[A-Za-z0-9_+:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid conversation ID");
        }
        if (CoreRuntimeState.serverUrl == null) {
            throw new java.io.IOException("Core not ready");
        }
        int port = CoreRuntimeState.httpsPort;
        String token = CoreRuntimeState.csrfToken;
        if (port < 1 || port > 65535 || token == null || token.isEmpty()) {
            throw new java.io.IOException("Core port or CSRF token unavailable");
        }
        URL url = new URL("https", "127.0.0.1", port, METHOD);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(pinnedLocalTls().getSocketFactory());
        // Exact loopback host + pinned certificate. No global TLS bypass.
        conn.setHostnameVerifier((host, session) -> "127.0.0.1".equals(host));
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(30000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/connect+json");
        conn.setRequestProperty("Accept", "application/connect+json");
        conn.setRequestProperty("Connect-Protocol-Version", "1");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("x-codeium-csrf-token", token);
        conn.setDoOutput(true);
        // Connect server-streaming requests MUST carry a length-prefixed frame.
        // Earlier v4 mistakenly wrote bare JSON and the server rejected every subscription.
        connection = conn;
        try {
            JSONObject request = new JSONObject();
            request.put("conversationId", conversationId);
            request.put("subscriberId", UUID.randomUUID().toString());
            // Preserve the initial snapshot so an already completed task can
            // report its true state after reconnect.
            request.put("disableRehydration", false);
            byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = conn.getOutputStream()) {
                ConnectFrameReader.writeRequest(out, data);
            }
            int http = conn.getResponseCode();
            if (http != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("HTTP_" + http);
            }
            String type = conn.getContentType();
            if (type == null || !type.startsWith("application/connect+json")) {
                throw new java.io.IOException("MIME_MISMATCH");
            }
            try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
                ConnectFrameReader.Frame frame;
                while ((frame = ConnectFrameReader.next(in)) != null) {
                    if (frame.isEndStream()) {
                        JSONObject end = new JSONObject(
                                new String(frame.payload, StandardCharsets.UTF_8));
                        if (end.has("error")) {
                            JSONObject error = end.optJSONObject("error");
                            String code = error == null ? "unknown" : error.optString("code", "unknown");
                            // Log only a protocol error code, never server diagnostics or tokens.
                            if (!code.matches("[a-z_]{1,32}")) code = "unknown";
                            throw new java.io.IOException("CONNECT_" + code);
                        }
                        break;
                    }
                    JSONObject payload = new JSONObject(
                            new String(frame.payload, StandardCharsets.UTF_8));
                    JSONObject update = payload.optJSONObject("update");
                    if (update == null) continue;
                    if (!conversationId.equals(update.optString("conversationId", ""))) {
                        // Do not permit cross-conversation status injection.
                        continue;
                    }
                    int status = parseStatus(update.opt("status"));
                    if (status != 1 && status != 2 && status != 4) continue;
                    boolean fullyIdle = update.optBoolean("fullyIdle", false);
                    if (!listener.onState(status, fullyIdle)) break;
                }
            }
        } finally {
            connection = null;
            conn.disconnect();
        }
    }

    /** Protobuf JSON may serialize enum status as an integer or enum name. */
    static int parseStatus(Object raw) {
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (!(raw instanceof String)) return 0;
        String v = (String)raw;
        if ("1".equals(v) || v.endsWith("_IDLE")) return 1;
        if ("2".equals(v) || v.endsWith("_RUNNING")) return 2;
        if ("4".equals(v) || v.endsWith("_BUSY")) return 4;
        return 0;
    }
}
