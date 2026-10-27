package com.agy.ui.splash;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.agy.R;
import com.agy.core.CoreStartupProgress;

/** Lightweight, accessible startup card. Only the framework progress bar animates. */
public final class NativeStartupView extends ScrollView {
    private final LinearLayout content;
    private final TextView title, status, step;
    private final ImageView logo;
    private final ProgressBar progress;
    private final Button retry, copy, save;
    private final LinearLayout actions;
    private int foreground = Color.WHITE, background = 0xff101010;

    public NativeStartupView(Context context) {
        super(context);
        setTag("startup-splash");
        setFillViewport(true);
        setClipToPadding(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(24), dp(28), dp(24));
        addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        logo = new ImageView(context);
        logo.setImageResource(R.drawable.splash_logo);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));
        title = text(context, 24, "Antigravity");
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        add(title, 18);
        status = text(context, 16, "");
        status.setTag("startup-status");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(status, 24);
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setTag("startup-progress");
        progress.setIndeterminate(true);
        progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(dp(240), dp(4));
        line.topMargin = dp(18);
        content.addView(progress, line);
        step = text(context, 13, "");
        step.setTag("startup-step");
        add(step, 10);

        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams controls = new LinearLayout.LayoutParams(dp(240), LayoutParams.WRAP_CONTENT);
        controls.topMargin = dp(24);
        content.addView(actions, controls);
        retry = button(context, "Retry", "startup-retry");
        copy = button(context, "Copy log", "startup-copy");
        save = button(context, "Save log", "startup-save");
        actions.addView(retry, buttonParams());
        actions.addView(copy, buttonParams());
        actions.addView(save, buttonParams());
        actions.setVisibility(GONE);
        applyPalette(background, foreground);
        showStage(CoreStartupProgress.PREPARING);
    }

    private TextView text(Context context, int size, String value) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER);
        text.setFontFeatureSettings("kern");
        return text;
    }
    private void add(TextView text, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        content.addView(text, params);
    }
    private Button button(Context context, String value, String tag) {
        Button button = new Button(context);
        button.setText(value); button.setTag(tag); button.setAllCaps(false);
        button.setTextSize(14); button.setMinHeight(dp(48));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        return button;
    }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8); return params;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    public void bind(Runnable onRetry, Runnable onCopy, Runnable onSave) {
        retry.setEnabled(onRetry != null);
        retry.setOnClickListener(v -> { retry.setEnabled(false); if (onRetry != null) onRetry.run(); });
        copy.setOnClickListener(v -> { if (onCopy != null) onCopy.run(); });
        save.setOnClickListener(v -> { if (onSave != null) onSave.run(); });
    }

    public void applyPalette(int bg, int fg) {
        background = bg; foreground = fg;
        setBackgroundColor(bg);
        title.setTextColor(fg); status.setTextColor(fg); step.setTextColor(fg);
        step.setAlpha(.6f);
        logo.setColorFilter(fg);
        progress.setIndeterminateTintList(ColorStateList.valueOf(fg));
        int neutral = android.graphics.Color.rgb(
                (Color.red(bg)*84+Color.red(fg)*16)/100,
                (Color.green(bg)*84+Color.green(fg)*16)/100,
                (Color.blue(bg)*84+Color.blue(fg)*16)/100);
        for (Button button : new Button[]{retry, copy, save}) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(neutral); shape.setCornerRadius(dp(24));
            button.setBackground(shape); button.setTextColor(fg);
        }
    }

    public void showStage(int stage) {
        String[] labels = {"Preparing environment", "Starting Core", "Connecting interface"};
        int safe = Math.max(0, Math.min(2, stage));
        status.setText(labels[safe]);
        step.setText("Stage " + (safe + 1) + " of 3");
        actions.setVisibility(GONE); progress.setVisibility(VISIBLE); step.setVisibility(VISIBLE);
    }
    public void showRestarting() {
        status.setText("Restarting Antigravity");
        step.setVisibility(GONE); actions.setVisibility(GONE); progress.setVisibility(VISIBLE);
    }
    public void showError(boolean uiTimeout, boolean retryable) {
        status.setText(uiTimeout ? "Could not open the interface. Try again."
                : "Could not finish startup. Try again.");
        progress.setVisibility(GONE); step.setVisibility(GONE);
        actions.setVisibility(VISIBLE);
        retry.setVisibility(retryable ? VISIBLE : GONE); retry.setEnabled(retryable);
    }

    @Override protected void onMeasure(int width, int height) {
        int available = Math.max(dp(48), View.MeasureSpec.getSize(width) - dp(56));
        int narrow = Math.min(dp(240), available);
        progress.getLayoutParams().width = narrow;
        actions.getLayoutParams().width = narrow;
        super.onMeasure(width, height);
    }
    public void release() {
        retry.setOnClickListener(null); copy.setOnClickListener(null); save.setOnClickListener(null);
        logo.setImageDrawable(null);
    }
}
