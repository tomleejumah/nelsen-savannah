package com.app.nisisiafrica;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.MentorsAdapter;
import com.app.nisisiafrica.Adapters.MentorsArrayAdapter;
import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import kotlin.Unit;

public class AllMentorsActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORIES = "extra_categories";

    private MentorsAdapter mentorsAdapter;
    private TextView tvMentorCount;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mentors + Admins talk to mentees from Chats — they don't browse/book mentors.
        if (!Roles.browsesMentors()) {
            Toast.makeText(this, "Open Chat to message your mentees", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_mentors);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvMentorCount = findViewById(R.id.tvMentorCount);
        progress = findViewById(R.id.progressMentors);

        RecyclerView recyclerView = findViewById(R.id.rvMentors);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ArrayList<String> categories = getIntent().getStringArrayListExtra(EXTRA_CATEGORIES);
        if (categories != null && !categories.isEmpty()) {
            toolbar.setSubtitle("Matched: " + TextUtils.join(", ", categories));
            loadMatchedMentors(recyclerView, categories);
        } else {
            mentorsAdapter = new MentorsAdapter(true, this);
            recyclerView.setAdapter(mentorsAdapter);
            mentorsAdapter.addLoadStateListener(states -> {
                boolean loading = states.getRefresh() instanceof LoadState.Loading;
                progress.setVisibility(loading ? View.VISIBLE : View.GONE);
                if (!loading) {
                    int count = mentorsAdapter.getItemCount();
                    tvMentorCount.setText(count == 1 ? "1 mentor available"
                            : count + " mentors available");
                }
                return null;
            });
            SharedViewModel sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);
            sharedViewModel.getMentors().observe(this, pagingData ->
                    mentorsAdapter.submitData(getLifecycle(), pagingData));
        }
    }

    private void loadMatchedMentors(RecyclerView recyclerView, List<String> categories) {
        MentorsArrayAdapter adapter = new MentorsArrayAdapter(this);
        recyclerView.setAdapter(adapter);
        progress.setVisibility(View.VISIBLE);
        Set<String> wanted = new HashSet<>();
        for (String c : categories) wanted.add(c.toLowerCase());

        FirebaseDatabase.getInstance().getReference("mentors").get()
                .addOnSuccessListener(snap -> {
                    progress.setVisibility(View.GONE);
                    List<MentorItem> matched = new ArrayList<>();
                    List<MentorItem> rest = new ArrayList<>();
                    for (DataSnapshot child : snap.getChildren()) {
                        MentorItem m = toMentor(child);
                        if (m.getMentorId() == null || m.getMentorId().isEmpty()) {
                            m = new MentorItem(
                                    child.getKey(),
                                    m.getMentorImageUrl(),
                                    m.getMentorName(),
                                    m.getMentorDescription(),
                                    m.getStudentsCount(),
                                    m.getStudentImages(),
                                    m.getBookedDates(),
                                    m.getCategories(),
                                    m.getAverageRating()
                            );
                        }
                        boolean hit = false;
                        if (m.getCategories() != null) {
                            for (String c : m.getCategories()) {
                                if (wanted.contains(c.toLowerCase())) {
                                    hit = true;
                                    break;
                                }
                            }
                        }
                        if (hit) matched.add(m);
                        else rest.add(m);
                    }
                    matched.addAll(rest); // matched first
                    adapter.submit(matched);
                    tvMentorCount.setText(matched.isEmpty()
                            ? "No mentors yet"
                            : matched.size() + " mentors · matches first");
                })
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Couldn't load mentors", Toast.LENGTH_SHORT).show();
                });
    }

    private MentorItem toMentor(DataSnapshot s) {
        List<String> cats = new ArrayList<>();
        Object rawCats = s.child("categories").getValue();
        if (rawCats instanceof List) {
            for (Object o : (List<?>) rawCats) if (o != null) cats.add(o.toString());
        }
        List<String> images = new ArrayList<>();
        Object rawImgs = s.child("studentImages").getValue();
        if (rawImgs instanceof List) {
            for (Object o : (List<?>) rawImgs) if (o != null) images.add(o.toString());
        } else if (rawImgs instanceof java.util.Map) {
            for (Object o : ((java.util.Map<?, ?>) rawImgs).values())
                if (o != null) images.add(o.toString());
        }
        return new MentorItem(
                s.child("mentorId").getValue(String.class) != null
                        ? s.child("mentorId").getValue(String.class) : s.getKey(),
                s.child("mentorImageUrl").getValue(String.class),
                s.child("mentorName").getValue(String.class),
                s.child("mentorDescription").getValue(String.class),
                s.child("studentsCount").getValue(String.class),
                images,
                null,
                cats,
                s.child("averageRating").getValue(Double.class)
        );
    }
}
