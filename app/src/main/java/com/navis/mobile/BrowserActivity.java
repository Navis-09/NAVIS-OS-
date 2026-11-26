package com.navis.mobile;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

public class BrowserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    private static final String DEFAULT_URL = "https://www.google.com";

    private WebView browserView;
    private EditText urlInput;
    private ProgressBar progressBar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#030814"));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#091426"));
        int pad = dp(8);
        topBar.setPadding(pad, pad, pad, pad);

        Button backBtn = makeNavBtn("←");
        backBtn.setOnClickListener(v -> {
            if (browserView.canGoBack()) {
                browserView.goBack();
            } else {
                finish();
            }
        });

        Button fwdBtn = makeNavBtn("→");
        fwdBtn.setOnClickListener(v -> {
            if (browserView.canGoForward()) {
                browserView.goForward();
            }
        });

        Button reloadBtn = makeNavBtn("↻");
        reloadBtn.setOnClickListener(v -> browserView.reload());

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setTextColor(Color.parseColor("#E8F1FA"));
        urlInput.setHintTextColor(Color.parseColor("#7D93AC"));
        urlInput.setHint("Search or enter URL");
        urlInput.setTextSize(14f);
        urlInput.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Color.parseColor("#040B17"));
        boxBg.setCornerRadius(dp(10));
        boxBg.setStroke(dp(1), Color.parseColor("#1E3654"));
        urlInput.setBackground(boxBg);

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inputLp.setMargins(dp(6), 0, dp(6), 0);
        urlInput.setLayoutParams(inputLp);

        Button goBtn = new Button(this);
        goBtn.setText("Go");
        goBtn.setAllCaps(false);
        goBtn.setTextColor(Color.parseColor("#04201F"));
        goBtn.setTextSize(13f);
        goBtn.setMinimumWidth(0);
        goBtn.setMinimumHeight(0);
        goBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable goBg = new GradientDrawable();
        goBg.setColor(Color.parseColor("#3FD0C9"));
        goBg.setCornerRadius(dp(10));
        goBtn.setBackground(goBg);

        Runnable navigateFromBar = () -> loadTarget(urlInput.getText().toString());
        goBtn.setOnClickListener(v -> navigateFromBar.run());
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateFromBar.run();
                return true;
            }
            return false;
        });

        Button closeBtn = makeNavBtn("✕");
        closeBtn.setOnClickListener(v -> finish());

        topBar.addView(backBtn);
        topBar.addView(fwdBtn);
        topBar.addView(reloadBtn);
        topBar.addView(urlInput);
        topBar.addView(goBtn);
        topBar.addView(closeBtn);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3));
        progressBar.setLayoutParams(progLp);

        browserView = new WebView(this);
        browserView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        WebSettings s = browserView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        browserView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl() != null ? request.getUrl().toString() : "";
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    urlInput.setText(url);
                    return false;
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (url != null) {
                    urlInput.setText(url);
                }
            }
        });

        browserView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        root.addView(topBar);
        root.addView(progressBar);
        root.addView(browserView);
        setContentView(root);

        String initial = getIntent().getStringExtra(EXTRA_URL);
        loadTarget(!TextUtils.isEmpty(initial) ? initial : DEFAULT_URL);
    }

    private void loadTarget(String raw) {
        String trimmed = raw != null ? raw.trim() : "";
        if (TextUtils.isEmpty(trimmed)) {
            trimmed = DEFAULT_URL;
        }
        String target;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            target = trimmed;
        } else if (!trimmed.contains(" ") && trimmed.contains(".")) {
            target = "https://" + trimmed;
        } else {
            target = "https://www.google.com/search?q=" + android.net.Uri.encode(trimmed);
        }
        urlInput.setText(target);
        browserView.loadUrl(target);
    }

    private Button makeNavBtn(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor("#E8F1FA"));
        b.setTextSize(14f);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        if (browserView != null && browserView.canGoBack()) {
            browserView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
