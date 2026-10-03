package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class CourseIdeActivity extends AppCompatActivity {
    public static final String EXTRA_TRACK_ID = "extra_track_id";
    private static final String WEB_BASE = "https://nelsen-savannah.co.ke";

    private WebView webView;
    private ProgressBar progress;
    private TextView error;
    private MaterialButton retry;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String ideUrl;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_ide);

        root = findViewById(R.id.ideRoot);
        webView = findViewById(R.id.ideWebView);
        progress = findViewById(R.id.ideProgress);
        error = findViewById(R.id.ideError);
        retry = findViewById(R.id.ideRetry);

        String trackId = getIntent().getStringExtra(EXTRA_TRACK_ID);
        if (TextUtils.isEmpty(trackId)) {
            showError("Course IDE unavailable");
            return;
        }

        ideUrl = WEB_BASE + "/learning/" + Uri.encode(trackId) + "?ide=1";

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                error.setVisibility(View.GONE);
                retry.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError webError
            ) {
                if (request.isForMainFrame()) {
                    showError("Could not load the course IDE");
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(
                        customView,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                );
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        retry.setOnClickListener(v -> loadIde());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        loadIde();
    }

    private void loadIde() {
        if (TextUtils.isEmpty(ideUrl)) return;
        progress.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        webView.loadUrl(ideUrl);
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        error.setText(message);
        error.setVisibility(View.VISIBLE);
        retry.setVisibility(TextUtils.isEmpty(ideUrl) ? View.GONE : View.VISIBLE);
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        hideCustomView();
        webView.stopLoading();
        webView.setWebChromeClient(null);
        webView.setWebViewClient(null);
        webView.destroy();
        super.onDestroy();
    }
}
