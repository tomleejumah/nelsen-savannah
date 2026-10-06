package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
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
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.firebase.auth.FirebaseAuth;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.HashMap;
import java.util.Map;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class LiveViewerActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "live_title";
    public static final String EXTRA_EVENT_ID = "live_event_id";
    public static final String EXTRA_YOUTUBE_URL = "live_youtube_url";
    public static final String EXTRA_LIVE_STATUS = "live_status";
    public static final String EXTRA_LIVE_AVAILABILITY = "live_availability";

    private String youtubeUrl = "";
    private String eventId = "";
    private WebView webView;
    private TextView viewerCountView;
    private TextView chatView;
    private MaterialButton openYoutubeButton;
    private final Handler telemetryHandler = new Handler(Looper.getMainLooper());
    private boolean telemetryRunning = false;
    private final Runnable telemetryPoll = new Runnable() {
        @Override public void run() {
            if (!telemetryRunning) return;
            loadTelemetry();
            telemetryHandler.postDelayed(this, 5_000L);
        }
    };
    private final Handler attendanceHandler = new Handler(Looper.getMainLooper());
    private boolean attendanceRunning = false;
    private final Runnable attendanceHeartbeat = new Runnable() {
        @Override public void run() {
            if (!attendanceRunning) return;
            recordAttendance(15);
            attendanceHandler.postDelayed(this, 15_000L);
        }
    };

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
        eventId = value(getIntent().getStringExtra(EXTRA_EVENT_ID));
        String status = value(getIntent().getStringExtra(EXTRA_LIVE_STATUS)).toLowerCase(Locale.US);
        String availability = value(getIntent().getStringExtra(EXTRA_LIVE_AVAILABILITY)).toLowerCase(Locale.US);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(TextUtils.isEmpty(title) ? "Nelsen Live" : title);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView statusView = findViewById(R.id.tvLiveStatus);
        statusView.setText(statusLabel(status));

        openYoutubeButton = findViewById(R.id.btnOpenYoutube);
        openYoutubeButton.setOnClickListener(v -> openLiveExternally());
        viewerCountView = findViewById(R.id.tvViewerCount);
        chatView = findViewById(R.id.tvLiveChat);
        TextView hostView = findViewById(R.id.tvLiveHost);
        TextView subtitleView = findViewById(R.id.tvLiveSubtitle);
        hostView.setText(TextUtils.isEmpty(title) ? "Nelsen Live" : title);
        subtitleView.setText("Live session");
        MaterialButton share = findViewById(R.id.btnShareLive);
        share.setOnClickListener(v -> shareLive(title));

        webView = findViewById(R.id.liveWebView);
        String videoId = youtubeVideoId(youtubeUrl);
        if (TextUtils.isEmpty(videoId) || "unavailable".equals(availability)) {
            webView.setVisibility(View.GONE);
            TextView unavailable = findViewById(R.id.liveUnavailable);
            unavailable.setText("This live or replay is unavailable. It may have ended or no longer be accessible.");
            unavailable.setVisibility(View.VISIBLE);
            openYoutubeButton.setVisibility(TextUtils.isEmpty(videoId) ? View.GONE : View.VISIBLE);
            return;
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUserAgentString(webView.getSettings().getUserAgentString() + " NelsenSavannah/Android");
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
                + "&playsinline=1&rel=0&modestbranding=1"
                + "&origin=https%3A%2F%2Fnelsen-savannah.co.ke";

        String html = "<!doctype html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"
                + "<style>html,body{margin:0;background:#000;height:100%;overflow:hidden}"
                + "iframe{border:0;width:100%;height:100%}</style></head><body>"
                + "<iframe src='" + embedUrl + "' allow='autoplay; encrypted-media; picture-in-picture; fullscreen'"
                + " allowfullscreen></iframe></body></html>";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://nelsen-savannah.co.ke/");
        webView.loadDataWithBaseURL("https://nelsen-savannah.co.ke", html, "text/html", "UTF-8", null);
    }

    private void loadTelemetry() {
        if (TextUtils.isEmpty(eventId)) return;
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(token ->
                ApiClient.getLmsService().liveTelemetry("Bearer " + token.getToken(), eventId)
                        .enqueue(new Callback<LmsModels.LiveTelemetryEnvelope>() {
                            @Override public void onResponse(Call<LmsModels.LiveTelemetryEnvelope> call, Response<LmsModels.LiveTelemetryEnvelope> response) {
                                if (!response.isSuccessful() || response.body() == null || response.body().data == null) return;
                                LmsModels.LiveTelemetryData data = response.body().data;
                                if (viewerCountView != null && data.concurrentViewers != null) {
                                    viewerCountView.setText(data.concurrentViewers + (data.concurrentViewers == 1 ? " watching" : " watching"));
                                    viewerCountView.setVisibility(View.VISIBLE);
                                }
                                if (chatView != null && data.chatMessages != null && !data.chatMessages.isEmpty()) {
                                    StringBuilder lines = new StringBuilder();
                                    for (LmsModels.LiveChatMessage item : data.chatMessages) {
                                        if (lines.length() > 0) lines.append("\n\n");
                                        if (!TextUtils.isEmpty(item.authorName)) lines.append(item.authorName).append(": ");
                                        lines.append(value(item.message));
                                    }
                                    chatView.setText(lines.toString());
                                }
                            }
                            @Override public void onFailure(Call<LmsModels.LiveTelemetryEnvelope> call, Throwable t) {}
                        }));
    }

    private void recordAttendance(int watchedSeconds) {
        if (TextUtils.isEmpty(eventId)) return;
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(token -> {
            Map<String, Integer> body = new HashMap<>();
            body.put("watchedSeconds", watchedSeconds);
            ApiClient.getLmsService()
                    .recordLiveAttendance("Bearer " + token.getToken(), eventId, body)
                    .enqueue(new Callback<LmsModels.MapEnvelope>() {
                        @Override public void onResponse(Call<LmsModels.MapEnvelope> call, Response<LmsModels.MapEnvelope> response) {}
                        @Override public void onFailure(Call<LmsModels.MapEnvelope> call, Throwable t) {}
                    });
        });
    }

    private void shareLive(String title) {
        if (TextUtils.isEmpty(eventId)) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,
                (TextUtils.isEmpty(title) ? "Nelsen Live" : title) + "\nhttps://nelsen-savannah.co.ke/live/" + eventId);
        startActivity(Intent.createChooser(send, "Share live session"));
    }

    private void openLiveExternally() {
        if (TextUtils.isEmpty(youtubeUrl)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Could not open live video", Toast.LENGTH_SHORT).show();
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
    protected void onResume() {
        super.onResume();
        if (!telemetryRunning) {
            telemetryRunning = true;
            loadTelemetry();
            telemetryHandler.postDelayed(telemetryPoll, 5_000L);
        }
        if (!attendanceRunning) {
            attendanceRunning = true;
            recordAttendance(0);
            attendanceHandler.postDelayed(attendanceHeartbeat, 15_000L);
        }
    }

    @Override
    protected void onPause() {
        telemetryRunning = false;
        telemetryHandler.removeCallbacks(telemetryPoll);
        attendanceRunning = false;
        attendanceHandler.removeCallbacks(attendanceHeartbeat);
        super.onPause();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && webView != null && webView.getVisibility() == View.VISIBLE) {
            try {
                enterPictureInPictureMode(
                        new PictureInPictureParams.Builder()
                                .setAspectRatio(new Rational(16, 9))
                                .build()
                );
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        attendanceRunning = false;
        attendanceHandler.removeCallbacks(attendanceHeartbeat);
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
