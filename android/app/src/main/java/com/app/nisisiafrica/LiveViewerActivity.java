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
import android.widget.ScrollView;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.app.nisisiafrica.Utils.LiveChatTimeline;
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
    private TextView liveStatusView;
    private String currentLiveStatus = "";
    private boolean streamPlayable = false;
    private TextView chatView;
    private ScrollView chatScroll;
    private String lastChatSnapshot = "";
    private final LiveChatTimeline chatTimeline = new LiveChatTimeline();
    private MaterialButton loadOlderChatButton;
    private boolean olderChatLoading = false;
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
    private boolean attendanceEligible = false;
    private boolean attendanceLeaveSent = false;
    private long lastWatchTickMs = 0L;
    private boolean chatSending = false;
    private MaterialButton sendChatButton;
     private final Runnable attendanceHeartbeat = new Runnable() {
        @Override public void run() {
            if (!attendanceRunning) return;
            long now = android.os.SystemClock.elapsedRealtime();
            int elapsed = lastWatchTickMs <= 0L ? 0 : (int) Math.min(15, Math.max(0, (now - lastWatchTickMs) / 1000L));
            lastWatchTickMs = now;
            recordAttendance(elapsed, "heartbeat");
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

        liveStatusView = findViewById(R.id.tvLiveStatus);
        currentLiveStatus = status;
        liveStatusView.setText(statusLabel(status));

        openYoutubeButton = findViewById(R.id.btnOpenYoutube);
        openYoutubeButton.setOnClickListener(v -> openLiveExternally());
        viewerCountView = findViewById(R.id.tvViewerCount);
        chatView = findViewById(R.id.tvLiveChat);
        chatScroll = findViewById(R.id.liveChatScroll);
        loadOlderChatButton = findViewById(R.id.btnLiveChatEarlier);
        loadOlderChatButton.setOnClickListener(v -> loadOlderChat());
        TextInputEditText chatInput = findViewById(R.id.inputLiveChat);
        sendChatButton = findViewById(R.id.btnSendLiveChat);
        sendChatButton.setOnClickListener(v -> sendLiveChat(chatInput));
        sendChatButton.setEnabled("live".equals(status));
        TextView hostView = findViewById(R.id.tvLiveHost);
        TextView subtitleView = findViewById(R.id.tvLiveSubtitle);
        hostView.setText(TextUtils.isEmpty(title) ? "Nelsen Live" : title);
        subtitleView.setText("Live session");
        MaterialButton share = findViewById(R.id.btnShareLive);
        share.setOnClickListener(v -> shareLive(title));

        webView = findViewById(R.id.liveWebView);
        String videoId = youtubeVideoId(youtubeUrl);
        // Count attendance only when the broadcast is actually live and a
        // playable stream is present. Upcoming/unavailable/replay views must
        // not create false live attendance or repeated API 409 responses.
        streamPlayable = !TextUtils.isEmpty(videoId) && !"unavailable".equals(availability);
        attendanceEligible = "live".equals(status) && streamPlayable;
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
                ApiClient.getLmsService().liveState("Bearer " + token.getToken(), eventId)
                        .enqueue(new Callback<LmsModels.LiveStateEnvelope>() {
                            @Override public void onResponse(Call<LmsModels.LiveStateEnvelope> call, Response<LmsModels.LiveStateEnvelope> response) {
                                LmsModels.LiveStateData data = response.body() != null ? response.body().data : null;
                                if (!response.isSuccessful() || data == null) return;
                                if (isFinishing() || isDestroyed()) return;
                                refreshLiveLifecycle(data);
                                if (viewerCountView != null) {
                                    viewerCountView.setText(data.concurrentViewers + " watching");
                                    viewerCountView.setVisibility(View.VISIBLE);
                                }
                                if (!isFinishing() && !isDestroyed() && chatView != null) {
                                    if (chatTimeline.appendLatest(data)) renderChat(false);
                                    refreshOlderChatButton();
                                }
                            }
                            @Override public void onFailure(Call<LmsModels.LiveStateEnvelope> call, Throwable t) {}
                        }));
    }

    private void refreshLiveLifecycle(LmsModels.LiveStateData data) {
        if (data == null) return;
        String next = value(data.liveStatus).toLowerCase(Locale.US);
        if (!"live".equals(next) && !"scheduled".equals(next) && !"ended".equals(next)) return;
        if (!next.equals(currentLiveStatus)) {
            currentLiveStatus = next;
            if (liveStatusView != null) liveStatusView.setText(statusLabel(next));
        }
        boolean mayAttend = streamPlayable && "live".equals(next);
        if (sendChatButton != null && !chatSending) sendChatButton.setEnabled(mayAttend);

        if (!mayAttend && attendanceRunning) {
            attendanceRunning = false;
            attendanceHandler.removeCallbacks(attendanceHeartbeat);
            lastWatchTickMs = 0L;
            recordLeaveOnce();
        }
        attendanceEligible = mayAttend;

        // A viewer can open an upcoming broadcast before the provider starts.
        // Begin counting only when it is actually live and visible on screen.
        boolean visible = hasWindowFocus() ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode());
        if (mayAttend && visible && !attendanceRunning) {
            attendanceLeaveSent = false;
            attendanceRunning = true;
            lastWatchTickMs = android.os.SystemClock.elapsedRealtime();
            recordAttendance(0, "heartbeat");
            attendanceHandler.postDelayed(attendanceHeartbeat, 15_000L);
        }
    }

    private void refreshOlderChatButton() {
        if (loadOlderChatButton == null) return;
        loadOlderChatButton.setVisibility(chatTimeline.hasOlder() ? View.VISIBLE : View.GONE);
        loadOlderChatButton.setEnabled(!olderChatLoading);
        loadOlderChatButton.setText(olderChatLoading ? "Loading messages…" : "Load earlier messages");
    }

    private void renderChat(boolean historyPrepended) {
        if (chatView == null || chatScroll == null) return;
        String next = chatTimeline.displayText();
        if (next.equals(lastChatSnapshot)) return;
        View child = chatScroll.getChildAt(0);
        int previousHeight = child == null ? 0 : child.getHeight();
        int oldScrollY = chatScroll.getScrollY();
        int threshold = Math.round(48 * getResources().getDisplayMetrics().density);
        boolean followingLatest = child != null &&
                oldScrollY + chatScroll.getHeight() >= previousHeight - threshold;
        lastChatSnapshot = next;
        chatView.setText(next);
        chatScroll.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            View updated = chatScroll.getChildAt(0);
            if (updated == null) return;
            if (historyPrepended) {
                chatScroll.scrollTo(0, oldScrollY + Math.max(0, updated.getHeight() - previousHeight));
            } else if (followingLatest) {
                chatScroll.scrollTo(0, updated.getHeight());
            }
        });
    }

    private void loadOlderChat() {
        if (olderChatLoading || !chatTimeline.hasOlder() || TextUtils.isEmpty(eventId)) return;
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String cursor = chatTimeline.nextCursor();
        olderChatLoading = true;
        refreshOlderChatButton();
        user.getIdToken(false).addOnSuccessListener(token -> {
            if (isFinishing() || isDestroyed()) {
                olderChatLoading = false;
                return;
            }
            ApiClient.getLmsService().liveChatHistory(
                    "Bearer " + token.getToken(), eventId, cursor, 50)
                    .enqueue(new Callback<LmsModels.LiveChatHistoryEnvelope>() {
                        @Override public void onResponse(Call<LmsModels.LiveChatHistoryEnvelope> call,
                                                         Response<LmsModels.LiveChatHistoryEnvelope> response) {
                            if (isFinishing() || isDestroyed()) return;
                            olderChatLoading = false;
                            LmsModels.LiveChatHistoryData data =
                                    response.body() != null ? response.body().data : null;
                            if (!response.isSuccessful() || data == null) {
                                Toast.makeText(LiveViewerActivity.this,
                                        "Could not load earlier messages", Toast.LENGTH_SHORT).show();
                            } else {
                                if (chatTimeline.prependHistory(data)) renderChat(true);
                            }
                            refreshOlderChatButton();
                        }
                        @Override public void onFailure(Call<LmsModels.LiveChatHistoryEnvelope> call, Throwable t) {
                            if (isFinishing() || isDestroyed()) return;
                            olderChatLoading = false;
                            refreshOlderChatButton();
                            Toast.makeText(LiveViewerActivity.this,
                                    "Could not load earlier messages", Toast.LENGTH_SHORT).show();
                        }
                    });
        }).addOnFailureListener(error -> {
            olderChatLoading = false;
            if (!isFinishing() && !isDestroyed()) refreshOlderChatButton();
        });
    }

    private void sendLiveChat(TextInputEditText input) {
        String message = input == null || input.getText() == null ? "" : input.getText().toString().trim();
        if (chatSending || message.isEmpty() || eventId.isEmpty()) return;
        if (message.length() > 500) {
            input.setError("Maximum 500 characters");
            return;
        }
        chatSending = true;
        sendChatButton.setEnabled(false);
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finishChatSend();
            return;
        }
        user.getIdToken(false).addOnSuccessListener(token -> {
            Map<String, String> body = new HashMap<>();
            body.put("message", message);
            ApiClient.getLmsService().postLiveChat("Bearer " + token.getToken(), eventId, body)
                    .enqueue(new Callback<LmsModels.MapEnvelope>() {
                        @Override public void onResponse(Call<LmsModels.MapEnvelope> call, Response<LmsModels.MapEnvelope> response) {
                            finishChatSend();
                            if (response.isSuccessful()) {
                                if (input.getText() != null && message.contentEquals(input.getText().toString().trim())) input.setText("");
                                loadTelemetry();
                            } else {
                                Toast.makeText(LiveViewerActivity.this, "Message not sent (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onFailure(Call<LmsModels.MapEnvelope> call, Throwable t) {
                            finishChatSend();
                            Toast.makeText(LiveViewerActivity.this, "Could not send message", Toast.LENGTH_SHORT).show();
                        }
                    });
        }).addOnFailureListener(error -> finishChatSend());
    }

    private void finishChatSend() {
        chatSending = false;
        if (sendChatButton != null) sendChatButton.setEnabled(true);
    }

    private void recordLeaveOnce() {
        if (!attendanceEligible || attendanceLeaveSent) return;
        attendanceLeaveSent = true;
        recordAttendance(0, "leave");
    }

    private void recordAttendance(int watchedSeconds, String action) {
        if (TextUtils.isEmpty(eventId)) return;
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(token -> {
            Map<String, Object> body = new HashMap<>();
            body.put("watchedSeconds", watchedSeconds);
            body.put("action", action);
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
        if (attendanceEligible && !attendanceRunning) {
            attendanceRunning = true;
            lastWatchTickMs = android.os.SystemClock.elapsedRealtime();
            recordAttendance(0, "heartbeat");
            attendanceHandler.postDelayed(attendanceHeartbeat, 15_000L);
        }
    }

    @Override
    protected void onPause() {
        telemetryRunning = false;
        telemetryHandler.removeCallbacks(telemetryPoll);
        // PiP transition may pause before the platform reports PiP mode.
        // Keep attendance running until onStop confirms the viewer is no longer visible.
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !isFinishing()
                && webView != null
                && webView.getVisibility() == View.VISIBLE
                && !isInPictureInPictureMode()) {
            try {
                enterPictureInPictureMode(
                        new PictureInPictureParams.Builder()
                                .setAspectRatio(new Rational(16, 9))
                                .build()
                );
            } catch (Exception ignored) {
            }
        }
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode())) {
            attendanceRunning = false;
            attendanceHandler.removeCallbacks(attendanceHeartbeat);
            lastWatchTickMs = 0L;
            if (isFinishing()) recordLeaveOnce();
        }
        super.onStop();
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
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (inPictureInPictureMode) {
            // onPause happens before the PiP transition. Resume live stats
            // while the video remains visible and the learner is counted.
            if (!telemetryRunning) {
                telemetryRunning = true;
                loadTelemetry();
                telemetryHandler.postDelayed(telemetryPoll, 5_000L);
            }
            if (attendanceEligible && !attendanceRunning) {
                attendanceRunning = true;
                lastWatchTickMs = android.os.SystemClock.elapsedRealtime();
                recordAttendance(0, "heartbeat");
                attendanceHandler.postDelayed(attendanceHeartbeat, 15_000L);
            }
        } else if (!hasWindowFocus()) {
            telemetryRunning = false;
            telemetryHandler.removeCallbacks(telemetryPoll);
            if (attendanceRunning) {
                attendanceRunning = false;
                attendanceHandler.removeCallbacks(attendanceHeartbeat);
                lastWatchTickMs = 0L;
            }
        }
    }

    @Override
    protected void onDestroy() {
        telemetryRunning = false;
        telemetryHandler.removeCallbacks(telemetryPoll);
        attendanceRunning = false;
        attendanceHandler.removeCallbacks(attendanceHeartbeat);
        if (isFinishing()) recordLeaveOnce();
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
