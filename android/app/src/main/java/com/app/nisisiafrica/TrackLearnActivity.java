package com.app.nisisiafrica;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.bumptech.glide.Glide;
import com.github.barteksc.pdfviewer.PDFView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;

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
    public static final String EXTRA_TUTOR_ID = "extra_tutor_id";
    public static final String EXTRA_TUTOR_NAME = "extra_tutor_name";
    public static final String EXTRA_TUTOR_AVATAR = "extra_tutor_avatar";

    private ProgressBar progress;
    private ProgressBar trackProgress;
    private TextView tvTitle;
    private TextView tvDesc;
    private TextView tvProgressLabel;
    private TextView tvStatDone;
    private TextView tvStatProgress;
    private TextView tvStatLocked;
    private TextView tvPlayerPlaceholder;
    private TextView tvTutorName;
    private ImageView ivTutorAvatar;
    private View tutorRow;
    private MaterialButton btnBookTutor;
    private VideoView videoView;
    private PDFView pdfView;
    private View playerFrame;
    private LinearLayout lessonPanel;
    private LinearLayout modulesContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService pdfExec = Executors.newSingleThreadExecutor();
    private final OkHttpClient http = new OkHttpClient();
    private final Runnable watchTick = this::onWatchTick;
    private String activeLessonId;
    private long lastWatchReport;
    private long lastPdfReport;
    private int pdfMaxPage;
    private MaterialButton btnEnroll;
    private MaterialButton btnLeaveCourse;
    private boolean enrolledOnTrack = false;
    private String trackId;
    private String fallbackUrl;
    private String tutorId;
    private String tutorName;
    private String tutorAvatarUrl;
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
        tutorId = getIntent().getStringExtra(EXTRA_TUTOR_ID);
        tutorName = getIntent().getStringExtra(EXTRA_TUTOR_NAME);
        tutorAvatarUrl = getIntent().getStringExtra(EXTRA_TUTOR_AVATAR);

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
        pdfView = findViewById(R.id.pdfView);
        playerFrame = findViewById(R.id.playerFrame);
        lessonPanel = findViewById(R.id.lessonPanel);
        modulesContainer = findViewById(R.id.modulesContainer);
        btnEnroll = findViewById(R.id.btnEnroll);
        btnLeaveCourse = findViewById(R.id.btnLeaveCourse);
        tutorRow = findViewById(R.id.tutorRow);
        tvTutorName = findViewById(R.id.tvTutorName);
        ivTutorAvatar = findViewById(R.id.ivTutorAvatar);
        btnBookTutor = findViewById(R.id.btnBookTutor);

        if (!TextUtils.isEmpty(title)) tvTitle.setText(title);
        if (!TextUtils.isEmpty(desc)) tvDesc.setText(desc);
        bindTutorRow(tutorId, tutorName, tutorAvatarUrl);

        btnEnroll.setOnClickListener(v -> {
            if (resumeLesson != null) {
                openLesson(resumeLesson);
            } else if (btnEnroll.isEnabled()) {
                enroll();
            }
        });
        if (btnLeaveCourse != null) {
            btnLeaveCourse.setOnClickListener(v -> confirmLeaveCourse());
        }
        loadTrack();
    }

    private void bindTutorRow(String id, String name, String avatarUrl) {
        if (tutorRow == null || tvTutorName == null) return;
        if (TextUtils.isEmpty(id) && TextUtils.isEmpty(name)) {
            tutorRow.setVisibility(View.GONE);
            return;
        }
        tutorRow.setVisibility(View.VISIBLE);
        String label = !TextUtils.isEmpty(name) ? name : "Tutor";
        tvTutorName.setText(label);
        if (ivTutorAvatar != null) {
            if (!TextUtils.isEmpty(avatarUrl)) {
                Glide.with(this).load(avatarUrl).circleCrop().into(ivTutorAvatar);
            } else {
                ivTutorAvatar.setImageResource(R.drawable.ic_person);
            }
        }
        View.OnClickListener openProfile = v -> openTutorProfile(id, label);
        tvTutorName.setOnClickListener(openProfile);
        if (ivTutorAvatar != null) ivTutorAvatar.setOnClickListener(openProfile);
        View identity = findViewById(R.id.tutorIdentity);
        if (identity != null) identity.setOnClickListener(openProfile);
        if (btnBookTutor != null) {
            boolean brandTutor = isBrandTutor(id, name);
            boolean canBook = com.app.nisisiafrica.Utils.Roles.browsesMentors()
                    && !TextUtils.isEmpty(id)
                    && !brandTutor;
            btnBookTutor.setVisibility(canBook ? View.VISIBLE : View.GONE);
            btnBookTutor.setOnClickListener(v -> {
                if (TextUtils.isEmpty(id) || isBrandTutor(id, name)) return;
                Intent book = new Intent(this, BookMentor.class);
                book.putExtra(Constants.MENTOR_ID, id);
                book.putExtra(Constants.MENTOR_NAME, label);
                startActivity(book);
            });
        }
    }

    /** Org placeholder tutors are not bookable people. */
    private static boolean isBrandTutor(String id, String name) {
        if (TextUtils.isEmpty(id) || "nelsen-org".equalsIgnoreCase(id)) return true;
        if (TextUtils.isEmpty(name)) return false;
        String n = name.trim();
        return n.equalsIgnoreCase("Nelsen Savannah")
                || n.equalsIgnoreCase("Nelsen Savannah Innovation Hub");
    }

    private void openTutorProfile(String id, String name) {
        if (TextUtils.isEmpty(id)) {
            Toast.makeText(this, "Tutor profile unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent profile = new Intent(this, ProfileActivity.class);
        profile.putExtra(Constants.IS_MENTOR, true);
        profile.putExtra(Constants.MENTOR_ID, id);
        if (!TextUtils.isEmpty(name)) {
            profile.putExtra(Constants.MENTOR_NAME, name);
        }
        startActivity(profile);
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
            if (!TextUtils.isEmpty(track.tutorId)) tutorId = track.tutorId;
            if (!TextUtils.isEmpty(track.tutorName)) tutorName = track.tutorName;
            if (!TextUtils.isEmpty(track.tutorAvatarUrl)) tutorAvatarUrl = track.tutorAvatarUrl;
            bindTutorRow(tutorId, tutorName, tutorAvatarUrl);
            String meta = "";
            if (!TextUtils.isEmpty(track.does)) meta = track.does;
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
            int pct = Math.round(track.trackPercent);
            trackProgress.setProgress(pct);
            tvProgressLabel.setText(
                    String.format(Locale.getDefault(), "Overall progress · %d%%", pct));
            if (track.enrolled) {
                enrolledOnTrack = true;
                btnEnroll.setText("Continue learning");
                if (btnLeaveCourse != null) btnLeaveCourse.setVisibility(View.VISIBLE);
            } else if (track.price != null && track.price.isPaid) {
                enrolledOnTrack = false;
                btnEnroll.setText("Unlock · " + formatPrice(track.price));
                if (btnLeaveCourse != null) btnLeaveCourse.setVisibility(View.GONE);
            } else {
                enrolledOnTrack = false;
                if (btnLeaveCourse != null) btnLeaveCourse.setVisibility(View.GONE);
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
                            presentLesson(full);
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

    private void presentLesson(LmsModels.LessonDto lesson) {
        if (lesson == null) return;
        activeLessonId = lesson.lessonId;
        stopWatchLoop();
        String url = !TextUtils.isEmpty(lesson.playbackUrl) ? lesson.playbackUrl : lesson.contentUrl;
        if (isPdfLesson(lesson, url)) {
            openPdf(url, Math.max(0, lesson.lastPage));
        } else if (!TextUtils.isEmpty(url)) {
            playUrl(url);
        } else {
            hidePlayers();
            if (playerFrame != null) playerFrame.setVisibility(View.VISIBLE);
            tvPlayerPlaceholder.setVisibility(View.VISIBLE);
            tvPlayerPlaceholder.setText("No media for this lesson");
        }
        bindLessonWork(lesson);
        reportOpened(lesson.lessonId);
    }

    private static boolean isPdfLesson(LmsModels.LessonDto lesson, String url) {
        if (lesson.isPdf || "pdf".equals(lesson.type)) return true;
        return url != null && url.matches("(?i).*\\.pdf(\\?.*)?$");
    }

    private void bindLessonWork(LmsModels.LessonDto lesson) {
        if (lessonPanel == null) return;
        lessonPanel.removeAllViews();
        boolean showQuiz = lesson.hasQuiz || lesson.quiz != null;
        boolean showAssignment = lesson.hasAssignment
                || "text".equals(lesson.type)
                || !TextUtils.isEmpty(lesson.assignmentPrompt);
        if (!showQuiz && !showAssignment) {
            lessonPanel.setVisibility(View.GONE);
            return;
        }
        lessonPanel.setVisibility(View.VISIBLE);
        TextView result = sectionLabel("");
        result.setTag("result");
        if (showQuiz) bindQuiz(lesson, result);
        if (showAssignment) bindAssignment(lesson, result);
        lessonPanel.addView(result);
    }

    private void bindQuiz(LmsModels.LessonDto lesson, TextView result) {
        LmsModels.LessonQuizDto quiz = lesson.quiz;
        String mode = quiz != null ? quiz.mode : null;
        if ("multi_answer".equals(mode) && quiz.questions != null && !quiz.questions.isEmpty()) {
            Map<String, RadioGroup> groups = new LinkedHashMap<>();
            for (LmsModels.QuizQuestionDto q : quiz.questions) {
                lessonPanel.addView(sectionLabel(q.prompt != null ? q.prompt : "Choose an answer"));
                RadioGroup group = new RadioGroup(this);
                group.setOrientation(RadioGroup.VERTICAL);
                if (q.options != null) {
                    for (LmsModels.QuizOptionDto option : q.options) {
                        RadioButton rb = new RadioButton(this);
                        String letter = option.id != null ? option.id.toUpperCase() + ". " : "";
                        rb.setId(View.generateViewId());
                        rb.setText(letter + (option.text != null ? option.text : ""));
                        rb.setTag(option.id);
                        group.addView(rb);
                    }
                }
                groups.put(q.id, group);
                lessonPanel.addView(group);
            }
            MaterialButton submit = new MaterialButton(this);
            submit.setText("Submit answers");
            submit.setOnClickListener(v -> {
                Map<String, String> answers = new LinkedHashMap<>();
                for (Map.Entry<String, RadioGroup> entry : groups.entrySet()) {
                    int checked = entry.getValue().getCheckedRadioButtonId();
                    RadioButton rb = entry.getValue().findViewById(checked);
                    if (rb == null || rb.getTag() == null) {
                        Toast.makeText(this, "Answer every question", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    answers.put(entry.getKey(), String.valueOf(rb.getTag()));
                }
                submitAuthoredQuizAnswers(lesson.lessonId, answers, result);
            });
            lessonPanel.addView(submit);
            return;
        }
        if ("single_answer".equals(mode) && quiz.options != null && !quiz.options.isEmpty()) {
            lessonPanel.addView(sectionLabel(quiz.prompt != null ? quiz.prompt : "Choose an answer"));
            RadioGroup group = new RadioGroup(this);
            group.setOrientation(RadioGroup.VERTICAL);
            for (LmsModels.QuizOptionDto option : quiz.options) {
                RadioButton rb = new RadioButton(this);
                String letter = option.id != null ? option.id.toUpperCase() + ". " : "";
                rb.setId(View.generateViewId());
                rb.setText(letter + (option.text != null ? option.text : option.id));
                rb.setTag(option.id);
                group.addView(rb);
            }
            lessonPanel.addView(group);
            MaterialButton submit = new MaterialButton(this);
            submit.setText("Submit answer");
            submit.setOnClickListener(v -> {
                int checked = group.getCheckedRadioButtonId();
                RadioButton rb = group.findViewById(checked);
                if (rb == null || rb.getTag() == null) {
                    Toast.makeText(this, "Pick an answer", Toast.LENGTH_SHORT).show();
                    return;
                }
                submitAuthoredQuiz(lesson.lessonId, String.valueOf(rb.getTag()), result);
            });
            lessonPanel.addView(submit);
            return;
        }
        lessonPanel.addView(sectionLabel(quiz != null && quiz.prompt != null
                ? quiz.prompt
                : "Record how you did on this lesson’s quiz."));
        EditText score = new EditText(this);
        score.setInputType(InputType.TYPE_CLASS_NUMBER);
        score.setHint("Score 0–100");
        lessonPanel.addView(score);
        MaterialButton submit = new MaterialButton(this);
        submit.setText("Save score");
        submit.setOnClickListener(v -> {
            int value = parseScore(score.getText() != null ? score.getText().toString() : "", -1);
            if (value < 0) {
                Toast.makeText(this, "Enter a score", Toast.LENGTH_SHORT).show();
                return;
            }
            submitQuiz(lesson.lessonId, value, value >= 80, result);
        });
        lessonPanel.addView(submit);
    }

    private void bindAssignment(LmsModels.LessonDto lesson, TextView result) {
        String prompt = !TextUtils.isEmpty(lesson.assignmentPrompt) ? lesson.assignmentPrompt : lesson.does;
        lessonPanel.addView(sectionLabel(!TextUtils.isEmpty(prompt)
                ? prompt
                : "Write your response and submit."));
        EditText input = new EditText(this);
        input.setMinLines(4);
        input.setHint("Your response");
        lessonPanel.addView(input);
        MaterialButton submit = new MaterialButton(this);
        submit.setText("Submit assignment");
        submit.setOnClickListener(v -> {
            String text = input.getText() != null ? input.getText().toString().trim() : "";
            if (text.length() < 8) {
                Toast.makeText(this, "Write a bit more", Toast.LENGTH_SHORT).show();
                return;
            }
            submitAssignment(lesson.lessonId, text, result);
        });
        lessonPanel.addView(submit);
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(getColor(R.color.ink));
        label.setPadding(0, dp(8), 0, dp(4));
        return label;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void submitAuthoredQuizAnswers(String lessonId, java.util.Map<String, String> answers,
                                           TextView result) {
        withBearer(bearer -> ApiClient.getLmsService()
                .submitQuiz(bearer, lessonId, LmsModels.QuizBody.answers(answers))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.QuizEnvelope> call,
                                           Response<LmsModels.QuizEnvelope> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            float pct = response.body().data != null
                                    ? response.body().data.quizPct : 0f;
                            showWorkResult(result, "Quiz scored " + Math.round(pct) + "%");
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

    private void submitAuthoredQuiz(String lessonId, String selectedOptionId, TextView result) {
        withBearer(bearer -> ApiClient.getLmsService()
                .submitQuiz(bearer, lessonId, LmsModels.QuizBody.option(selectedOptionId))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.QuizEnvelope> call,
                                           Response<LmsModels.QuizEnvelope> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            float pct = response.body().data != null
                                    ? response.body().data.quizPct : 0f;
                            showWorkResult(result, "Quiz scored " + Math.round(pct) + "%");
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

    private static int parseScore(String raw, int fallback) {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(raw.trim())));
        } catch (Exception e) {
            return fallback;
        }
    }

    private void submitQuiz(String lessonId, int score, boolean passed, TextView result) {
        withBearer(bearer -> ApiClient.getLmsService()
                .submitQuiz(bearer, lessonId, new LmsModels.QuizBody(score, passed))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.QuizEnvelope> call,
                                           Response<LmsModels.QuizEnvelope> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            showWorkResult(result, "Quiz saved (" + score + "%)");
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

    private void submitAssignment(String lessonId, String text, TextView result) {
        withBearer(bearer -> ApiClient.getLmsService()
                .submitAssignment(bearer, new LmsModels.SubmissionBody(lessonId, text))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.SubmissionEnvelope> call,
                                           Response<LmsModels.SubmissionEnvelope> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            showWorkResult(result, "Assignment submitted");
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
        hidePdf();
        setPlayerHeight(180);
        if (playerFrame != null) playerFrame.setVisibility(View.VISIBLE);
        tvPlayerPlaceholder.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoURI(Uri.parse(url));
        videoView.setOnPreparedListener(mp -> {
            mp.start();
            lastWatchReport = 0;
            mainHandler.removeCallbacks(watchTick);
            mainHandler.post(watchTick);
        });
        videoView.setOnCompletionListener(mp -> reportVideoPosition(true));
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show();
            return true;
        });
        videoView.start();
    }

    private void openPdf(String url, int lastPage) {
        hideVideo();
        setPlayerHeight(480);
        if (playerFrame != null) playerFrame.setVisibility(View.VISIBLE);
        pdfView.setVisibility(View.VISIBLE);
        tvPlayerPlaceholder.setVisibility(View.VISIBLE);
        tvPlayerPlaceholder.setText("Opening PDF…");
        pdfMaxPage = Math.max(1, lastPage);
        String lessonId = activeLessonId;
        pdfExec.execute(() -> {
            try {
                okhttp3.Response res = http.newCall(new Request.Builder().url(url).build()).execute();
                if (!res.isSuccessful() || res.body() == null) throw new java.io.IOException("pdf");
                File out = new File(getCacheDir(), "lesson-" + lessonId + ".pdf");
                try (InputStream in = res.body().byteStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0) os.write(buf, 0, n);
                }
                res.close();
                int start = Math.max(0, lastPage - 1);
                mainHandler.post(() -> showPdfFile(out, start, lessonId));
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvPlayerPlaceholder.setText("Could not open PDF");
                    Toast.makeText(this, "Could not open PDF", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showPdfFile(File file, int startPage, String lessonId) {
        if (isFinishing() || !lessonId.equals(activeLessonId) || pdfView == null) return;
        tvPlayerPlaceholder.setVisibility(View.GONE);
        pdfView.fromFile(file)
                .defaultPage(startPage)
                .onPageChange((page, pageCount) -> {
                    int current = page + 1;
                    if (current > pdfMaxPage) pdfMaxPage = current;
                    int pct = pageCount <= 0 ? 0 : Math.min(100, Math.round(pdfMaxPage * 100f / pageCount));
                    long now = System.currentTimeMillis();
                    if (now - lastPdfReport < 1200 && pct < 100) return;
                    lastPdfReport = now;
                    LmsModels.ProgressBody body = new LmsModels.ProgressBody(true, (float) pct, null);
                    body.watchSeconds = pdfMaxPage;
                    patchProgress(lessonId, body);
                })
                .load();
    }

    private void onWatchTick() {
        reportVideoPosition(false);
        if (videoView != null && videoView.isPlaying()) {
            mainHandler.postDelayed(watchTick, 4000);
        }
    }

    private void reportVideoPosition(boolean force) {
        if (videoView == null || TextUtils.isEmpty(activeLessonId)) return;
        int duration = videoView.getDuration();
        if (duration <= 0) return;
        int position = videoView.getCurrentPosition();
        int pct = Math.min(100, Math.round(position * 100f / duration));
        long now = System.currentTimeMillis();
        if (!force && now - lastWatchReport < 4000 && pct < 95) return;
        lastWatchReport = now;
        LmsModels.ProgressBody body = new LmsModels.ProgressBody(true, (float) pct, null);
        body.watchSeconds = position / 1000;
        body.watchPct = (float) pct;
        patchProgress(activeLessonId, body);
    }

    private void reportOpened(String lessonId) {
        patchProgress(lessonId, new LmsModels.ProgressBody(true, null, null));
    }

    private void patchProgress(String lessonId, LmsModels.ProgressBody body) {
        if (TextUtils.isEmpty(lessonId) || body == null) return;
        withBearer(bearer -> ApiClient.getLmsService()
                .patchProgress(bearer, lessonId, body)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.ProgressEnvelope> call,
                                           Response<LmsModels.ProgressEnvelope> response) {}

                    @Override
                    public void onFailure(Call<LmsModels.ProgressEnvelope> call, Throwable t) {}
                }));
    }

    private void showWorkResult(TextView result, String message) {
        if (result != null) result.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setPlayerHeight(int heightDp) {
        if (playerFrame == null) return;
        ViewGroup.LayoutParams lp = playerFrame.getLayoutParams();
        lp.height = dp(heightDp);
        playerFrame.setLayoutParams(lp);
    }

    private void hidePdf() {
        if (pdfView != null) {
            try {
                pdfView.recycle();
            } catch (Exception ignored) {
                /* viewer was not loaded */
            }
            pdfView.setVisibility(View.GONE);
        }
    }

    private void hideVideo() {
        stopWatchLoop();
        if (videoView != null) {
            videoView.stopPlayback();
            videoView.setVisibility(View.GONE);
        }
    }

    private void hidePlayers() {
        hidePdf();
        hideVideo();
    }

    private void stopWatchLoop() {
        mainHandler.removeCallbacks(watchTick);
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
                            enrolledOnTrack = true;
                            btnEnroll.setText("Continue learning");
                            if (btnLeaveCourse != null) btnLeaveCourse.setVisibility(View.VISIBLE);
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

    private void confirmLeaveCourse() {
        if (TextUtils.isEmpty(trackId) || !enrolledOnTrack) return;
        new AlertDialog.Builder(this)
                .setTitle("Leave this course?")
                .setMessage("You’ll be removed from the course. Your lesson progress is kept if you re-enroll later.")
                .setPositiveButton("Leave course", (d, w) -> unenroll())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unenroll() {
        if (TextUtils.isEmpty(trackId)) return;
        withBearer(bearer -> ApiClient.getLmsService()
                .unenroll(bearer, trackId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            enrolledOnTrack = false;
                            resumeLesson = null;
                            if (btnLeaveCourse != null) btnLeaveCourse.setVisibility(View.GONE);
                            btnEnroll.setText(trackPrice != null && trackPrice.isPaid
                                    ? "Unlock · " + formatPrice(trackPrice)
                                    : "Enroll");
                            Toast.makeText(TrackLearnActivity.this, "Left course", Toast.LENGTH_SHORT).show();
                            loadTrack();
                            return;
                        }
                        Toast.makeText(TrackLearnActivity.this,
                                "Couldn’t leave course (" + response.code() + ")",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(TrackLearnActivity.this,
                                "Couldn’t leave course", Toast.LENGTH_SHORT).show();
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
        if (videoView != null && videoView.isPlaying()) {
            reportVideoPosition(true);
            videoView.pause();
        }
        stopWatchLoop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopWatchLoop();
        pdfExec.shutdownNow();
        if (pdfView != null) pdfView.recycle();
        super.onDestroy();
    }

    private interface BearerCallback {
        void onToken(String bearer);
    }
}
