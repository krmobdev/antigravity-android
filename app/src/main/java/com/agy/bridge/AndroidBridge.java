package com.agy.bridge;

import android.content.Context;
import android.webkit.JavascriptInterface;
import com.agy.core.NativeAgentStateObserver;
import java.util.LinkedHashMap;
import java.util.Map;

import com.agy.runtime.RuntimeManager;
import com.agy.shell.ShizukuBridge;
import com.agy.theme.ThemeManager;
import com.agy.ui.MainActivity;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Minimal, low-privilege JavaScript bridge for the app UI. It is visible to
 * embedded frames, so shell, notification, and arbitrary URL capabilities must
 * not be exposed here. Native-only getters below are intentionally unannotated.
 */
public final class AndroidBridge {

    private final Context diagnosticContext;
    private final AgentNotifications agentNotifications;
    private final Map<String, String> lastAgentStates = new LinkedHashMap<>();
    private final UiBridge ui = new UiBridge();
    private final ThemeBridge themeBridge;
    private final EngineBridge engine;
    private final EngineConfigJson config;
    private final String blobBridgeToken = createBlobBridgeToken();
    private final NativeVoiceRecorder nativeVoiceRecorder;
    private final NativePcmCapture nativePcmCapture=new NativePcmCapture();
    private volatile WeakReference<MainActivity> activityRef;

    public AndroidBridge(Context context, RuntimeManager runtime,
                         ShizukuBridge shizuku, ThemeManager theme) {
        Context appContext = context.getApplicationContext();
        this.diagnosticContext = appContext;
        this.nativeVoiceRecorder = new NativeVoiceRecorder(appContext);
        this.agentNotifications = new AgentNotifications(appContext);
        this.themeBridge = new ThemeBridge(theme);
        this.engine = new EngineBridge(runtime);
        this.config = new EngineConfigJson(appContext, runtime, engine, shizuku, themeBridge);
    }

    /** Host activity, used to dismiss the native splash once the UI has painted. */
    public void setActivity(MainActivity activity) {
        if (activity == null) cancelNativeVoiceOnBackground();
        this.activityRef = activity != null ? new WeakReference<MainActivity>(activity) : null;
        ui.setActivity(activity);
    }

    @JavascriptInterface
    public void onUiReady(String token) {
        if (matchesNativeBridgeToken(token)) {
            ui.onUiReady();
        }
    }

    @JavascriptInterface
    public void openDirectoryPicker() {
        final MainActivity act = activityRef != null ? activityRef.get() : MainActivity.activeActivity;
        if (act != null) {
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    act.launchDirectoryPicker();
                }
            });
        }
    }

    @JavascriptInterface
    public void onUiError(String token, final String errorMessage) {
        if (matchesNativeBridgeToken(token)) {
            ui.onUiError(errorMessage);
        }
    }

    @JavascriptInterface
    public boolean setTheme(String mode) {
        return themeBridge.setTheme(mode);
    }

    @JavascriptInterface
    public boolean setThemeByUser(final String mode) {
        return themeBridge.setThemeByUser(mode);
    }

    @JavascriptInterface
    public void onVisualThemeChanged(String visual) {
        themeBridge.onVisualThemeChanged(visual);
    }

    @JavascriptInterface
    public String getTheme() {
        return themeBridge.getTheme();
    }

    @JavascriptInterface
    public boolean isDesktopMode() {
        return ui.isDesktopMode();
    }

    @JavascriptInterface
    public void setDesktopMode(boolean enabled) {
        ui.setDesktopMode(enabled);
    }

    // Native-only helpers used while preparing the trusted app document.
    public boolean isEngineReady() {
        return engine.isReady();
    }

    public int getEnginePort() {
        return engine.getPort();
    }

    public String getEngineBaseUrl() {
        return engine.getBaseUrl();
    }

    public String getCsrfToken() {
        return engine.getCsrfToken();
    }

    public String getConfig() {
        return config.get();
    }

    /** Native-only token inserted into the trusted top-level document. */
    public String getBlobBridgeToken() {
        return blobBridgeToken;
    }

    /**
     * Recorder bridge is token-bound because addJavascriptInterface is visible
     * to embedded frames. Only the trusted top-level page receives the token.
     * Each recording is started by an explicit microphone button interaction.
     */
    /** Read-only check for QA; never starts recording or reveals private data. */
    @JavascriptInterface
    public String nativeVoiceReadiness(String token) {
        return matchesNativeBridgeToken(token)
                ? nativeVoiceRecorder.readiness(getActivity()) : "ERR_UNAUTHORIZED";
    }

    @JavascriptInterface
    public String startNativeVoice(String token) {
        if (!matchesNativeBridgeToken(token)) return "ERR_UNAUTHORIZED";
        return nativeVoiceRecorder.start(getActivity());
    }

    @JavascriptInterface
    public String stopNativeVoice(String token) {
        if (!matchesNativeBridgeToken(token)) return "{\"ok\":false,\"error\":\"ERR_UNAUTHORIZED\"}";
        return nativeVoiceRecorder.stop();
    }

    @JavascriptInterface
    public void cancelNativeVoice(String token) {
        if (matchesNativeBridgeToken(token)) nativeVoiceRecorder.cancel();
    }

    /**
     * Native PCM feeds the ORIGINAL Core streaming transcription RPC.
     * The bridge only provides raw 16k/16-bit/mono samples: no local STT.
     * Every call requires the trusted document token.
     */
    @JavascriptInterface
    public String nativePcmReadiness(String token) {
        return matchesNativeBridgeToken(token)
                ? nativePcmCapture.capability(getActivity()) : "ERR_UNAUTHORIZED";
    }

    @JavascriptInterface
    public String startNativePcm(String token) {
        return matchesNativeBridgeToken(token)
                ? nativePcmCapture.start(getActivity()) : "ERR_UNAUTHORIZED";
    }

    @JavascriptInterface
    public String readNativePcm(String token) {
        return matchesNativeBridgeToken(token)
                ? nativePcmCapture.poll() : "ERR_UNAUTHORIZED";
    }

    @JavascriptInterface
    public void stopNativePcm(String token) {
        if (matchesNativeBridgeToken(token)) nativePcmCapture.stop();
    }

    /** No JS token required because this is invoked by the native lifecycle. */
    public void cancelNativeVoiceOnBackground() {
        nativeVoiceRecorder.cancel();
        nativePcmCapture.stop();
    }

    /** Saves a downloaded blob only for scripts holding the top-level document token. */
    @JavascriptInterface
    public void saveBlob(String token, String base64Data, String filename, String mimeType) {
        if (!matchesNativeBridgeToken(token)) {
            return;
        }
        final MainActivity act = activityRef != null ? activityRef.get() : MainActivity.activeActivity;
        if (act != null) {
            com.agy.ui.download.FileDownloader.handleBlobData(
                    act, base64Data, filename, mimeType);
        }
    }

    /** Narrow host-only telemetry; unlike the legacy bridge this never executes shell commands. */
    @JavascriptInterface
    public void onAgentState(String token, String conversationId, int status, boolean fullyIdle, String stage) {
        if (!matchesNativeBridgeToken(token) || !validConversationId(conversationId)
                || (status != 1 && status != 2 && status != 4)) return;
        String current = status + "/" + fullyIdle;
        synchronized (lastAgentStates) {
            if (!current.equals(lastAgentStates.get(conversationId))) {
                lastAgentStates.put(conversationId, current);
                if (lastAgentStates.size() > 128)
                    lastAgentStates.remove(lastAgentStates.keySet().iterator().next());
                AgyDiagnostics.log(diagnosticContext, "agent_state",
                        "id=" + safeId(conversationId) + " status=" + status + " idle=" + fullyIdle);
            }
        }
        agentNotifications.observe(conversationId, status, fullyIdle,
                stage == null ? "" : stage.substring(0, Math.min(stage.length(), 160)));
        NativeAgentStateObserver.onUiState(conversationId, status, fullyIdle);
    }

    @JavascriptInterface
    public boolean wasAgentRecentlyFinished(String token, String id) {
        return matchesNativeBridgeToken(token) && validConversationId(id)
                && NativeAgentStateObserver.wasRecentlyFinished(diagnosticContext, id);
    }

    @JavascriptInterface
    public void onConversationSummary(String token, String id, String title) {
        if (matchesNativeBridgeToken(token) && validConversationId(id))
            agentNotifications.updateTitle(id, title);
    }

    @JavascriptInterface
    public void onVisibleConversation(String token, String id) {
        if (!matchesNativeBridgeToken(token) || (id != null && !validConversationId(id)))
            return;
        MainActivity host = getActivity();
        if (host != null) host.runOnUiThread(() -> host.setVisibleConversation(id));
    }

    @JavascriptInterface
    public void onNotificationNavigationResult(String token, String id) {
        if (!matchesNativeBridgeToken(token) || !validConversationId(id)) return;
        MainActivity host = getActivity();
        if (host != null) host.onNotificationNavigationResult(id);
    }

    @JavascriptInterface
    public void diagnosticEvent(String token, String kind, String details) {
        if (!matchesNativeBridgeToken(token) || kind == null
                || !kind.matches("[a-z_]{1,32}")) return;
        String safe = details == null ? "" : details.substring(0, Math.min(160, details.length()));
        AgyDiagnostics.log(diagnosticContext, kind, safe);
    }

    private MainActivity getActivity() {
        WeakReference<MainActivity> ref = activityRef;
        return ref == null ? null : ref.get();
    }

    private static boolean validConversationId(String id) {
        return id != null && id.matches("[A-Za-z0-9_+:-]{1,128}");
    }

    private static String safeId(String id) {
        return id.substring(Math.max(0, id.length() - 6))
                .replaceAll("[^A-Za-z0-9]", "_");
    }

    private boolean matchesNativeBridgeToken(String candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            byte[] expected = blobBridgeToken.getBytes(Charset.forName("UTF-8"));
            byte[] actual = candidate.getBytes(Charset.forName("UTF-8"));
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String createBlobBridgeToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        char[] hex = "0123456789abcdef".toCharArray();
        char[] encoded = new char[random.length * 2];
        for (int i = 0; i < random.length; i++) {
            int value = random[i] & 0xff;
            encoded[i * 2] = hex[value >>> 4];
            encoded[i * 2 + 1] = hex[value & 0x0f];
        }
        return new String(encoded);
    }
}
