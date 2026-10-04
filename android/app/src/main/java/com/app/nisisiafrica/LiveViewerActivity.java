package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class LiveViewerActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "live_title";
    public static final String EXTRA_YOUTUBE_URL = "live_youtube_url";
    public static final String EXTRA_LIVE_STATUS = "live_status";

    private String youtubeUrl = "";
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_live_viewer);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        youtubeUrl = value(getIntent().getStringExtra(EXTRA_YOUTUBE_URL));
        String status = value(getIntent().getStringExtra(EXTRA_LIVE_STATUS)).toLowerCase(Locale.US);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(TextUtils.isEmpty(title) ? "Nelsen Live" : title);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView statusView = findViewById(R.id.tvLiveStatus);
        statusView.setText(statusLabel(status));

        MaterialButton openYoutube = findViewById(R.id.btnOpenYoutube);
        openYoutube.setOnClickListener(v -> openYoutube());

        webView = findViewById(R.id.liveWebView);
        String videoId = youtubeVideoId(youtubeUrl);
        if (TextUtils.isEmpty(videoId)) {
            webView.setVisibility(View.GONE);
            findViewById(R.id.liveUnavailable).setVisibility(View.VISIBLE);
            return;
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri != null ? uri.getHost() : null;
                if (host != null && (host.endsWith("youtube.com") || host.equals("youtu.be"))) {
                    return false;
                }
                return true;
            }
        });

        String embedUrl = "https://www.youtube.com/embed/" + videoId
                + "?autoplay=" + ("live".equals(status) ? "1" : "0")
                + "&playsinline=1&rel=0&modestbranding=1";

        String html = "<!doctype html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"
                + "<style>html,body{margin:0;background:#000;height:100%;overflow:hidden}"
                + "iframe{border:0;width:100%;height:100%}</style></head><body>"
                + "<iframe src='" + embedUrl + "' allow='autoplay; encrypted-media; picture-in-picture; fullscreen'"
                + " allowfullscreen></iframe></body></html>";
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
    }

    private void openYoutube() {
        if (TextUtils.isEmpty(youtubeUrl)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Could not open YouTube", Toast.LENGTH_SHORT).show();
        }
    }

    private static String statusLabel(String status) {
        if ("live".equals(status)) return "LIVE NOW";
        if ("ended".equals(status)) return "REPLAY";
        return "SCHEDULED LIVE";
    }

    public static String youtubeVideoId(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        try {
            Uri uri = Uri.parse(raw.trim());
            String host = value(uri.getHost()).toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            if ("youtu.be".equals(host)) {
                String segment = uri.getLastPathSegment();
                return value(segment);
            }
            if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                String watch = uri.getQueryParameter("v");
                if (!TextUtils.isEmpty(watch)) return watch;
                java.util.List<String> parts = uri.getPathSegments();
                if (parts.size() >= 2) {
                    String first = parts.get(0);
                    if ("embed".equals(first) || "live".equals(first) || "shorts".equals(first)) {
                        return value(parts.get(1));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String value(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
