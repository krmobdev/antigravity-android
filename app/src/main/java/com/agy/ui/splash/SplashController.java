package com.agy.ui.splash;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;

import com.agy.core.CoreRuntimeState;
import com.agy.core.CoreStartupProgress;
import com.agy.theme.ThemeManager;

/** Owns a native startup surface until an authenticated UI-ready callback. */
public final class SplashController {
    public static final int MODE_LOADING = 0, MODE_TRANSIENT = 1, MODE_ERROR = 2;
    private final Context context;
    private final NativeStartupView view;
    private final ThemeManager theme;
    private final FrameLayout root;
    private SplashRetryListener retryListener;
    private boolean dismissed, fatalShown, uiTimeout;
    private int mode = MODE_LOADING, stage = CoreStartupProgress.PREPARING;
    private long lastAttempt = -1, minimumAttempt;

    public SplashController(Context context, ThemeManager theme, FrameLayout root) {
        this.context = context; this.theme = theme; this.root = root;
        view = new NativeStartupView(context);
        palette();
    }
    public NativeStartupView view() { return view; }
    public int mode() { return mode; }
    public boolean isFatalShown() { return fatalShown; }
    public boolean isDismissed() { return dismissed; }
    public void setRetryListener(SplashRetryListener listener) {
        retryListener = listener;
        view.bind(listener == null ? null : listener::onRetry,
                () -> LogExporter.copyToClipboard(context),
                () -> LogExporter.saveAndShare(context));
    }
    public void loadInitial() {
        mode = MODE_LOADING; stage = CoreStartupProgress.PREPARING;
        setVisible(); render();
    }
    /** A user retry awaits a fresh supervisor attempt, ignoring its old snapshot. */
    public void showLoading() {
        minimumAttempt = CoreRuntimeState.startup.snapshot().attempt + 1;
        mode = MODE_LOADING; stage = CoreStartupProgress.PREPARING;
        setVisible(); render();
    }
    public void showProgress(CoreStartupProgress.Snapshot snapshot) {
        if (dismissed || fatalShown || snapshot.attempt < minimumAttempt
                || snapshot.attempt < lastAttempt) return;
        if (snapshot.attempt == lastAttempt && snapshot.stage == stage && mode == MODE_LOADING) return;
        if (snapshot.attempt == lastAttempt && snapshot.stage < stage) return;
        lastAttempt = snapshot.attempt; stage = snapshot.stage; mode = MODE_LOADING;
        render();
    }
    /** Core answered its ready probe; now wait for the Web UI to really mount. */
    public void showConnecting() {
        minimumAttempt = 0; mode = MODE_LOADING; stage = CoreStartupProgress.CONNECTING;
        setVisible(); render();
    }
    public void showTransient(String ignoredDiagnostic) {
        if (fatalShown) return;
        setVisible();
        if (mode == MODE_TRANSIENT) return;
        mode = MODE_TRANSIENT; render();
    }
    public void showFatalError(String diagnostic) {
        if (fatalShown) return;
        fatalShown = true; uiTimeout = "ui_ready_timeout".equals(diagnostic);
        mode = MODE_ERROR; setVisible(); render();
    }
    public void clearFatal() { fatalShown = false; }
    public void setVisible() {
        dismissed = false; view.animate().cancel(); view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
        if (root != null) root.bringChildToFront(view);
    }
    public void dismiss() {
        if (dismissed || fatalShown) return;
        dismissed = true; view.animate().cancel(); view.setVisibility(View.GONE);
    }
    public void refresh() { palette(); if (!dismissed) render(); }
    public void installExitAnimation(Activity activity) { SplashExitInstaller.install(activity, view); }
    public void destroy() { view.animate().cancel(); view.setVisibility(View.GONE); view.release(); retryListener = null; }
    private void palette() {
        view.applyPalette(theme.getBackgroundColor(), Color.parseColor(theme.getForegroundHex()));
    }
    private void render() {
        if (mode == MODE_ERROR) view.showError(uiTimeout, retryListener != null);
        else if (mode == MODE_TRANSIENT) view.showRestarting();
        else view.showStage(stage);
    }
}
