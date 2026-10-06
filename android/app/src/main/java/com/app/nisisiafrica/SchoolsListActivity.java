package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.bumptech.glide.Glide;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * School picker for mentees — Enroll Schools.
 * Lists membership schools first, then Explore others from GET /lms/schools/catalog.
 * Membership rows show enrolled track counts (“n tracks in progress”).
 */
public class SchoolsListActivity extends AppCompatActivity {

    public static final String EXTRA_EXPLORE = "explore";
    public static final String EXTRA_TABLET_SHELL = "tabletShell";

    private final List<Row> allRows = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    /** schoolId → enrolled track count for this user. */
    private final Map<String, Integer> tracksInProgressBySchool = new HashMap<>();
    private SchoolsAdapter adapter;
    private TextView tvEmpty;
    private TextView tvHint;
    private TextView tvSchoolCount;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private SwipeRefreshLayout swipeRefresh;
    private boolean loadingSchools = false;
    private boolean tabletShell = false;
    private View tabletDetail;
    private TextView tabletDetailTitle;
    private TextView tabletDetailHint;
    private RecyclerView tabletCourses;
    private TabletCourseAdapter tabletCourseAdapter;
    private String tabletSchoolId = "";
    private String tabletSchoolName = "";
    private final List<CourseItem> tabletCourseRows = new ArrayList<>();
    private final Set<String> tabletEnrolledTrackIds = new HashSet<>();
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_schools_list);
        View headerContent = findViewById(R.id.headerContent);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            headerContent.setPadding(
                    headerContent.getPaddingLeft(),
                    bars.top + Math.round(12 * getResources().getDisplayMetrics().density),
                    headerContent.getPaddingRight(),
                    headerContent.getPaddingBottom());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        WindowInsetsControllerCompat barsCtrl =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (barsCtrl != null) barsCtrl.setAppearanceLightStatusBars(true);

        tvEmpty = findViewById(R.id.tvSchoolsEmpty);
        tvHint = findViewById(R.id.tvSchoolsHint);
        tvSchoolCount = findViewById(R.id.tvSchoolCount);
        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        swipeRefresh = findViewById(R.id.swipeRefreshSchools);
        tabletShell = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        tabletDetail = findViewById(R.id.tabletSchoolDetail);
        tabletDetailTitle = findViewById(R.id.tvTabletSchoolTitle);
        tabletDetailHint = findViewById(R.id.tvTabletSchoolHint);
        tabletCourses = findViewById(R.id.rvTabletCourses);
        if (tabletShell && tabletCourses != null) {
            tabletCourses.setLayoutManager(new LinearLayoutManager(this));
            tabletCourseAdapter = new TabletCourseAdapter();
            tabletCourses.setAdapter(tabletCourseAdapter);
            loadTabletEnrollments();
        }

        RecyclerView rv = findViewById(R.id.rvSchools);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SchoolsAdapter();
        rv.setAdapter(adapter);
        swipeRefresh.setOnRefreshListener(this::load);

        setupSearch();
        load();
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s != null ? s.toString().trim() : "";
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                etSearch.setText("");
                etSearch.clearFocus();
            });
        }
    }

    private void applyFilter() {
        rows.clear();
        if (query.isEmpty()) {
            rows.addAll(allRows);
        } else {
            String q = query.toLowerCase(Locale.getDefault());
            for (Row row : allRows) {
                if (row.name.toLowerCase(Locale.getDefault()).contains(q)
                        || row.schoolId.toLowerCase(Locale.getDefault()).contains(q)) {
                    rows.add(row);
                }
            }
        }
        adapter.notifyDataSetChanged();
        boolean empty = rows.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            tvEmpty.setText(query.isEmpty()
                    ? R.string.schools_empty
                    : R.string.schools_empty_search);
        }
        updateSchoolCount();
    }

    private void load() {
        if (loadingSchools) return;
        loadingSchools = true;
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finishSchoolRefresh();
            Toast.makeText(this, "Sign in to browse schools", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (allRows.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Loading schools…");
        }
        user.getIdToken(false)
                .addOnSuccessListener(tokenResult -> {
                    String token = tokenResult.getToken();
                    if (TextUtils.isEmpty(token)) {
                        showLoadError("Could not authenticate. Check your connection and try again.");
                        return;
                    }
                    String bearer = "Bearer " + token;
                    // Do not block school discovery on the optional enrollment-count request.
                    loadMemberships(bearer);
                    loadEnrolledTrackCounts(bearer, this::applyFilter);
                })
                .addOnFailureListener(e ->
                        showLoadError("Could not authenticate. Check your connection and try again."));
    }

    private void finishSchoolRefresh() {
        loadingSchools = false;
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    }

    private void showLoadError(String message) {
        finishSchoolRefresh();
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(message);
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void loadEnrolledTrackCounts(String bearer, Runnable next) {
        ApiClient.getLmsService().myEnrollments(bearer).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LmsModels.EnrollmentListEnvelope> call,
                                   @NonNull Response<LmsModels.EnrollmentListEnvelope> response) {
                tracksInProgressBySchool.clear();
                LmsModels.EnrollmentListEnvelope body = response.body();
                if (response.isSuccessful() && body != null && body.ok
                        && body.data != null && body.data.enrollments != null) {
                    for (LmsModels.Enrollment e : body.data.enrollments) {
                        if (e == null || TextUtils.isEmpty(e.schoolId)) continue;
                        Integer cur = tracksInProgressBySchool.get(e.schoolId);
                        tracksInProgressBySchool.put(e.schoolId, (cur == null ? 0 : cur) + 1);
                    }
                }
                next.run();
            }

            @Override
            public void onFailure(@NonNull Call<LmsModels.EnrollmentListEnvelope> call,
                                  @NonNull Throwable t) {
                tracksInProgressBySchool.clear();
                next.run();
            }
        });
    }

    private void loadMemberships(String bearer) {
        ApiClient.getLmsService().me(bearer).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LmsModels.MeEnvelope> call,
                                   @NonNull Response<LmsModels.MeEnvelope> response) {
                Set<String> mine = new HashSet<>();
                List<Row> mineRows = new ArrayList<>();
                LmsModels.MeEnvelope body = response.body();
                if (response.isSuccessful() && body != null && body.ok && body.data != null) {
                    Object mem = body.data.get("memberships");
                    if (mem instanceof List<?> list) {
                        for (Object o : list) {
                            if (!(o instanceof Map<?, ?> m)) continue;
                            Object status = m.get("status");
                            if (status != null && !"active".equals(String.valueOf(status))) continue;
                            String sid = str(m.get("schoolId"));
                            String name = str(m.get("schoolName"));
                            if (sid.isEmpty()) continue;
                            mine.add(sid);
                            mineRows.add(new Row(sid, name.isEmpty() ? sid : name, true));
                        }
                    }
                }
                loadCatalog(bearer, mine, mineRows);
            }

            @Override
            public void onFailure(@NonNull Call<LmsModels.MeEnvelope> call, @NonNull Throwable t) {
                loadCatalog(bearer, new HashSet<>(), new ArrayList<>());
            }
        });
    }

    private void loadCatalog(String bearer, Set<String> mineIds, List<Row> mineRows) {
        ApiClient.getLmsService().schoolsCatalog(bearer).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LmsModels.SchoolsEnvelope> call,
                                   @NonNull Response<LmsModels.SchoolsEnvelope> response) {
                allRows.clear();
                allRows.addAll(mineRows);
                LmsModels.SchoolsEnvelope body = response.body();
                if (response.isSuccessful() && body != null && body.ok
                        && body.data != null && body.data.schools != null) {
                    for (LmsModels.SchoolDto s : body.data.schools) {
                        if (s == null || s.schoolId == null || s.schoolId.isEmpty()) continue;
                        if (mineIds.contains(s.schoolId)) continue;
                        allRows.add(new Row(s.schoolId, s.name != null ? s.name : s.schoolId, false));
                    }
                }
                if (mineRows.isEmpty()) {
                    tvHint.setText("Choose a school to see what they offer. Enrolling joins that school.");
                } else {
                    tvHint.setText("Your schools first — Explore others below.");
                }
                finishSchoolRefresh();
                applyFilter();
                if (tabletShell) {
                    String requestedSchool = getIntent().getStringExtra(AllCoursesActivity.EXTRA_SCHOOL_ID);
                    if (!TextUtils.isEmpty(requestedSchool)) {
                        for (Row row : allRows) {
                            if (requestedSchool.equals(row.schoolId)) {
                                getIntent().removeExtra(AllCoursesActivity.EXTRA_SCHOOL_ID);
                                openSchoolInPane(row);
                                break;
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<LmsModels.SchoolsEnvelope> call, @NonNull Throwable t) {
                allRows.clear();
                allRows.addAll(mineRows);
                finishSchoolRefresh();
                applyFilter();
                if (mineRows.isEmpty()) {
                    showLoadError("Could not load schools. Check your connection and try again.");
                } else {
                    Toast.makeText(SchoolsListActivity.this,
                            "Could not refresh school catalog", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateSchoolCount() {
        if (tvSchoolCount == null) return;
        int n = rows.size();
        tvSchoolCount.setText(n == 1
                ? getString(R.string.schools_count_one)
                : getString(R.string.schools_count_label, n));
    }

    private String tracksMeta(String schoolId, boolean mine) {
        Integer n = tracksInProgressBySchool.get(schoolId);
        int count = n == null ? 0 : n;
        if (count > 0) {
            return count == 1
                    ? getString(R.string.school_one_track_in_progress)
                    : getString(R.string.school_tracks_in_progress, count);
        }
        return mine ? "Your school" : "Explore";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private void openSchool(Row row) {
        if (tabletShell && tabletDetail != null) {
            openSchoolInPane(row);
            return;
        }
        Intent i = new Intent(this, AllCoursesActivity.class);
        i.putExtra(AllCoursesActivity.EXTRA_SCHOOL_ID, row.schoolId);
        i.putExtra(AllCoursesActivity.EXTRA_SCHOOL_NAME, row.name);
        startActivity(i);
    }

    private void openSchoolInPane(Row row) {
        tabletSchoolId = row.schoolId;
        tabletSchoolName = row.name;
        tabletDetail.setVisibility(View.VISIBLE);
        tabletDetailTitle.setText(row.name);
        tabletDetailHint.setText("Loading courses…");
        tabletCourseRows.clear();
        tabletCourseAdapter.notifyDataSetChanged();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(token ->
                ApiClient.getLmsService().tracks("Bearer " + token.getToken(), row.schoolId)
                        .enqueue(new Callback<>() {
                            @Override public void onResponse(@NonNull Call<LmsModels.TracksEnvelope> call,
                                                            @NonNull Response<LmsModels.TracksEnvelope> response) {
                                tabletCourseRows.clear();
                                LmsModels.TracksEnvelope body = response.body();
                                if (response.isSuccessful() && body != null && body.ok
                                        && body.data != null && body.data.tracks != null) {
                                    for (LmsModels.TrackCard card : body.data.tracks) {
                                        CourseItem item = tabletCourseItem(card);
                                        if (item != null) tabletCourseRows.add(item);
                                    }
                                }
                                tabletDetailHint.setText(tabletCourseRows.isEmpty()
                                        ? "No published courses yet"
                                        : tabletCourseRows.size() + (tabletCourseRows.size() == 1 ? " course" : " courses"));
                                tabletCourseAdapter.notifyDataSetChanged();
                            }
                            @Override public void onFailure(@NonNull Call<LmsModels.TracksEnvelope> call,
                                                           @NonNull Throwable t) {
                                tabletDetailHint.setText("Could not load courses. Tap the school to retry.");
                            }
                        }));
    }

    private void loadTabletEnrollments() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(token ->
                ApiClient.getLmsService().myEnrollments("Bearer " + token.getToken())
                        .enqueue(new Callback<>() {
                            @Override public void onResponse(@NonNull Call<LmsModels.EnrollmentListEnvelope> call,
                                                            @NonNull Response<LmsModels.EnrollmentListEnvelope> response) {
                                tabletEnrolledTrackIds.clear();
                                LmsModels.EnrollmentListEnvelope body = response.body();
                                if (response.isSuccessful() && body != null && body.ok
                                        && body.data != null && body.data.enrollments != null) {
                                    for (LmsModels.Enrollment e : body.data.enrollments) {
                                        if (e != null && !TextUtils.isEmpty(e.trackId)) tabletEnrolledTrackIds.add(e.trackId);
                                    }
                                }
                                if (tabletCourseAdapter != null) tabletCourseAdapter.notifyDataSetChanged();
                            }
                            @Override public void onFailure(@NonNull Call<LmsModels.EnrollmentListEnvelope> call,
                                                           @NonNull Throwable t) {}
                        }));
    }

    private CourseItem tabletCourseItem(LmsModels.TrackCard card) {
        if (card == null) return null;
        String id = card.courseId != null ? card.courseId : card.trackId;
        if (TextUtils.isEmpty(id)) return null;
        if (card.enrolled) tabletEnrolledTrackIds.add(id);
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

    private void openTabletCourse(CourseItem course) {
        if (!tabletEnrolledTrackIds.contains(course.getCourseId())) {
            Toast.makeText(this, "Enroll in this course to open it", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent learn = new Intent(this, TrackLearnActivity.class);
        learn.putExtra(TrackLearnActivity.EXTRA_TRACK_ID, course.getCourseId());
        learn.putExtra(TrackLearnActivity.EXTRA_TITLE, course.getCourseTitle());
        learn.putExtra(TrackLearnActivity.EXTRA_DESC, course.getTutorName());
        learn.putExtra(TrackLearnActivity.EXTRA_FALLBACK_URL, course.getCourseLink());
        startActivity(learn);
    }

    private class TabletCourseAdapter extends RecyclerView.Adapter<TabletCourseAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_course_row, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int position) { h.bind(tabletCourseRows.get(position)); }
        @Override public int getItemCount() { return tabletCourseRows.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, meta, lessons, duration, initials;
            final android.widget.ImageView tile;
            final com.google.android.material.button.MaterialButton action;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.tvCourseTitle);
                meta = v.findViewById(R.id.tvCourseMeta);
                lessons = v.findViewById(R.id.tvLessonsChip);
                duration = v.findViewById(R.id.tvDurationChip);
                initials = v.findViewById(R.id.tvCourseInitials);
                tile = v.findViewById(R.id.ivCourseTile);
                action = v.findViewById(R.id.btnEnrollCourse);
            }
            void bind(CourseItem course) {
                title.setText(course.getCourseTitle());
                meta.setText(course.getTutorName());
                String ls = course.getLessons() == null ? "" : course.getLessons().trim();
                lessons.setVisibility(ls.isEmpty() ? View.GONE : View.VISIBLE);
                lessons.setText(ls.isEmpty() ? "" : ls + " lessons");
                String dur = course.getDuration() == null ? "" : course.getDuration().trim();
                duration.setVisibility(dur.isEmpty() ? View.GONE : View.VISIBLE);
                duration.setText(dur.isEmpty() ? "" : dur + " h");
                initials.setText(course.getCourseTitle() == null || course.getCourseTitle().isEmpty()
                        ? "?" : course.getCourseTitle().substring(0, 1).toUpperCase(Locale.getDefault()));
                if (!TextUtils.isEmpty(course.getCourseImageUrl())) {
                    tile.setVisibility(View.VISIBLE);
                    initials.setVisibility(View.GONE);
                    Glide.with(itemView).load(course.getCourseImageUrl()).centerCrop().into(tile);
                } else {
                    tile.setVisibility(View.INVISIBLE);
                    initials.setVisibility(View.VISIBLE);
                }
                boolean enrolled = tabletEnrolledTrackIds.contains(course.getCourseId());
                action.setText(enrolled ? "Continue" : "Enroll");
                action.setOnClickListener(v -> {
                    if (enrolled) openTabletCourse(course);
                    else {
                        Intent school = new Intent(SchoolsListActivity.this, AllCoursesActivity.class);
                        school.putExtra(AllCoursesActivity.EXTRA_SCHOOL_ID, tabletSchoolId);
                        school.putExtra(AllCoursesActivity.EXTRA_SCHOOL_NAME, tabletSchoolName);
                        startActivity(school);
                    }
                });
                itemView.setOnClickListener(v -> openTabletCourse(course));
            }
        }
    }

    private static final class Row {
        final String schoolId;
        final String name;
        final boolean mine;

        Row(String schoolId, String name, boolean mine) {
            this.schoolId = schoolId;
            this.name = name;
            this.mine = mine;
        }
    }

    private class SchoolsAdapter extends RecyclerView.Adapter<SchoolsAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_school_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Row row = rows.get(position);
            holder.name.setText(row.name);
            holder.meta.setText(tracksMeta(row.schoolId, row.mine));
            holder.itemView.setOnClickListener(v -> openSchool(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView meta;

            VH(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvSchoolName);
                meta = itemView.findViewById(R.id.tvSchoolMeta);
            }
        }
    }
}
