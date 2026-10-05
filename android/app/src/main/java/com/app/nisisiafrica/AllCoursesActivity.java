package com.app.nisisiafrica;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.Repository.LmsCacheBridge;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kotlin.Unit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Full course catalog with MentUI cards + search.
 * Tap opens {@link TrackLearnActivity}.
 */
public class AllCoursesActivity extends AppCompatActivity {

    public static final String EXTRA_SCHOOL_ID = "schoolId";
    public static final String EXTRA_SCHOOL_NAME = "schoolName";

    private final List<CourseItem> allCourses = new ArrayList<>();
    private final Set<String> enrolledTrackIds = new HashSet<>();
    private CourseCardAdapter listAdapter;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tvResultCount;
    private TextView tvEmptyTitle;
    private TextView tvEmptyHint;
    private ProgressBar catalogProgress;
    private View emptyState;
    private com.google.android.material.button.MaterialButton btnApplySchool;
    private TextView tvApplyPending;
    private String query = "";
    private String schoolIdFilter = "";
    private String schoolNameFilter = "";
    /** null = unknown/loading, true = active member, false = not, "pending" handled separately */
    private Boolean schoolMemberActive = null;
    private boolean schoolJoinPending = false;
    private LmsCacheBridge offlineCache;
    private com.google.android.material.button.MaterialButton btnRetryCourses;
    private PagingDataAdapter<CourseItem, RecyclerView.ViewHolder> pagingBridge;
    private boolean catalogLoading = true;
    private boolean catalogRequestComplete = false;
    private boolean catalogFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_courses);
        offlineCache = new LmsCacheBridge(getApplicationContext());
        View headerContent = findViewById(R.id.headerContent);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Wash draws under status bar; only header + list respect insets.
            headerContent.setPadding(
                    headerContent.getPaddingLeft(),
                    bars.top + dp(12),
                    headerContent.getPaddingRight(),
                    headerContent.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        WindowInsetsControllerCompat barsCtrl =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (barsCtrl != null) barsCtrl.setAppearanceLightStatusBars(true);

        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        tvResultCount = findViewById(R.id.tvResultCount);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        catalogProgress = findViewById(R.id.catalogProgress);
        emptyState = findViewById(R.id.emptyState);
        btnRetryCourses = findViewById(R.id.btnRetryCourses);
        btnRetryCourses.setOnClickListener(v -> retryCatalog());
        btnApplySchool = findViewById(R.id.btnApplySchool);
        tvApplyPending = findViewById(R.id.tvApplyPending);

        schoolIdFilter = getIntent().getStringExtra(EXTRA_SCHOOL_ID);
        if (schoolIdFilter == null) schoolIdFilter = "";
        schoolNameFilter = getIntent().getStringExtra(EXTRA_SCHOOL_NAME);
        if (schoolNameFilter == null) schoolNameFilter = "";

        TextView tvTitle = findViewById(R.id.tvScreenTitle);
        TextView tvSubtitle = findViewById(R.id.tvScreenSubtitle);
        if (tvTitle != null && !schoolIdFilter.isEmpty()) {
            tvTitle.setOnLongClickListener(v -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                String url = "https://nelsen-savannah.co.ke/schools/" + android.net.Uri.encode(schoolIdFilter);
                String label = !schoolNameFilter.isEmpty() ? schoolNameFilter + "\n" : "";
                share.putExtra(Intent.EXTRA_TEXT, label + url);
                startActivity(Intent.createChooser(share, "Share school"));
                return true;
            });
        }
        if (tvTitle != null) {
            if (!schoolNameFilter.isEmpty()) {
                tvTitle.setText(schoolNameFilter);
            } else {
                String shell = Roles.lmsShell();
                if (Roles.SHELL_STUDENT.equals(shell)) {
                    tvTitle.setText(R.string.courses_all);
                } else {
                    tvTitle.setText(Roles.lmsShellLabel() + " · Courses");
                }
            }
        }
        if (tvSubtitle != null && !schoolIdFilter.isEmpty()) {
            tvSubtitle.setText(R.string.courses_from_school);
            tvSubtitle.setOnClickListener(null);
        } else if (tvSubtitle != null && !Roles.SHELL_STUDENT.equals(Roles.lmsShell())) {
            tvSubtitle.setText("Tap here for teach board (queue · mark · assign)");
            tvSubtitle.setOnClickListener(v ->
                    startActivity(new Intent(this, MentorBoardActivity.class)));
        } else if (tvSubtitle != null) {
            tvSubtitle.setText("Tap for assigned coursework inbox");
            tvSubtitle.setOnClickListener(v -> showAssignmentsInbox());
        }

        if (btnApplySchool != null) {
            btnApplySchool.setOnClickListener(v -> applyToSchool());
        }

        RecyclerView rv = findViewById(R.id.rvCourses);
        rv.setLayoutManager(new LinearLayoutManager(this));
        listAdapter = new CourseCardAdapter();
        rv.setAdapter(listAdapter);

        loadMyEnrollments();
        if (!schoolIdFilter.isEmpty()) {
            refreshSchoolMembership();
            loadTracksForSchool(schoolIdFilter);
        } else {
            // Invisible paging bridge — LMS/Firebase returns one page; we filter in-memory.
            pagingBridge =
                    new PagingDataAdapter<CourseItem, RecyclerView.ViewHolder>(DIFF) {
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            return new RecyclerView.ViewHolder(new View(parent.getContext())) {};
                        }

                        @Override
                        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
                    };
            pagingBridge.addLoadStateListener(state -> {
                LoadState refresh = state.getRefresh();
                if (refresh instanceof LoadState.Loading) {
                    catalogLoading = true;
                    catalogFailed = false;
                    applyFilter();
                } else if (refresh instanceof LoadState.Error) {
                    catalogLoading = false;
                    catalogRequestComplete = true;
                    catalogFailed = true;
                    applyFilter();
                } else if (refresh instanceof LoadState.NotLoading) {
                    catalogLoading = false;
                    catalogRequestComplete = true;
                    catalogFailed = false;
                    allCourses.clear();
                    for (CourseItem c : pagingBridge.snapshot()) {
                        if (c != null) allCourses.add(c);
                    }
                    applyFilter();
                }
                return Unit.INSTANCE;
            });

            SharedViewModel vm = new ViewModelProvider(this).get(SharedViewModel.class);
            vm.getCourses().observe(this, data -> pagingBridge.submitData(getLifecycle(), data));
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                query = s != null ? s.toString().trim() : "";
                btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));
    }

    private CourseItem courseItem(LmsModels.TrackCard card) {
        if (card == null) return null;
        String id = card.courseId != null ? card.courseId : card.trackId;
        if (TextUtils.isEmpty(id)) return null;
        if (card.enrolled) enrolledTrackIds.add(id);
        return new CourseItem(id,
                card.tutorId != null ? card.tutorId : "",
                card.courseImageUrl != null ? card.courseImageUrl : "",
                card.tutorAvatarUrl != null ? card.tutorAvatarUrl : "",
                card.tutorName != null ? card.tutorName : "",
                card.courseTitle != null ? card.courseTitle : "",
                card.durationString(), card.lessonsString(),
                card.courseLink != null ? card.courseLink : "",
                card.isLiked, card.programSlug != null ? card.programSlug : "");
    }

    private void loadTracksForSchool(String schoolId) {
        catalogLoading = true;
        catalogRequestComplete = false;
        catalogFailed = false;
        applyFilter();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvResultCount.setText(R.string.courses_sign_in_required);
            return;
        }
        if (offlineCache != null) {
            offlineCache.tracks(user.getUid(), schoolId, cached -> {
                if (cached != null && !cached.isEmpty()) {
                    allCourses.clear();
                    for (LmsModels.TrackCard card : cached) {
                        CourseItem item = courseItem(card);
                        if (item != null) allCourses.add(item);
                    }
                    applyFilter();
                }
                return Unit.INSTANCE;
            });
        }
        user.getIdToken(false).addOnSuccessListener(r ->
                ApiClient.getLmsService()
                        .tracks("Bearer " + r.getToken(), schoolId)
                        .enqueue(new Callback<>() {
                            @Override
                            public void onResponse(@NonNull Call<LmsModels.TracksEnvelope> call,
                                                   @NonNull Response<LmsModels.TracksEnvelope> response) {
                                catalogLoading = false;
                                catalogRequestComplete = true;
                                LmsModels.TracksEnvelope body = response.body();
                                if (response.isSuccessful() && body != null && body.ok
                                        && body.data != null && body.data.tracks != null) {
                                    catalogFailed = false;
                                    allCourses.clear();
                                    if (offlineCache != null) {
                                        offlineCache.saveTracks(user.getUid(), body.data.tracks, schoolId);
                                    }
                                    for (LmsModels.TrackCard card : body.data.tracks) {
                                        CourseItem item = courseItem(card);
                                        if (item != null) allCourses.add(item);
                                    }
                                } else {
                                    catalogFailed = true;
                                }
                                applyFilter();
                            }

                            @Override
                            public void onFailure(@NonNull Call<LmsModels.TracksEnvelope> call,
                                                  @NonNull Throwable t) {
                                catalogLoading = false;
                                catalogRequestComplete = true;
                                catalogFailed = true;
                                applyFilter();
                            }
                        }))
                .addOnFailureListener(e -> {
                    catalogLoading = false;
                    catalogRequestComplete = true;
                    catalogFailed = true;
                    applyFilter();
                });
    }

    private void loadMyEnrollments() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(r ->
                ApiClient.getLmsService()
                        .myEnrollments("Bearer " + r.getToken())
                        .enqueue(new Callback<>() {
                            @Override
                            public void onResponse(
                                    @NonNull Call<LmsModels.EnrollmentListEnvelope> call,
                                    @NonNull Response<LmsModels.EnrollmentListEnvelope> response) {
                                LmsModels.EnrollmentListEnvelope body = response.body();
                                if (response.isSuccessful() && body != null && body.ok
                                        && body.data != null && body.data.enrollments != null) {
                                    for (LmsModels.Enrollment e : body.data.enrollments) {
                                        if (e != null && e.trackId != null) {
                                            enrolledTrackIds.add(e.trackId);
                                        }
                                    }
                                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                                }
                            }

                            @Override
                            public void onFailure(
                                    @NonNull Call<LmsModels.EnrollmentListEnvelope> call,
                                    @NonNull Throwable t) {}
                        }));
    }

    private void refreshSchoolMembership() {
        if (schoolIdFilter.isEmpty()) {
            bindApplyUi();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(r ->
                ApiClient.getLmsService().me("Bearer " + r.getToken()).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<LmsModels.MeEnvelope> call,
                                           @NonNull Response<LmsModels.MeEnvelope> response) {
                        schoolMemberActive = false;
                        schoolJoinPending = false;
                        LmsModels.MeEnvelope body = response.body();
                        if (response.isSuccessful() && body != null && body.ok && body.data != null) {
                            Object mem = body.data.get("memberships");
                            if (mem instanceof List<?> list) {
                                for (Object o : list) {
                                    if (!(o instanceof java.util.Map<?, ?> m)) continue;
                                    String sid = String.valueOf(m.get("schoolId") != null
                                            ? m.get("schoolId") : "");
                                    if (!schoolIdFilter.equals(sid)) continue;
                                    String status = String.valueOf(m.get("status") != null
                                            ? m.get("status") : "");
                                    if ("active".equals(status)) {
                                        schoolMemberActive = true;
                                        schoolJoinPending = false;
                                        break;
                                    }
                                    if ("applied".equals(status) || "invited".equals(status)) {
                                        schoolJoinPending = true;
                                    }
                                }
                            }
                        }
                        bindApplyUi();
                        if (listAdapter != null) listAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(@NonNull Call<LmsModels.MeEnvelope> call,
                                          @NonNull Throwable t) {
                        schoolMemberActive = false;
                        bindApplyUi();
                    }
                }));
    }

    private void bindApplyUi() {
        if (btnApplySchool == null || tvApplyPending == null) return;
        if (schoolIdFilter.isEmpty()) {
            btnApplySchool.setVisibility(View.GONE);
            tvApplyPending.setVisibility(View.GONE);
            return;
        }
        if (Boolean.TRUE.equals(schoolMemberActive)) {
            btnApplySchool.setVisibility(View.GONE);
            tvApplyPending.setVisibility(View.GONE);
        } else if (schoolJoinPending) {
            btnApplySchool.setVisibility(View.GONE);
            tvApplyPending.setVisibility(View.VISIBLE);
        } else {
            btnApplySchool.setVisibility(View.VISIBLE);
            tvApplyPending.setVisibility(View.GONE);
        }
    }

    private void applyToSchool() {
        if (schoolIdFilter.isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show();
            return;
        }
        btnApplySchool.setEnabled(false);
        String name = user.getDisplayName() != null ? user.getDisplayName() : "";
        user.getIdToken(false).addOnSuccessListener(r ->
                ApiClient.getLmsService()
                        .applyToJoinSchool(
                                "Bearer " + r.getToken(),
                                schoolIdFilter,
                                new LmsModels.JoinSchoolBody(name))
                        .enqueue(new Callback<>() {
                            @Override
                            public void onResponse(@NonNull Call<LmsModels.MapEnvelope> call,
                                                   @NonNull Response<LmsModels.MapEnvelope> response) {
                                btnApplySchool.setEnabled(true);
                                if (response.isSuccessful()) {
                                    Toast.makeText(AllCoursesActivity.this,
                                            R.string.school_apply_sent, Toast.LENGTH_SHORT).show();
                                    schoolJoinPending = true;
                                    schoolMemberActive = false;
                                    bindApplyUi();
                                } else {
                                    Toast.makeText(AllCoursesActivity.this,
                                            "Could not apply (" + response.code() + ")",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<LmsModels.MapEnvelope> call,
                                                  @NonNull Throwable t) {
                                btnApplySchool.setEnabled(true);
                                Toast.makeText(AllCoursesActivity.this,
                                        "Could not apply", Toast.LENGTH_SHORT).show();
                            }
                        }))
                .addOnFailureListener(e -> {
                    btnApplySchool.setEnabled(true);
                    Toast.makeText(this, "Auth failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void enrollTrack(CourseItem c) {
        if (!schoolIdFilter.isEmpty() && !Boolean.TRUE.equals(schoolMemberActive)) {
            Toast.makeText(this,
                    schoolJoinPending
                            ? getString(R.string.school_apply_pending)
                            : "Apply to this school first",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show();
            return;
        }
        user.getIdToken(false).addOnSuccessListener(r ->
                ApiClient.getLmsService()
                        .enroll("Bearer " + r.getToken(), new LmsModels.EnrollBody(c.getCourseId()))
                        .enqueue(new Callback<>() {
                            @Override
                            public void onResponse(
                                    @NonNull Call<LmsModels.EnrollmentEnvelope> call,
                                    @NonNull Response<LmsModels.EnrollmentEnvelope> response) {
                                if (response.isSuccessful()) {
                                    enrolledTrackIds.add(c.getCourseId());
                                    listAdapter.notifyDataSetChanged();
                                    Toast.makeText(AllCoursesActivity.this,
                                            "Enrolled", Toast.LENGTH_SHORT).show();
                                    openTrack(c);
                                } else if (response.code() == 403) {
                                    Toast.makeText(AllCoursesActivity.this,
                                            "Apply to this school first",
                                            Toast.LENGTH_SHORT).show();
                                    refreshSchoolMembership();
                                } else {
                                    Toast.makeText(AllCoursesActivity.this,
                                            "Enroll failed (" + response.code() + ")",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(
                                    @NonNull Call<LmsModels.EnrollmentEnvelope> call,
                                    @NonNull Throwable t) {
                                Toast.makeText(AllCoursesActivity.this,
                                        "Enroll failed", Toast.LENGTH_SHORT).show();
                            }
                        }));
    }

    private void openTrack(CourseItem c) {
        String tutor = c.getTutorName();
        boolean showTutor = !TextUtils.isEmpty(tutor)
                && !tutor.trim().equalsIgnoreCase("Nelsen Savannah")
                && !tutor.trim().equalsIgnoreCase("Nelsen Savannah Innovation Hub");
        Intent learn = new Intent(AllCoursesActivity.this, TrackLearnActivity.class);
        learn.putExtra(TrackLearnActivity.EXTRA_TRACK_ID, c.getCourseId());
        learn.putExtra(TrackLearnActivity.EXTRA_TITLE, c.getCourseTitle());
        learn.putExtra(TrackLearnActivity.EXTRA_DESC,
                showTutor ? getString(R.string.courses_with_tutor, tutor) : "");
        learn.putExtra(TrackLearnActivity.EXTRA_FALLBACK_URL, c.getCourseLink());
        if (showTutor && !TextUtils.isEmpty(c.getTutorId())) {
            learn.putExtra(TrackLearnActivity.EXTRA_TUTOR_ID, c.getTutorId());
        }
        if (showTutor) {
            learn.putExtra(TrackLearnActivity.EXTRA_TUTOR_NAME, tutor);
        }
        if (showTutor && !TextUtils.isEmpty(c.getTutorAvatarUrl())) {
            learn.putExtra(TrackLearnActivity.EXTRA_TUTOR_AVATAR, c.getTutorAvatarUrl());
        }
        startActivity(learn);
    }

    private void applyFilter() {
        List<CourseItem> filtered = new ArrayList<>();
        String q = query.toLowerCase(Locale.getDefault());
        for (CourseItem c : allCourses) {
            if (q.isEmpty() || matches(c, q)) filtered.add(c);
        }
        listAdapter.submit(filtered);
        int n = filtered.size();

        boolean firstLoad = catalogLoading && allCourses.isEmpty();
        catalogProgress.setVisibility(firstLoad ? View.VISIBLE : View.GONE);
        btnRetryCourses.setVisibility(View.GONE);

        if (firstLoad) {
            tvResultCount.setText(R.string.courses_loading);
            emptyState.setVisibility(View.GONE);
            return;
        }
        if (catalogFailed && allCourses.isEmpty()) {
            tvResultCount.setText(R.string.courses_load_failed);
            tvEmptyTitle.setText("Could not load courses");
            tvEmptyHint.setText("Check your connection and try again.");
            btnRetryCourses.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.VISIBLE);
            return;
        }
        if (catalogRequestComplete && allCourses.isEmpty()) {
            tvResultCount.setText("0 tracks");
            tvEmptyTitle.setText("No courses yet");
            tvEmptyHint.setText("There are no published courses here yet.");
            emptyState.setVisibility(View.VISIBLE);
            return;
        }
        if (n == 0) {
            tvResultCount.setText("0 matches");
            tvEmptyTitle.setText("No matches");
            tvEmptyHint.setText("Try another title or tutor name.");
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        String count = n == 1 ? "1 track" : n + " tracks";
        tvResultCount.setText(catalogLoading ? count + " · Refreshing…" : count);
        emptyState.setVisibility(View.GONE);
    }

    private void retryCatalog() {
        if (!schoolIdFilter.isEmpty()) {
            loadTracksForSchool(schoolIdFilter);
            return;
        }
        catalogLoading = true;
        catalogFailed = false;
        catalogRequestComplete = false;
        applyFilter();
        if (pagingBridge != null) pagingBridge.retry();
    }

    private static boolean matches(CourseItem c, String q) {
        return contains(c.getCourseTitle(), q)
                || contains(c.getTutorName(), q)
                || contains(c.getCourseId(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(q);
    }

    private static final DiffUtil.ItemCallback<CourseItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull CourseItem a, @NonNull CourseItem b) {
            return a.getCourseId().equals(b.getCourseId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull CourseItem a, @NonNull CourseItem b) {
            return a.equals(b);
        }
    };

    private class CourseCardAdapter extends RecyclerView.Adapter<CourseCardAdapter.VH> {
        private final List<CourseItem> items = new ArrayList<>();

        void submit(List<CourseItem> next) {
            items.clear();
            items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_course_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView tile;
            final TextView initials;
            final TextView title;
            final TextView meta;
            final TextView lessonsChip;
            final TextView durationChip;
            final View tutorClickRow;
            final de.hdodenhof.circleimageview.CircleImageView tutorAvatar;
            final com.google.android.material.button.MaterialButton btnEnrollCourse;

            VH(@NonNull View itemView) {
                super(itemView);
                tile = itemView.findViewById(R.id.ivCourseTile);
                initials = itemView.findViewById(R.id.tvCourseInitials);
                title = itemView.findViewById(R.id.tvCourseTitle);
                meta = itemView.findViewById(R.id.tvCourseMeta);
                lessonsChip = itemView.findViewById(R.id.tvLessonsChip);
                durationChip = itemView.findViewById(R.id.tvDurationChip);
                tutorClickRow = itemView.findViewById(R.id.tutorClickRow);
                tutorAvatar = itemView.findViewById(R.id.ivTutorAvatar);
                btnEnrollCourse = itemView.findViewById(R.id.btnEnrollCourse);
            }

            void bind(CourseItem c) {
                title.setText(c.getCourseTitle());
                String tutor = c.getTutorName();
                boolean showTutor = !TextUtils.isEmpty(tutor)
                        && !tutor.trim().equalsIgnoreCase("Nelsen Savannah")
                        && !tutor.trim().equalsIgnoreCase("Nelsen Savannah Innovation Hub");
                if (showTutor) {
                    tutorClickRow.setVisibility(View.VISIBLE);
                    meta.setText(tutor);
                    String avatar = c.getTutorAvatarUrl();
                    if (!TextUtils.isEmpty(avatar)) {
                        Glide.with(itemView).load(avatar).placeholder(R.drawable.ic_person).into(tutorAvatar);
                    } else {
                        tutorAvatar.setImageResource(R.drawable.ic_person);
                    }
                } else {
                    tutorClickRow.setVisibility(View.GONE);
                    meta.setText("");
                }

                String lessons = c.getLessons() != null ? c.getLessons().trim() : "";
                String duration = c.getDuration() != null ? c.getDuration().trim() : "";
                if (lessons.isEmpty()) {
                    lessonsChip.setVisibility(View.GONE);
                } else {
                    lessonsChip.setVisibility(View.VISIBLE);
                    lessonsChip.setText(lessons + " lessons");
                }
                if (duration.isEmpty()) {
                    durationChip.setVisibility(View.GONE);
                } else {
                    durationChip.setVisibility(View.VISIBLE);
                    durationChip.setText(duration + " h");
                }

                String cover = c.getCourseImageUrl();
                initials.setText(initialsFor(c.getCourseTitle()));
                if (!TextUtils.isEmpty(cover)) {
                    initials.setVisibility(View.GONE);
                    tile.setVisibility(View.VISIBLE);
                    tile.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                    tile.setClipToOutline(true);
                    Glide.with(itemView).load(cover).centerCrop().into(tile);
                } else {
                    tile.setImageDrawable(null);
                    tile.setVisibility(View.INVISIBLE);
                    initials.setVisibility(View.VISIBLE);
                }

                boolean enrolled = enrolledTrackIds.contains(c.getCourseId());
                if (btnEnrollCourse != null) {
                    btnEnrollCourse.setVisibility(View.VISIBLE);
                    btnEnrollCourse.setText(enrolled
                            ? R.string.enroll_continue
                            : R.string.enroll_now);
                    btnEnrollCourse.setOnClickListener(v -> {
                        if (enrolled) openTrack(c);
                        else enrollTrack(c);
                    });
                }

                itemView.setOnClickListener(v -> openTrack(c));
                tutorClickRow.setOnClickListener(v -> {
                    String tid = c.getTutorId();
                    if (!showTutor || TextUtils.isEmpty(tid)) {
                        Toast.makeText(AllCoursesActivity.this, "Tutor profile unavailable", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent profile = new Intent(AllCoursesActivity.this, ProfileActivity.class);
                    profile.putExtra(Constants.IS_MENTOR, true);
                    profile.putExtra(Constants.MENTOR_ID, tid);
                    if (!TextUtils.isEmpty(tutor)) {
                        profile.putExtra(Constants.MENTOR_NAME, tutor);
                    }
                    startActivity(profile);
                });
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String initialsFor(String title) {
        if (TextUtils.isEmpty(title)) return "?";
        String[] parts = title.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.getDefault());
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.getDefault());
    }

    private void showAssignmentsInbox() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show();
            return;
        }
        user.getIdToken(false).addOnSuccessListener(r ->
                ApiClient.getLmsService()
                        .myAssignments("Bearer " + r.getToken())
                        .enqueue(new Callback<>() {
                            @Override
                            public void onResponse(Call<LmsModels.AssignmentsEnvelope> call,
                                                   Response<LmsModels.AssignmentsEnvelope> response) {
                                LmsModels.AssignmentsEnvelope body = response.body();
                                if (!response.isSuccessful() || body == null || !body.ok
                                        || body.data == null || body.data.assignments == null
                                        || body.data.assignments.isEmpty()) {
                                    Toast.makeText(AllCoursesActivity.this,
                                            "No assigned work", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                CharSequence[] lines = new CharSequence[body.data.assignments.size()];
                                for (int i = 0; i < body.data.assignments.size(); i++) {
                                    LmsModels.AssignmentDto a = body.data.assignments.get(i);
                                    lines[i] = a.title + (a.trackId != null ? " · " + a.trackId : "");
                                }
                                new AlertDialog.Builder(AllCoursesActivity.this)
                                        .setTitle("Assigned to you")
                                        .setItems(lines, null)
                                        .setPositiveButton("OK", null)
                                        .show();
                            }

                            @Override
                            public void onFailure(Call<LmsModels.AssignmentsEnvelope> call, Throwable t) {
                                Toast.makeText(AllCoursesActivity.this,
                                        "Could not load assignments", Toast.LENGTH_SHORT).show();
                            }
                        }));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
