package com.app.nisisiafrica;

import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MentUI Course track: hero + stats + lesson nodes + sticky Continue.
 * Binds LMS when live; otherwise stubs lesson list from course extras.
 * Lesson-level progress uses LMS status when present — otherwise UI stubs locked/current.
 */
public class TrackLearnActivity extends AppCompatActivity {

    public static final String EXTRA_TRACK_ID = "extra_track_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_DESC = "extra_desc";
    public static final String EXTRA_FALLBACK_URL = "extra_fallback_url";

    private ProgressBar progress;
    private ProgressBar trackProgress;
    private TextView tvTitle;
    private TextView tvDesc;
    private TextView tvProgressLabel;
    private TextView tvStatDone;
    private TextView tvStatProgress;
    private TextView tvStatLocked;
    private TextView tvPlayerPlaceholder;
    private VideoView videoView;
    private LinearLayout modulesContainer;
    private MaterialButton btnEnroll;
    private String trackId;
    private String fallbackUrl;
    private final List<LmsModels.LessonDto> flatLessons = new ArrayList<>();
    private LmsModels.LessonDto resumeLesson;
    private LmsModels.CohortRunDto cohortRun;
    private LmsModels.TrackPrice trackPrice;

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
        trackProgress = findViewById(R.id.trackProgress);
        tvTitle = findViewById(R.id.tvTitle);
        tvDesc = findViewById(R.id.tvDesc);
        tvProgressLabel = findViewById(R.id.tvProgressLabel);
        tvStatDone = findViewById(R.id.tvStatDone);
        tvStatProgress = findViewById(R.id.tvStatProgress);
        tvStatLocked = findViewById(R.id.tvStatLocked);
        tvPlayerPlaceholder = findViewById(R.id.tvPlayerPlaceholder);
        videoView = findViewById(R.id.videoView);
        modulesContainer = findViewById(R.id.modulesContainer);
        btnEnroll = findViewById(R.id.btnEnroll);

        if (!TextUtils.isEmpty(title)) tvTitle.setText(title);
        if (!TextUtils.isEmpty(desc)) tvDesc.setText(desc);

        btnEnroll.setOnClickListener(v -> {
            if (resumeLesson != null) {
                openLesson(resumeLesson);
            } else if (btnEnroll.isEnabled()) {
                enroll();
            }
        });
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
                        resumeProgress();
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

    private void resumeProgress() {
        if (TextUtils.isEmpty(trackId)) return;
        withBearer(bearer -> ApiClient.getLmsService().myProgress(bearer, trackId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.ProgressMapEnvelope> call,
                                           Response<LmsModels.ProgressMapEnvelope> response) {
                        LmsModels.ProgressMapEnvelope body = response.body();
                        if (!response.isSuccessful() || body == null || !body.ok || body.data == null) {
                            return;
                        }
                        Object trackObj = body.data.byTrackId != null
                                ? body.data.byTrackId.get(trackId) : null;
                        if (trackObj instanceof Map) {
                            Object pct = ((Map<?, ?>) trackObj).get("trackPercent");
                            if (pct instanceof Number) {
                                int p = ((Number) pct).intValue();
                                trackProgress.setProgress(p);
                                tvProgressLabel.setText(
                                        String.format(Locale.getDefault(), "Overall progress · %d%%", p));
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.ProgressMapEnvelope> call, Throwable t) {}
                }));
    }

    private void bindTrack(LmsModels.TrackDetailData data) {
        cohortRun = data.cohortRun;
        LmsModels.TrackCard track = data.track;
        if (track != null) {
            trackPrice = track.price;
            if (!TextUtils.isEmpty(track.courseTitle)) tvTitle.setText(track.courseTitle);
            String meta = "";
            if (!TextUtils.isEmpty(track.tutorName)) meta = track.tutorName;
            if (!TextUtils.isEmpty(track.lessonsString())) {
                meta += (meta.isEmpty() ? "" : " · ") + track.lessonsString() + " lessons";
            }
            if (!TextUtils.isEmpty(track.durationString())) {
                meta += (meta.isEmpty() ? "" : " · ") + track.durationString() + " h";
            }
            if (track.price != null && track.price.isPaid) {
                meta += (meta.isEmpty() ? "" : " · ") + formatPrice(track.price);
            }
            if (!meta.isEmpty()) tvDesc.setText(meta);
            else if (!TextUtils.isEmpty(track.does)) tvDesc.setText(track.does);
            int pct = Math.round(track.trackPercent);
            trackProgress.setProgress(pct);
            tvProgressLabel.setText(
                    String.format(Locale.getDefault(), "Overall progress · %d%%", pct));
            if (track.enrolled) {
                btnEnroll.setText("Continue learning");
            } else if (track.price != null && track.price.isPaid) {
                btnEnroll.setText("Unlock · " + formatPrice(track.price));
            }
        }
        if (cohortRun != null && cohortRun.milestones != null && !cohortRun.milestones.isEmpty()) {
            TextView walk = new TextView(this);
            walk.setText("Cohort walkthrough");
            walk.setTextSize(14f);
            walk.setPadding(0, 4, 0, 4);
            walk.setTextColor(getColor(R.color.muted));
            modulesContainer.removeAllViews();
            modulesContainer.addView(walk);
            for (LmsModels.MilestoneDto m : cohortRun.milestones) {
                TextView row = new TextView(this);
                String label = m.title != null ? m.title : ("Milestone " + m.order);
                String state = m.completed ? "done"
                        : (m.available ? "open" : (m.lockedReason != null ? m.lockedReason : "locked"));
                row.setText("• " + label + " · " + state);
                row.setTextSize(13f);
                row.setPadding(0, 2, 0, 2);
                row.setTextColor(getColor(m.available || m.completed ? R.color.ink : R.color.muted));
                modulesContainer.addView(row);
            }
        }
        List<LmsModels.ModuleDto> modules = data.modules;
        if (modules == null || modules.isEmpty()) {
            if (cohortRun == null || cohortRun.milestones == null || cohortRun.milestones.isEmpty()) {
                modulesContainer.removeAllViews();
                showFallbackLessonsHint();
            }
            return;
        }
        if (cohortRun == null || cohortRun.milestones == null || cohortRun.milestones.isEmpty()) {
            modulesContainer.removeAllViews();
        }
        for (LmsModels.ModuleDto module : modules) {
            TextView header = new TextView(this);
            header.setText(module.title != null ? module.title : "Module");
            header.setTextSize(14f);
            header.setPadding(0, 12, 0, 4);
            header.setTextColor(getColor(R.color.muted));
            modulesContainer.addView(header);
            loadModuleLessonsInto(module.moduleId, module == modules.get(0));
        }
    }

    private void loadModuleLessonsInto(String moduleId, boolean autoExpand) {
        if (TextUtils.isEmpty(moduleId) || !autoExpand) return;
        withBearer(bearer -> ApiClient.getLmsService().module(bearer, moduleId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.ModuleDetailEnvelope> call,
                                           Response<LmsModels.ModuleDetailEnvelope> response) {
                        LmsModels.ModuleDetailEnvelope body = response.body();
                        if (!response.isSuccessful() || body == null || !body.ok
                                || body.data == null || body.data.lessons == null) {
                            return;
                        }
                        flatLessons.clear();
                        flatLessons.addAll(body.data.lessons);
                        renderLessonNodes(flatLessons);
                    }

                    @Override
                    public void onFailure(Call<LmsModels.ModuleDetailEnvelope> call, Throwable t) {}
                }));
    }

    private void renderLessonNodes(List<LmsModels.LessonDto> lessons) {
        // Keep module headers; append lesson rows after last child or rebuild bottom.
        int done = 0, inProg = 0, locked = 0;
        resumeLesson = null;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < lessons.size(); i++) {
            LmsModels.LessonDto lesson = lessons.get(i);
            LmsModels.MilestoneDto mile = milestoneFor(lesson.lessonId);
            String status = lesson.status != null ? lesson.status.toLowerCase(Locale.US) : "";
            boolean isDone = "done".equals(status) || "completed".equals(status)
                    || lesson.lessonPercent >= 100f
                    || (mile != null && mile.completed);
            boolean isLocked = mile != null
                    ? !mile.available && !mile.completed
                    : "locked".equals(status);
            boolean isCurrent = !isDone && !isLocked
                    && ("current".equals(status) || "in_progress".equals(status)
                    || resumeLesson == null);

            // Default when LMS has no per-lesson status / milestone yet:
            if (mile == null && TextUtils.isEmpty(status) && lesson.lessonPercent <= 0) {
                if (i == 0) {
                    isCurrent = true;
                    isLocked = false;
                } else {
                    isLocked = true;
                    isCurrent = false;
                }
            }

            if (isDone) done++;
            else if (isLocked) locked++;
            else {
                inProg++;
                if (resumeLesson == null) resumeLesson = lesson;
            }

            View row = inflater.inflate(R.layout.item_lesson_node, modulesContainer, false);
            TextView node = row.findViewById(R.id.lessonNode);
            TextView title = row.findViewById(R.id.tvLessonTitle);
            TextView meta = row.findViewById(R.id.tvLessonMeta);
            TextView action = row.findViewById(R.id.tvLessonAction);
            View line = row.findViewById(R.id.lessonLine);
            line.setVisibility(i == lessons.size() - 1 ? View.INVISIBLE : View.VISIBLE);

            title.setText(lesson.title != null ? lesson.title : "Lesson");
            String mins = lesson.estimatedMinutes > 0
                    ? lesson.estimatedMinutes + " min" : "";
            String type = lesson.type != null ? lesson.type : "";
            String lockHint = "";
            if (isLocked && mile != null) {
                lockHint = mile.lockedReason != null ? mile.lockedReason : "locked";
            }
            meta.setText((mins
                    + (mins.isEmpty() || type.isEmpty() ? "" : " · ")
                    + type
                    + (lockHint.isEmpty() ? "" : " · " + lockHint)).trim());

            if (isDone) {
                node.setBackgroundResource(R.drawable.bg_lesson_node_done);
                node.setText("✓");
                node.setTextColor(getColor(R.color.white));
                action.setVisibility(View.GONE);
            } else if (isCurrent) {
                node.setBackgroundResource(R.drawable.bg_lesson_node_current);
                node.setText("▶");
                node.setTextColor(getColor(R.color.maroon_700));
                action.setVisibility(View.VISIBLE);
                action.setText("Resume");
            } else {
                node.setBackgroundResource(R.drawable.bg_lesson_node_locked);
                node.setText("🔒");
                node.setTextColor(getColor(R.color.muted));
                action.setVisibility(View.GONE);
            }

            boolean clickable = !isLocked;
            row.setAlpha(isLocked ? 0.55f : 1f);
            row.setOnClickListener(v -> {
                if (clickable) openLesson(lesson);
                else Toast.makeText(this, milestoneLockMessage(mile), Toast.LENGTH_SHORT).show();
            });
            modulesContainer.addView(row);
        }
        tvStatDone.setText(String.valueOf(done));
        tvStatProgress.setText(String.valueOf(inProg));
        tvStatLocked.setText(String.valueOf(locked));
        if (resumeLesson != null) {
            btnEnroll.setText("Continue learning");
            btnEnroll.setEnabled(true);
        }
    }

    @Nullable
    private LmsModels.MilestoneDto milestoneFor(String lessonId) {
        if (cohortRun == null || cohortRun.milestones == null || TextUtils.isEmpty(lessonId)) {
            return null;
        }
        for (LmsModels.MilestoneDto m : cohortRun.milestones) {
            if (lessonId.equals(m.lessonId)) return m;
        }
        return null;
    }

    private static String milestoneLockMessage(@Nullable LmsModels.MilestoneDto mile) {
        if (mile == null) return "Lesson locked";
        if ("release_date".equals(mile.lockedReason)) {
            return "Opens after release date";
        }
        return "Complete the previous milestone first";
    }

    private static String formatPrice(LmsModels.TrackPrice price) {
        if (price == null || !price.isPaid || price.amountMinor <= 0) return "Free";
        return String.format(Locale.US, "%s %.2f",
                price.currency != null ? price.currency : "USD",
                price.amountMinor / 100.0);
    }

    private void openLesson(LmsModels.LessonDto lesson) {
        if (lesson == null) return;
        LmsModels.MilestoneDto mile = milestoneFor(lesson.lessonId);
        if (mile != null && !mile.available && !mile.completed) {
            Toast.makeText(this, milestoneLockMessage(mile), Toast.LENGTH_SHORT).show();
            return;
        }
        if (lesson.hasQuiz || (lesson.quiz != null && "single_answer".equals(lesson.quiz.mode))) {
            promptQuiz(lesson);
            return;
        }
        if (lesson.hasAssignment) {
            promptAssignment(lesson);
            return;
        }
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
                                && body.data != null && body.data.lesson != null) {
                            LmsModels.LessonDto full = body.data.lesson;
                            if (full.milestone != null && !full.milestone.available
                                    && !full.milestone.completed) {
                                Toast.makeText(TrackLearnActivity.this,
                                        milestoneLockMessage(full.milestone),
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (full.hasQuiz || (full.quiz != null
                                    && "single_answer".equals(full.quiz.mode))) {
                                promptQuiz(full);
                            } else if (full.hasAssignment) {
                                promptAssignment(full);
                            } else if (!TextUtils.isEmpty(full.playbackUrl)) {
                                playUrl(full.playbackUrl);
                                reportProgress(lesson.lessonId, true, 1f, 0f);
                            } else {
                                reportProgress(lesson.lessonId, true, 1f, 0f);
                                Toast.makeText(TrackLearnActivity.this,
                                        "Marked opened — no media URL", Toast.LENGTH_SHORT).show();
                            }
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

    private void promptQuiz(LmsModels.LessonDto lesson) {
        if (lesson.quiz != null && "single_answer".equals(lesson.quiz.mode)
                && lesson.quiz.options != null && !lesson.quiz.options.isEmpty()) {
            promptAuthoredQuiz(lesson);
            return;
        }
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Score 0–100");
        input.setText("85");
        new AlertDialog.Builder(this)
                .setTitle(lesson.title != null ? lesson.title : "Quiz")
                .setMessage(lesson.does != null && !lesson.does.isEmpty()
                        ? lesson.does
                        : "Enter your quiz score (pass ≥ 80).")
                .setView(input)
                .setPositiveButton("Submit pass", (d, w) -> {
                    int score = parseScore(input.getText().toString(), 85);
                    submitQuiz(lesson.lessonId, Math.max(score, 80), true);
                })
                .setNeutralButton("Save score", (d, w) -> {
                    int score = parseScore(input.getText().toString(), 70);
                    submitQuiz(lesson.lessonId, score, score >= 80);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptAuthoredQuiz(LmsModels.LessonDto lesson) {
        List<LmsModels.QuizOptionDto> options = lesson.quiz.options;
        CharSequence[] labels = new CharSequence[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).text != null ? options.get(i).text : options.get(i).id;
        }
        final int[] selected = {-1};
        String prompt = lesson.quiz.prompt != null ? lesson.quiz.prompt : "Choose an answer";
        new AlertDialog.Builder(this)
                .setTitle(lesson.title != null ? lesson.title : "Quiz")
                .setMessage(prompt)
                .setSingleChoiceItems(labels, -1, (d, which) -> selected[0] = which)
                .setPositiveButton("Submit", (d, w) -> {
                    if (selected[0] < 0 || selected[0] >= options.size()) {
                        Toast.makeText(this, "Pick an answer", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String optionId = options.get(selected[0]).id;
                    submitAuthoredQuiz(lesson.lessonId, optionId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitAuthoredQuiz(String lessonId, String selectedOptionId) {
        withBearer(bearer -> ApiClient.getLmsService()
                .submitQuiz(bearer, lessonId, LmsModels.QuizBody.option(selectedOptionId))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.QuizEnvelope> call,
                                           Response<LmsModels.QuizEnvelope> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            float pct = response.body().data != null
                                    ? response.body().data.quizPct : 0f;
                            Toast.makeText(TrackLearnActivity.this,
                                    "Quiz scored " + Math.round(pct) + "%",
                                    Toast.LENGTH_SHORT).show();
                            loadTrack();
                        } else {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Quiz submit failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.QuizEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "Quiz submit failed", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void promptAssignment(LmsModels.LessonDto lesson) {
        final EditText input = new EditText(this);
        input.setMinLines(4);
        input.setHint("Your assignment response");
        new AlertDialog.Builder(this)
                .setTitle(lesson.title != null ? lesson.title : "Assignment")
                .setMessage(lesson.does != null && !lesson.does.isEmpty()
                        ? lesson.does
                        : "Write your response for mentor review.")
                .setView(input)
                .setPositiveButton("Submit", (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if (text.length() < 8) {
                        Toast.makeText(this, "Write a bit more", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitAssignment(lesson.lessonId, text);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static int parseScore(String raw, int fallback) {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(raw.trim())));
        } catch (Exception e) {
            return fallback;
        }
    }

    private void submitQuiz(String lessonId, int score, boolean passed) {
        withBearer(bearer -> ApiClient.getLmsService()
                .submitQuiz(bearer, lessonId, new LmsModels.QuizBody(score, passed))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.QuizEnvelope> call,
                                           Response<LmsModels.QuizEnvelope> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Quiz saved (" + score + "%)", Toast.LENGTH_SHORT).show();
                            loadTrack();
                        } else {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Quiz submit failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.QuizEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "Quiz network error", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void submitAssignment(String lessonId, String text) {
        withBearer(bearer -> ApiClient.getLmsService()
                .submitAssignment(bearer, new LmsModels.SubmissionBody(lessonId, text))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.SubmissionEnvelope> call,
                                           Response<LmsModels.SubmissionEnvelope> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Assignment submitted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Submit failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.SubmissionEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "Submit network error", Toast.LENGTH_SHORT).show();
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
                        LmsModels.EnrollmentEnvelope body = response.body();
                        if (body == null) {
                            body = parseEnrollmentError(response);
                        }
                        if (response.isSuccessful() && body != null && body.ok) {
                            btnEnroll.setText("Continue learning");
                            Toast.makeText(TrackLearnActivity.this, "Enrolled", Toast.LENGTH_SHORT).show();
                            loadTrack();
                            return;
                        }
                        if (body != null && body.data != null
                                && "TRACK_PAYMENT_REQUIRED".equals(body.data.code)
                                && body.data.price != null) {
                            showPaywall(body.data.price);
                            return;
                        }
                        Toast.makeText(TrackLearnActivity.this,
                                body != null && body.error != null
                                        ? body.error
                                        : "Enroll failed (" + response.code() + ")",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Call<LmsModels.EnrollmentEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "LMS not ready — try again later", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void showPaywall(LmsModels.TrackPrice price) {
        new AlertDialog.Builder(this)
                .setTitle("Unlock this track")
                .setMessage("Demo checkout — no live card or M-Pesa yet.\n\n"
                        + formatPrice(price)
                        + "\n\nPaying records access so you can enroll.")
                .setPositiveButton("Pay (demo) & enroll", (d, w) -> payAndEnroll())
                .setNeutralButton("My purchases", (d, w) -> showPurchases())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void payAndEnroll() {
        withBearer(bearer -> ApiClient.getLmsService()
                .checkout(bearer, new LmsModels.CheckoutBody(trackId))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.CheckoutEnvelope> call,
                                           Response<LmsModels.CheckoutEnvelope> response) {
                        LmsModels.CheckoutEnvelope body = response.body();
                        if (!response.isSuccessful() || body == null || !body.ok) {
                            Toast.makeText(TrackLearnActivity.this,
                                    "Checkout failed", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(TrackLearnActivity.this,
                                "Payment recorded", Toast.LENGTH_SHORT).show();
                        enroll();
                    }

                    @Override
                    public void onFailure(Call<LmsModels.CheckoutEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "Checkout failed", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void showPurchases() {
        withBearer(bearer -> ApiClient.getLmsService().myPurchases(bearer)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.PurchasesEnvelope> call,
                                           Response<LmsModels.PurchasesEnvelope> response) {
                        LmsModels.PurchasesEnvelope body = response.body();
                        if (!response.isSuccessful() || body == null || !body.ok
                                || body.data == null || body.data.purchases == null
                                || body.data.purchases.isEmpty()) {
                            Toast.makeText(TrackLearnActivity.this,
                                    "No purchases yet", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        for (LmsModels.PurchaseDto p : body.data.purchases) {
                            String title = p.courseTitle != null ? p.courseTitle : p.trackId;
                            sb.append("• ").append(title)
                                    .append(" · ").append(p.currency != null ? p.currency : "USD")
                                    .append(" ")
                                    .append(String.format(Locale.US, "%.2f", p.amountMinor / 100.0))
                                    .append(" · ").append(p.status != null ? p.status : "")
                                    .append("\n");
                        }
                        new AlertDialog.Builder(TrackLearnActivity.this)
                                .setTitle("My purchases")
                                .setMessage(sb.toString().trim())
                                .setPositiveButton("OK", null)
                                .show();
                    }

                    @Override
                    public void onFailure(Call<LmsModels.PurchasesEnvelope> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "Could not load purchases", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    @Nullable
    private static LmsModels.EnrollmentEnvelope parseEnrollmentError(
            Response<LmsModels.EnrollmentEnvelope> response) {
        try {
            if (response.errorBody() == null) return null;
            return new com.google.gson.Gson().fromJson(
                    response.errorBody().charStream(),
                    LmsModels.EnrollmentEnvelope.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void showFallbackLessonsHint() {
        TextView hint = new TextView(this);
        hint.setText("Lessons will appear here when the LMS publishes modules for this track.");
        hint.setTextColor(getColor(R.color.muted));
        modulesContainer.addView(hint);
        tvStatDone.setText("0");
        tvStatProgress.setText("0");
        tvStatLocked.setText("—");
    }

    private void showFallback() {
        if (!TextUtils.isEmpty(fallbackUrl)
                && (fallbackUrl.contains(".mp4") || fallbackUrl.contains("playback")
                || fallbackUrl.contains("m3u8"))) {
            playUrl(fallbackUrl);
            tvPlayerPlaceholder.setText("Playing linked media");
        } else {
            tvPlayerPlaceholder.setVisibility(View.VISIBLE);
            tvPlayerPlaceholder.setText("Course player ready — lessons sync when LMS is live.");
            modulesContainer.removeAllViews();
            showFallbackLessonsHint();
            // TODO: lesson-level progress not tracked offline yet — UI stubs only.
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
