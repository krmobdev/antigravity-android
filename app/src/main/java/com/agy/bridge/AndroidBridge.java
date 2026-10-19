package com.agy.bridge;

import android.content.Context;
import android.webkit.JavascriptInterface;
import java.util.LinkedHashMap;
import java.util.Map;

import com.agy.oauth.OAuthManager;
import com.agy.core.NativeAgentStateObserver;
import com.agy.runtime.RuntimeManager;
import com.agy.shell.ShizukuBridge;
import com.agy.theme.ThemeManager;
import com.agy.ui.MainActivity;

/**
 * The single JS bridge exposed to the WebView as the `Android` object
 * (aliased to `AndroidBridge` by the injected boot script). All methods are
 * fail-soft: they never throw into JS and return JSON / plain strings.
 */
public final class AndroidBridge {

    private final UiBridge ui = new UiBridge();
    private final ThemeBridge themeBridge;
    private final EngineBridge engine;
    private final EngineConfigJson config;
    private final ExternalUrlBridge external;
    private final ShellBridge shell;
    private final NotificationBridge notification;
    private final AgentNotifications agentNotifications;
    private final Context diagnosticContext;
    private final Map<String, String> lastAgentStates = new LinkedHashMap<>();

    public AndroidBridge(Context context, RuntimeManager runtime,
                         ShizukuBridge shizuku, OAuthManager oauth,
                         ThemeManager theme) {
        Context appContext = context.getApplicationContext();
        this.diagnosticContext = appContext;
        this.themeBridge = new ThemeBridge(theme);
        this.engine = new EngineBridge(runtime);
        this.config = new EngineConfigJson(appContext, runtime, engine, shizuku, themeBridge);
        this.external = new ExternalUrlBridge(appContext, oauth);
        this.shell = new ShellBridge(shizuku);
        this.notification = new NotificationBridge(appContext, shizuku);
        this.agentNotifications = new AgentNotifications(appContext);
    }

    /** Host activity, used to dismiss the native splash once the UI has painted. */
    public void setActivity(MainActivity activity) {
        ui.setActivity(activity);
    }

    /**
     * Called from web/patches/startup_ready.js after the UI's first paint.
     */
    @JavascriptInterface
    public void onUiReady() {
        ui.onUiReady();
    }

    @JavascriptInterface
    public void onUiError(final String errorMessage) {
        ui.onUiError(errorMessage);
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

    @JavascriptInterface
    public boolean isEngineReady() {
        return engine.isReady();
    }

    @JavascriptInterface
    public int getEnginePort() {
        return engine.getPort();
    }

    @JavascriptInterface
    public String getEngineBaseUrl() {
        return engine.getBaseUrl();
    }

    @JavascriptInterface
    public String getCsrfToken() {
        return engine.getCsrfToken();
    }

    @JavascriptInterface
    public String getConfig() {
        return config.get();
    }

    @JavascriptInterface
    public void openUrl(String url) {
        external.open(url);
    }

    @JavascriptInterface
    public String exec(String cmd) {
        return shell.exec(cmd);
    }

    @JavascriptInterface
    public String shizuku(String cmd) {
        return shell.exec(cmd);
    }

    @JavascriptInterface
    public boolean shizukuAvailable() {
        return shell.available();
    }

    @JavascriptInterface
    public boolean postNotification(String title, String message, String tag) {
        return notification.post(title, message, tag);
    }

    /** Receives decoded core state transitions; never reads chat content. */
    @JavascriptInterface
    public void onAgentState(String conversationId, int status, boolean fullyIdle, String stage) {
        // Diagnostic transitions only. Full stream can emit dozens of updates
        // per second and previously flooded the rotating log with duplicates.
        if (conversationId != null && conversationId.length() <= 128) {
            String current = status + "/" + fullyIdle;
            synchronized (lastAgentStates) {
                if (!current.equals(lastAgentStates.get(conversationId))) {
                    lastAgentStates.put(conversationId, current);
                    if (lastAgentStates.size() > 128) {
                        lastAgentStates.remove(lastAgentStates.keySet().iterator().next());
                    }
                    AgyDiagnostics.log(diagnosticContext, "agent_state",
                            "id=" + safeId(conversationId) + " status=" + status + " idle=" + fullyIdle);
                }
            }
        }
        agentNotifications.observe(conversationId, status, fullyIdle, stage);
        NativeAgentStateObserver.onUiState(conversationId, status, fullyIdle);
    }

    /** Native completion survives WebView destruction; no prompts or chat content exposed. */
    @JavascriptInterface
    public boolean wasAgentRecentlyFinished(String conversationId) {
        return NativeAgentStateObserver.wasRecentlyFinished(diagnosticContext, conversationId);
    }

    @JavascriptInterface
    public void diagnosticEvent(String kind, String details) {
        AgyDiagnostics.log(diagnosticContext, kind, details);
    }

    @JavascriptInterface
    public void onConversationSummary(String conversationId, String title) {
        agentNotifications.updateTitle(conversationId, title);
    }

    @JavascriptInterface
    public void onNotificationNavigationResult(String conversationId) {
        final MainActivity host = MainActivity.activeActivity;
        if (host != null) host.onNotificationNavigationResult(conversationId);
    }

    @JavascriptInterface
    public String listNotifications() {
        return notification.list();
    }

    private static String safeId(String id) {
        if (id == null) return "-";
        return id.substring(Math.max(0, id.length() - 6)).replaceAll("[^a-zA-Z0-9]", "_");
    }

    @JavascriptInterface
    public void saveBlob(String base64Data, String filename, String mimeType) {
        if (MainActivity.activeActivity != null) {
            com.agy.ui.download.FileDownloader.handleBlobData(
                    MainActivity.activeActivity, base64Data, filename, mimeType);
        }
    }
}
