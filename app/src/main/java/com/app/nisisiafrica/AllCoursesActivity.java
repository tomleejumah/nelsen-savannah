package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * Full vertical course list (Home "See all"). Same course data as the home rail.
 * Tap opens {@link TrackLearnActivity} (Course track stub until Chunk 4).
 */
public class AllCoursesActivity extends AppCompatActivity {

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

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvCourses);
        rv.setLayoutManager(new LinearLayoutManager(this));
        CourseRowAdapter adapter = new CourseRowAdapter();
        rv.setAdapter(adapter);

        SharedViewModel vm = new ViewModelProvider(this).get(SharedViewModel.class);
        vm.getCourses().observe(this, data -> adapter.submitData(getLifecycle(), data));
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

    private class CourseRowAdapter extends PagingDataAdapter<CourseItem, CourseRowAdapter.VH> {
        CourseRowAdapter() {
            super(DIFF);
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
            CourseItem item = getItem(position);
            if (item != null) holder.bind(item);
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView tile;
            final TextView title;
            final TextView meta;
            final TextView stats;

            VH(@NonNull View itemView) {
                super(itemView);
                tile = itemView.findViewById(R.id.ivCourseTile);
                title = itemView.findViewById(R.id.tvCourseTitle);
                meta = itemView.findViewById(R.id.tvCourseMeta);
                stats = itemView.findViewById(R.id.tvCourseStats);
            }

            void bind(CourseItem c) {
                title.setText(c.getCourseTitle());
                meta.setText(c.getTutorName() != null ? c.getTutorName() : "");
                String lessons = c.getLessons() != null ? c.getLessons() : "";
                String duration = c.getDuration() != null ? c.getDuration() : "";
                stats.setText((lessons.isEmpty() ? "" : lessons + " lessons")
                        + (duration.isEmpty() ? "" : " · " + duration + " h"));
                if (c.getCourseImageUrl() != null && !c.getCourseImageUrl().isEmpty()) {
                    Glide.with(itemView).load(c.getCourseImageUrl()).into(tile);
                    tile.setColorFilter(null);
                }
                itemView.setOnClickListener(v -> {
                    Intent learn = new Intent(AllCoursesActivity.this, TrackLearnActivity.class);
                    learn.putExtra(TrackLearnActivity.EXTRA_TRACK_ID, c.getCourseId());
                    learn.putExtra(TrackLearnActivity.EXTRA_TITLE, c.getCourseTitle());
                    learn.putExtra(TrackLearnActivity.EXTRA_DESC,
                            c.getTutorName() != null ? "with " + c.getTutorName() : "");
                    learn.putExtra(TrackLearnActivity.EXTRA_FALLBACK_URL, c.getCourseLink());
                    startActivity(learn);
                });
            }
        }
    }
}
