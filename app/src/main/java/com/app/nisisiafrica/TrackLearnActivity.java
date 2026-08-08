package com.app.nisisiafrica;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.Interfaces.LmsApiService;
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * In-app LMS track player. Catalog from GET /lms/tracks/:id (+ modules).
 */
public class TrackLearnActivity extends AppCompatActivity {

    public static final String EXTRA_TRACK_ID = "extra_track_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_DESC = "extra_desc";
    public static final String EXTRA_FALLBACK_URL = "extra_fallback_url";

    private ProgressBar progress;
    private TextView tvTitle;
    private TextView tvDesc;
    private TextView tvPlayerPlaceholder;
    private VideoView videoView;
    private LinearLayout modulesContainer;
    private MaterialButton btnEnroll;
    private String trackId;
    private String fallbackUrl;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_track_learn);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        trackId = getIntent().getStringExtra(EXTRA_TRACK_ID);
        fallbackUrl = getIntent().getStringExtra(EXTRA_FALLBACK_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String desc = getIntent().getStringExtra(EXTRA_DESC);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progress);
        tvTitle = findViewById(R.id.tvTitle);
        tvDesc = findViewById(R.id.tvDesc);
        tvPlayerPlaceholder = findViewById(R.id.tvPlayerPlaceholder);
        videoView = findViewById(R.id.videoView);
        modulesContainer = findViewById(R.id.modulesContainer);
        btnEnroll = findViewById(R.id.btnEnroll);

        if (!TextUtils.isEmpty(title)) tvTitle.setText(title);
        if (!TextUtils.isEmpty(desc)) tvDesc.setText(desc);

        btnEnroll.setOnClickListener(v -> enroll());
        loadTrack();
    }

    private void withBearer(BearerCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in to learn", Toast.LENGTH_SHORT).show();
            return;
        }
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Could not auth LMS", Toast.LENGTH_SHORT).show();
                return;
            }
            cb.onToken("Bearer " + task.getResult().getToken());
        });
    }

    private void loadTrack() {
        if (TextUtils.isEmpty(trackId)) {
            showFallback();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        withBearer(bearer -> {
            LmsApiService api = ApiClient.getLmsService();
            api.track(bearer, trackId).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<LmsModels.TrackDetailEnvelope> call,
                                       Response<LmsModels.TrackDetailEnvelope> response) {
                    progress.setVisibility(View.GONE);
                    LmsModels.TrackDetailEnvelope body = response.body();
                    if (response.isSuccessful() && body != null && body.ok && body.data != null) {
                        bindTrack(body.data);
                    } else {
                        showFallback();
                    }
                }

                @Override
                public void onFailure(Call<LmsModels.TrackDetailEnvelope> call, Throwable t) {
                    progress.setVisibility(View.GONE);
                    showFallback();
                }
            });
        });
    }

    private void bindTrack(LmsModels.TrackDetailData data) {
        LmsModels.TrackCard track = data.track;
        if (track != null) {
            if (!TextUtils.isEmpty(track.courseTitle)) tvTitle.setText(track.courseTitle);
            if (!TextUtils.isEmpty(track.does)) tvDesc.setText(track.does);
            if (track.enrolled) {
                btnEnroll.setText("Enrolled");
                btnEnroll.setEnabled(false);
            }
        }
        modulesContainer.removeAllViews();
        List<LmsModels.ModuleDto> modules = data.modules;
        if (modules == null || modules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Modules will appear when published");
            empty.setTextColor(getColor(R.color.text_secondary));
            modulesContainer.addView(empty);
            return;
        }
        for (LmsModels.ModuleDto module : modules) {
            TextView header = new TextView(this);
            header.setText(module.title != null ? module.title : "Module");
            header.setTextSize(15f);
            header.setPadding(0, 16, 0, 8);
            header.setTextColor(getColor(R.color.text_primary));
            modulesContainer.addView(header);
            MaterialButton openMod = new MaterialButton(this,
                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            openMod.setText("Open module (" + module.lessonCount + " lessons)");
            openMod.setOnClickListener(v -> loadModuleLessons(module.moduleId));
            modulesContainer.addView(openMod);
        }
    }

    private void loadModuleLessons(String moduleId) {
        if (TextUtils.isEmpty(moduleId)) return;
        progress.setVisibility(View.VISIBLE);
        withBearer(bearer -> ApiClient.getLmsService().module(bearer, moduleId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.ModuleDetailEnvelope> call,
                                           Response<LmsModels.ModuleDetailEnvelope> response) {
                        progress.setVisibility(View.GONE);
                        LmsModels.ModuleDetailEnvelope body = response.body();
                        if (!response.isSuccessful() || body == null || !body.ok || body.data == null) {
                            Toast.makeText(TrackLearnActivity.this, "Module unavailable", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        modulesContainer.removeAllViews();
                        if (body.data.module != null && body.data.module.title != null) {
                            TextView header = new TextView(TrackLearnActivity.this);
                            header.setText(body.data.module.title);
                            header.setTextSize(16f);
                            header.setPadding(0, 8, 0, 8);
                            header.setTextColor(getColor(R.color.text_primary));
                            modulesContainer.addView(header);
                        }
                        if (body.data.lessons == null) return;
                        for (LmsModels.LessonDto lesson : body.data.lessons) {
                            MaterialButton lessonBtn = new MaterialButton(TrackLearnActivity.this,
                                    null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                            lessonBtn.setText(lesson.title != null ? lesson.title : "Lesson");
                            lessonBtn.setOnClickListener(v -> openLesson(lesson));
                            modulesContainer.addView(lessonBtn);
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.ModuleDetailEnvelope> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(TrackLearnActivity.this, "Could not load module", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void openLesson(LmsModels.LessonDto lesson) {
        if (lesson == null) return;
        if (!TextUtils.isEmpty(lesson.playbackUrl)) {
            playUrl(lesson.playbackUrl);
            reportProgress(lesson.lessonId, true, 1f, 0f);
            return;
        }
        withBearer(bearer -> ApiClient.getLmsService().lesson(bearer, lesson.lessonId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.LessonDetailEnvelope> call,
                                           Response<LmsModels.LessonDetailEnvelope> response) {
                        LmsModels.LessonDetailEnvelope body = response.body();
                        if (response.isSuccessful() && body != null && body.ok
                                && body.data != null && body.data.lesson != null
                                && !TextUtils.isEmpty(body.data.lesson.playbackUrl)) {
                            playUrl(body.data.lesson.playbackUrl);
                            reportProgress(lesson.lessonId, true, 1f, 0f);
                        } else {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Lesson not ready yet", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.LessonDetailEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "Could not load lesson", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void playUrl(String url) {
        tvPlayerPlaceholder.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoURI(Uri.parse(url));
        videoView.setOnPreparedListener(MediaPlayer::start);
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show();
            return true;
        });
        videoView.start();
    }

    private void reportProgress(String lessonId, boolean opened, float contentPct, float quizPct) {
        if (TextUtils.isEmpty(lessonId)) return;
        withBearer(bearer -> ApiClient.getLmsService()
                .patchProgress(bearer, lessonId, new LmsModels.ProgressBody(opened, contentPct, quizPct))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.ProgressEnvelope> call,
                                           Response<LmsModels.ProgressEnvelope> response) {}

                    @Override
                    public void onFailure(Call<LmsModels.ProgressEnvelope> call, Throwable t) {}
                }));
    }

    private void enroll() {
        if (TextUtils.isEmpty(trackId)) {
            Toast.makeText(this, "Track unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        withBearer(bearer -> ApiClient.getLmsService()
                .enroll(bearer, new LmsModels.EnrollBody(trackId))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.EnrollmentEnvelope> call,
                                           Response<LmsModels.EnrollmentEnvelope> response) {
                        if (response.isSuccessful()) {
                            btnEnroll.setText("Enrolled");
                            btnEnroll.setEnabled(false);
                            Toast.makeText(TrackLearnActivity.this, "Enrolled", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Enroll failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.EnrollmentEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "LMS not ready — try again later", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void showFallback() {
        if (!TextUtils.isEmpty(fallbackUrl)
                && (fallbackUrl.contains(".mp4") || fallbackUrl.contains("playback")
                || fallbackUrl.contains("m3u8"))) {
            playUrl(fallbackUrl);
            tvPlayerPlaceholder.setText("Playing linked media");
        } else {
            tvPlayerPlaceholder.setVisibility(View.VISIBLE);
            tvPlayerPlaceholder.setText(
                    "In-app learning is wiring up. Course content will play here when LMS is live.");
            modulesContainer.removeAllViews();
            TextView hint = new TextView(this);
            hint.setText("Progress, quizzes, and certificates will sync from /lms once the API is deployed.");
            hint.setTextColor(getColor(R.color.text_secondary));
            modulesContainer.addView(hint);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) videoView.pause();
    }

    private interface BearerCallback {
        void onToken(String bearer);
    }
}
