package com.app.nisisiafrica;

import android.content.Intent;
import android.media.MediaPlayer;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
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
import androidx.core.view.WindowInsetsControllerCompat;

import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.github.barteksc.pdfviewer.PDFView;
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
 * Dedicated chapter learning page — PDF/video outside NestedScrollView so pages swipe,
 * and progress is reported via PATCH /lms/progress/:lessonId.
 */
public class ChapterLearnActivity extends AppCompatActivity {

    public static final String EXTRA_TRACK_ID = "extra_track_id";
    public static final String EXTRA_MODULE_ID = "extra_module_id";
    public static final String EXTRA_CHAPTER_TITLE = "extra_chapter_title";
    public static final String EXTRA_CHAPTER_DOES = "extra_chapter_does";
    public static final String EXTRA_CHAPTER_INDEX = "extra_chapter_index";
    public static final String EXTRA_LESSON_ID = "extra_lesson_id";
    public static final String EXTRA_RELEASE_AT = "extra_release_at";
    public static final String EXTRA_DUE_AT = "extra_due_at";

    private ProgressBar progress;
    private FrameLayout playerFrame;
    private VideoView videoView;
    private PDFView pdfView;
    private TextView tvPlayerPlaceholder;
    private TextView tvPageHint;
    private TextView tvChapterDoes;
    private LinearLayout lessonsContainer;
    private LinearLayout lessonPanel;
    private TextView tvScreenTitle;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService pdfExec = Executors.newSingleThreadExecutor();
    private final OkHttpClient http = new OkHttpClient();
    private final Runnable watchTick = this::onWatchTick;

    private String trackId;
    private String moduleId;
    private String openLessonId;
    private String activeLessonId;
    private long releaseAt;
    private long dueAt;
    private int pdfMaxPage;
    private long lastPdfReport;
    private long lastWatchReport;
    private final Map<String, Float> lessonPercents = new LinkedHashMap<>();
    private final List<LmsModels.LessonDto> lessons = new ArrayList<>();
    private LmsModels.CohortRunDto cohortRun;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chapter_learn);
        View headerContent = findViewById(R.id.headerContent);
        View lessonsSheet = findViewById(R.id.lessonsSheet);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            headerContent.setPadding(
                    headerContent.getPaddingLeft(),
                    bars.top + dp(12),
                    headerContent.getPaddingRight(),
                    headerContent.getPaddingBottom());
            if (lessonsSheet != null) {
                lessonsSheet.setPadding(
                        lessonsSheet.getPaddingLeft(),
                        lessonsSheet.getPaddingTop(),
                        lessonsSheet.getPaddingRight(),
                        bars.bottom + dp(12));
            }
            v.setPadding(bars.left, 0, bars.right, 0);
            return insets;
        });
        WindowInsetsControllerCompat barsCtrl =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (barsCtrl != null) barsCtrl.setAppearanceLightStatusBars(true);

        trackId = getIntent().getStringExtra(EXTRA_TRACK_ID);
        moduleId = getIntent().getStringExtra(EXTRA_MODULE_ID);
        openLessonId = getIntent().getStringExtra(EXTRA_LESSON_ID);
        releaseAt = getIntent().getLongExtra(EXTRA_RELEASE_AT, 0L);
        dueAt = getIntent().getLongExtra(EXTRA_DUE_AT, 0L);
        String title = getIntent().getStringExtra(EXTRA_CHAPTER_TITLE);
        String does = getIntent().getStringExtra(EXTRA_CHAPTER_DOES);
        int chapterIndex = getIntent().getIntExtra(EXTRA_CHAPTER_INDEX, 1);

        tvScreenTitle = findViewById(R.id.tvScreenTitle);
        tvScreenTitle.setText(!TextUtils.isEmpty(title) ? title : ("Chapter " + chapterIndex));

        progress = findViewById(R.id.progress);
        playerFrame = findViewById(R.id.playerFrame);
        videoView = findViewById(R.id.videoView);
        pdfView = findViewById(R.id.pdfView);
        tvPlayerPlaceholder = findViewById(R.id.tvPlayerPlaceholder);
        tvPageHint = findViewById(R.id.tvPageHint);
        tvChapterDoes = findViewById(R.id.tvChapterDoes);
        lessonsContainer = findViewById(R.id.lessonsContainer);
        lessonPanel = findViewById(R.id.lessonPanel);

        if (!TextUtils.isEmpty(does)) {
            tvChapterDoes.setVisibility(View.VISIBLE);
            tvChapterDoes.setText(does);
        }

        if (TextUtils.isEmpty(moduleId)) {
            Toast.makeText(this, "Chapter unavailable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadProgressThenModule();
    }

    private void loadProgressThenModule() {
        progress.setVisibility(View.VISIBLE);
        if (TextUtils.isEmpty(trackId)) {
            loadModule();
            return;
        }
        withBearer(bearer -> ApiClient.getLmsService().myProgress(bearer, trackId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.ProgressMapEnvelope> call,
                                           Response<LmsModels.ProgressMapEnvelope> response) {
                        LmsModels.ProgressMapEnvelope body = response.body();
                        if (response.isSuccessful() && body != null && body.ok && body.data != null
                                && body.data.byLessonId != null) {
                            lessonPercents.clear();
                            for (Map.Entry<String, Object> e : body.data.byLessonId.entrySet()) {
                                if (!(e.getValue() instanceof Map)) continue;
                                Object pct = ((Map<?, ?>) e.getValue()).get("lessonPercent");
                                if (pct instanceof Number) {
                                    lessonPercents.put(e.getKey(), ((Number) pct).floatValue());
                                }
                            }
                        }
                        loadModule();
                    }

                    @Override
                    public void onFailure(Call<LmsModels.ProgressMapEnvelope> call, Throwable t) {
                        loadModule();
                    }
                }));
        // Also fetch track for cohort milestones.
        withBearer(bearer -> ApiClient.getLmsService().track(bearer, trackId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.TrackDetailEnvelope> call,
                                           Response<LmsModels.TrackDetailEnvelope> response) {
                        LmsModels.TrackDetailEnvelope body = response.body();
                        if (response.isSuccessful() && body != null && body.ok && body.data != null) {
                            cohortRun = body.data.cohortRun;
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.TrackDetailEnvelope> call, Throwable t) {}
                }));
    }

    private void loadModule() {
        withBearer(bearer -> ApiClient.getLmsService().module(bearer, moduleId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.ModuleDetailEnvelope> call,
                                           Response<LmsModels.ModuleDetailEnvelope> response) {
                        progress.setVisibility(View.GONE);
                        LmsModels.ModuleDetailEnvelope body = response.body();
                        if (!response.isSuccessful() || body == null || !body.ok
                                || body.data == null || body.data.lessons == null) {
                            Toast.makeText(ChapterLearnActivity.this,
                                    "Could not load chapter", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (body.data.module != null) {
                            if (!TextUtils.isEmpty(body.data.module.title)) {
                                tvScreenTitle.setText(body.data.module.title);
                            }
                            if (body.data.module.releaseAt != null) {
                                releaseAt = body.data.module.releaseAt;
                            }
                            if (body.data.module.dueAt != null) {
                                dueAt = body.data.module.dueAt;
                            }
                        }
                        renderLessons(body.data.lessons);
                    }

                    @Override
                    public void onFailure(Call<LmsModels.ModuleDetailEnvelope> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(ChapterLearnActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void renderLessons(List<LmsModels.LessonDto> list) {
        lessons.clear();
        lessonsContainer.removeAllViews();
        long now = System.currentTimeMillis();
        boolean hasWindow = releaseAt > 0 && dueAt > 0;
        boolean chapterReleased = !hasWindow || releaseAt <= now;
        boolean chapterExpired = hasWindow && dueAt < now;
        LayoutInflater inflater = LayoutInflater.from(this);
        LmsModels.LessonDto autoOpen = null;
        for (int i = 0; i < list.size(); i++) {
            LmsModels.LessonDto lesson = list.get(i);
            Float prog = lessonPercents.get(lesson.lessonId);
            if (prog != null) lesson.lessonPercent = prog;
            lessons.add(lesson);
            LmsModels.MilestoneDto mile = milestoneFor(lesson.lessonId);
            boolean canOpen = chapterReleased && (mile == null || mile.available || mile.completed);
            View row = inflater.inflate(R.layout.item_chapter_lesson, lessonsContainer, false);
            TextView index = row.findViewById(R.id.tvLessonIndex);
            TextView title = row.findViewById(R.id.tvLessonTitle);
            TextView meta = row.findViewById(R.id.tvLessonMeta);
            TextView percent = row.findViewById(R.id.tvLessonPercent);
            View thumb = row.findViewById(R.id.ivLessonThumb);
            index.setText((i + 1) + ".");
            title.setText(lesson.title != null ? lesson.title : "Lesson");
            String type = lesson.type != null ? lesson.type : "";
            if ("read".equals(type)) type = "text";
            if (!type.isEmpty()) {
                type = type.substring(0, 1).toUpperCase(Locale.US) + type.substring(1);
            }
            StringBuilder metaText = new StringBuilder(type);
            if (lesson.estimatedMinutes > 0) {
                if (metaText.length() > 0) metaText.append(" · ");
                metaText.append(lesson.estimatedMinutes).append(" min");
            }
            if (!canOpen) {
                if (metaText.length() > 0) metaText.append(" · ");
                metaText.append("locked");
            } else if (chapterExpired) {
                if (metaText.length() > 0) metaText.append(" · ");
                metaText.append("Past Due");
            }
            meta.setText(metaText.toString());
            percent.setText(Math.round(lesson.lessonPercent) + "%");
            boolean pdf = isPdfLesson(lesson, lesson.contentUrl);
            thumb.setVisibility(pdf ? View.VISIBLE : View.GONE);
            row.setAlpha(canOpen ? 1f : 0.7f);
            row.setSelected(false);
            boolean openable = canOpen;
            row.setOnClickListener(v -> {
                if (openable) openLesson(lesson);
                else Toast.makeText(this, "This lesson is locked", Toast.LENGTH_SHORT).show();
            });
            lessonsContainer.addView(row);
            if (openable) {
                if (!TextUtils.isEmpty(openLessonId)
                        && openLessonId.equals(lesson.lessonId)) {
                    autoOpen = lesson;
                } else if (autoOpen == null && lesson.lessonPercent < 100f) {
                    autoOpen = lesson;
                }
            }
        }
        if (autoOpen == null && !lessons.isEmpty()) {
            autoOpen = lessons.get(0);
        }
        if (autoOpen != null) openLesson(autoOpen);
    }

    private LmsModels.MilestoneDto milestoneFor(String lessonId) {
        if (cohortRun == null || cohortRun.milestones == null || TextUtils.isEmpty(lessonId)) {
            return null;
        }
        for (LmsModels.MilestoneDto m : cohortRun.milestones) {
            if (lessonId.equals(m.lessonId)) return m;
        }
        return null;
    }

    private void openLesson(LmsModels.LessonDto lesson) {
        if (lesson == null || TextUtils.isEmpty(lesson.lessonId)) return;
        highlightLesson(lesson.lessonId);
        withBearer(bearer -> ApiClient.getLmsService().lesson(bearer, lesson.lessonId)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.LessonDetailEnvelope> call,
                                           Response<LmsModels.LessonDetailEnvelope> response) {
                        LmsModels.LessonDetailEnvelope body = response.body();
                        if (response.isSuccessful() && body != null && body.ok
                                && body.data != null && body.data.lesson != null) {
                            presentLesson(body.data.lesson);
                        } else {
                            Toast.makeText(ChapterLearnActivity.this,
                                    "Lesson not ready yet", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.LessonDetailEnvelope> call, Throwable t) {
                        Toast.makeText(ChapterLearnActivity.this,
                                "Could not load lesson", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void highlightLesson(String lessonId) {
        for (int i = 0; i < lessonsContainer.getChildCount(); i++) {
            View row = lessonsContainer.getChildAt(i);
            boolean active = i < lessons.size() && lessonId.equals(lessons.get(i).lessonId);
            row.setAlpha(active ? 1f : 0.85f);
            row.setBackgroundColor(active
                    ? getColor(R.color.surface_2)
                    : android.graphics.Color.TRANSPARENT);
        }
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
            tvPlayerPlaceholder.setVisibility(View.VISIBLE);
            tvPlayerPlaceholder.setText("No media for this lesson");
            if (tvPageHint != null) tvPageHint.setVisibility(View.GONE);
        }
        bindLessonWork(lesson);
        reportOpened(lesson.lessonId);
    }

    private static boolean isPdfLesson(LmsModels.LessonDto lesson, String url) {
        if (lesson != null && (lesson.isPdf || "pdf".equalsIgnoreCase(lesson.type))) return true;
        return url != null && url.matches("(?i).*\\.pdf(\\?.*)?$");
    }

    private void playUrl(String url) {
        hidePdf();
        tvPlayerPlaceholder.setVisibility(View.GONE);
        if (tvPageHint != null) tvPageHint.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);
        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setVideoURI(Uri.parse(url));
        videoView.setOnPreparedListener((MediaPlayer mp) -> {
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
        pdfView.setVisibility(View.VISIBLE);
        tvPlayerPlaceholder.setVisibility(View.VISIBLE);
        tvPlayerPlaceholder.setText("Opening PDF…");
        pdfMaxPage = Math.max(1, lastPage);
        String lessonId = activeLessonId;
        pdfExec.execute(() -> {
            try {
                okhttp3.Response res = http.newCall(new Request.Builder().url(url).build()).execute();
                if (!res.isSuccessful() || res.body() == null) throw new java.io.IOException("pdf");
                File out = new File(getCacheDir(), "chapter-lesson-" + lessonId + ".pdf");
                try (InputStream in = res.body().byteStream();
                     OutputStream os = new FileOutputStream(out)) {
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
        if (tvPageHint != null) {
            tvPageHint.setVisibility(View.VISIBLE);
            tvPageHint.setText("Swipe to turn pages");
        }
        pdfView.fromFile(file)
                .defaultPage(startPage)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .spacing(8)
                .onPageChange((page, pageCount) -> {
                    int current = page + 1;
                    if (current > pdfMaxPage) pdfMaxPage = current;
                    if (tvPageHint != null && pageCount > 0) {
                        tvPageHint.setText("Page " + current + " / " + pageCount);
                    }
                    int pct = pageCount <= 0 ? 0
                            : Math.min(100, Math.round(pdfMaxPage * 100f / pageCount));
                    long now = System.currentTimeMillis();
                    if (now - lastPdfReport < 1200 && pct < 100) return;
                    lastPdfReport = now;
                    LmsModels.ProgressBody body = new LmsModels.ProgressBody(true, (float) pct, null);
                    body.watchSeconds = pdfMaxPage;
                    patchProgress(lessonId, body);
                    updateLessonPercentUi(lessonId, pct);
                })
                .onError(t -> Toast.makeText(this, "PDF error", Toast.LENGTH_SHORT).show())
                .load();
    }

    private void updateLessonPercentUi(String lessonId, int pct) {
        lessonPercents.put(lessonId, (float) pct);
        for (int i = 0; i < lessons.size(); i++) {
            if (!lessonId.equals(lessons.get(i).lessonId)) continue;
            lessons.get(i).lessonPercent = pct;
            if (i < lessonsContainer.getChildCount()) {
                TextView percent = lessonsContainer.getChildAt(i).findViewById(R.id.tvLessonPercent);
                if (percent != null) percent.setText(pct + "%");
            }
            break;
        }
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
        updateLessonPercentUi(activeLessonId, pct);
        if (tvPageHint != null) {
            tvPageHint.setVisibility(View.VISIBLE);
            tvPageHint.setText("Watched " + pct + "%");
        }
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
                submitQuiz(lesson.lessonId, LmsModels.QuizBody.answers(answers), result);
            });
            lessonPanel.addView(submit);
            return;
        }
        lessonPanel.addView(sectionLabel(
                quiz != null && quiz.prompt != null ? quiz.prompt : "Quiz"));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        if (quiz != null && quiz.options != null) {
            for (LmsModels.QuizOptionDto option : quiz.options) {
                RadioButton rb = new RadioButton(this);
                rb.setId(View.generateViewId());
                rb.setText(option.text != null ? option.text : option.id);
                rb.setTag(option.id);
                group.addView(rb);
            }
        }
        lessonPanel.addView(group);
        MaterialButton submit = new MaterialButton(this);
        submit.setText("Submit quiz");
        submit.setOnClickListener(v -> {
            int checked = group.getCheckedRadioButtonId();
            RadioButton rb = group.findViewById(checked);
            if (rb == null || rb.getTag() == null) {
                Toast.makeText(this, "Pick an answer", Toast.LENGTH_SHORT).show();
                return;
            }
            submitQuiz(lesson.lessonId,
                    LmsModels.QuizBody.option(String.valueOf(rb.getTag())), result);
        });
        lessonPanel.addView(submit);
    }

    private void bindAssignment(LmsModels.LessonDto lesson, TextView result) {
        lessonPanel.addView(sectionLabel(
                !TextUtils.isEmpty(lesson.assignmentPrompt)
                        ? lesson.assignmentPrompt
                        : "Your response"));
        EditText input = new EditText(this);
        input.setMinLines(4);
        input.setGravity(android.view.Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Write your answer…");
        lessonPanel.addView(input);
        MaterialButton submit = new MaterialButton(this);
        submit.setText("Submit assignment");
        submit.setOnClickListener(v -> {
            String text = input.getText() != null ? input.getText().toString().trim() : "";
            if (text.isEmpty()) {
                Toast.makeText(this, "Write something first", Toast.LENGTH_SHORT).show();
                return;
            }
            LmsModels.SubmissionBody body = new LmsModels.SubmissionBody(lesson.lessonId, text);
            withBearer(bearer -> ApiClient.getLmsService().submitAssignment(bearer, body)
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(Call<LmsModels.SubmissionEnvelope> call,
                                               Response<LmsModels.SubmissionEnvelope> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().ok) {
                                result.setText("Assignment submitted");
                                Toast.makeText(ChapterLearnActivity.this,
                                        "Assignment submitted", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ChapterLearnActivity.this,
                                        "Submit failed", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<LmsModels.SubmissionEnvelope> call, Throwable t) {
                            Toast.makeText(ChapterLearnActivity.this,
                                    "Submit network error", Toast.LENGTH_SHORT).show();
                        }
                    }));
        });
        lessonPanel.addView(submit);
    }

    private void submitQuiz(String lessonId, LmsModels.QuizBody body, TextView result) {
        withBearer(bearer -> ApiClient.getLmsService().submitQuiz(bearer, lessonId, body)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.QuizEnvelope> call,
                                           Response<LmsModels.QuizEnvelope> response) {
                        LmsModels.QuizEnvelope env = response.body();
                        if (response.isSuccessful() && env != null && env.ok && env.data != null) {
                            String msg = env.data.quizPct >= 80f
                                    ? "Quiz passed"
                                    : "Quiz submitted";
                            result.setText(msg);
                            Toast.makeText(ChapterLearnActivity.this, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ChapterLearnActivity.this,
                                    "Quiz failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LmsModels.QuizEnvelope> call, Throwable t) {
                        Toast.makeText(ChapterLearnActivity.this,
                                "Quiz network error", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.ink));
        tv.setTextSize(14f);
        tv.setPadding(0, dp(8), 0, dp(4));
        return tv;
    }

    private void hidePdf() {
        if (pdfView != null) {
            try {
                pdfView.recycle();
            } catch (Exception ignored) {
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private interface BearerAction {
        void run(String bearer);
    }

    private void withBearer(BearerAction action) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show();
            return;
        }
        user.getIdToken(false).addOnSuccessListener(r ->
                action.run("Bearer " + r.getToken()));
    }

    @Override
    protected void onPause() {
        super.onPause();
        reportVideoPosition(true);
        stopWatchLoop();
    }

    @Override
    protected void onDestroy() {
        stopWatchLoop();
        hidePlayers();
        pdfExec.shutdownNow();
        super.onDestroy();
    }
}
