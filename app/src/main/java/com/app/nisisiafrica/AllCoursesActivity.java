package com.app.nisisiafrica;

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
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;

/**
 * Full course catalog with MentUI cards + search.
 * Tap opens {@link TrackLearnActivity}.
 */
public class AllCoursesActivity extends AppCompatActivity {

    private final List<CourseItem> allCourses = new ArrayList<>();
    private CourseCardAdapter listAdapter;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tvResultCount;
    private View emptyState;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_courses);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        tvResultCount = findViewById(R.id.tvResultCount);
        emptyState = findViewById(R.id.emptyState);

        RecyclerView rv = findViewById(R.id.rvCourses);
        rv.setLayoutManager(new LinearLayoutManager(this));
        listAdapter = new CourseCardAdapter();
        rv.setAdapter(listAdapter);

        // Invisible paging bridge — LMS/Firebase returns one page; we filter in-memory.
        PagingDataAdapter<CourseItem, RecyclerView.ViewHolder> bridge =
                new PagingDataAdapter<CourseItem, RecyclerView.ViewHolder>(DIFF) {
                    @NonNull
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        return new RecyclerView.ViewHolder(new View(parent.getContext())) {};
                    }

                    @Override
                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
                };
        bridge.addLoadStateListener(state -> {
            if (state.getRefresh() instanceof LoadState.NotLoading) {
                allCourses.clear();
                for (CourseItem c : bridge.snapshot()) {
                    if (c != null) allCourses.add(c);
                }
                applyFilter();
            }
            return Unit.INSTANCE;
        });

        SharedViewModel vm = new ViewModelProvider(this).get(SharedViewModel.class);
        vm.getCourses().observe(this, data -> bridge.submitData(getLifecycle(), data));

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

    private void applyFilter() {
        List<CourseItem> filtered = new ArrayList<>();
        String q = query.toLowerCase(Locale.getDefault());
        for (CourseItem c : allCourses) {
            if (q.isEmpty() || matches(c, q)) filtered.add(c);
        }
        listAdapter.submit(filtered);
        int n = filtered.size();
        if (allCourses.isEmpty()) {
            tvResultCount.setText("Loading tracks…");
            emptyState.setVisibility(View.GONE);
        } else if (n == 0) {
            tvResultCount.setText("0 matches");
            emptyState.setVisibility(View.VISIBLE);
        } else {
            tvResultCount.setText(n == 1 ? "1 track" : n + " tracks");
            emptyState.setVisibility(View.GONE);
        }
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

            VH(@NonNull View itemView) {
                super(itemView);
                tile = itemView.findViewById(R.id.ivCourseTile);
                initials = itemView.findViewById(R.id.tvCourseInitials);
                title = itemView.findViewById(R.id.tvCourseTitle);
                meta = itemView.findViewById(R.id.tvCourseMeta);
                lessonsChip = itemView.findViewById(R.id.tvLessonsChip);
                durationChip = itemView.findViewById(R.id.tvDurationChip);
            }

            void bind(CourseItem c) {
                title.setText(c.getCourseTitle());
                String tutor = c.getTutorName();
                meta.setText(TextUtils.isEmpty(tutor) ? "Nelsen Savannah" : "with " + tutor);

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

                itemView.setOnClickListener(v -> {
                    Intent learn = new Intent(AllCoursesActivity.this, TrackLearnActivity.class);
                    learn.putExtra(TrackLearnActivity.EXTRA_TRACK_ID, c.getCourseId());
                    learn.putExtra(TrackLearnActivity.EXTRA_TITLE, c.getCourseTitle());
                    learn.putExtra(TrackLearnActivity.EXTRA_DESC,
                            !TextUtils.isEmpty(tutor) ? "with " + tutor : "");
                    learn.putExtra(TrackLearnActivity.EXTRA_FALLBACK_URL, c.getCourseLink());
                    startActivity(learn);
                });
            }
        }
    }

    private static String initialsFor(String title) {
        if (TextUtils.isEmpty(title)) return "?";
        String[] parts = title.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.getDefault());
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.getDefault());
    }
}
