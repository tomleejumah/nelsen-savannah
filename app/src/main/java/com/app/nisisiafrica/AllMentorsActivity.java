package com.app.nisisiafrica;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.google.android.material.appbar.MaterialToolbar;

public class AllMentorsActivity extends AppCompatActivity {

    private MentorsAdapter mentorsAdapter;
    private TextView tvMentorCount;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
