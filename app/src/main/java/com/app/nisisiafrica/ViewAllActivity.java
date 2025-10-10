package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.app.nisisiafrica.Adapters.CoursesAdapter;
import com.app.nisisiafrica.Adapters.MentorsAdapter;
import com.app.nisisiafrica.ViewModel.SharedViewModel;
import com.google.android.material.appbar.MaterialToolbar;

public class ViewAllActivity extends AppCompatActivity {
    TextView tvTitle;
    private CoursesAdapter coursesAdapter;
    private MentorsAdapter mentorsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_view_all);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        boolean isCourses = intent.getBooleanExtra("isCourses", false);

        SharedViewModel sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        RecyclerView recyclerView = findViewById(R.id.rContents);
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);


        if (isCourses) {
            toolbar.setTitle("Courses");
//            tvTitle.setText("Courses");
            StaggeredGridLayoutManager staggeredGridLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
            recyclerView.setLayoutManager(staggeredGridLayoutManager);
            coursesAdapter = new CoursesAdapter((this));
            recyclerView.setAdapter(coursesAdapter);

            sharedViewModel.getCourses().observe(this, pagingData -> {
                coursesAdapter.submitData(getLifecycle(), pagingData);
            });
        } else {
            toolbar.setTitle("Mentors");
//            tvTitle.setText("Mentors");
            LinearLayoutManager layoutManager1 = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
            recyclerView.setLayoutManager(layoutManager1);
            mentorsAdapter = new MentorsAdapter(true, this);
            recyclerView.setAdapter(mentorsAdapter);

            sharedViewModel.getMentors().observe(this, pagingData -> {
                mentorsAdapter.submitData(getLifecycle(), pagingData);
            });

        }
    }
}