package com.app.nisisiafrica;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.EventAdapter;
import com.app.nisisiafrica.ViewModel.EventViewModel;
import com.app.nisisiafrica.ViewModel.EventViewModelFactory;
import com.app.nisisiafrica.data.Repository.EventRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class AllSchedulesActivity extends AppCompatActivity {

    private EventViewModel eventViewModel;
    private EventAdapter adapter;
    private TextView tvEmpty;
    private String filter = "upcoming";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_schedules);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvSchedules);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter(false);
        rv.setAdapter(adapter);

        eventViewModel = new ViewModelProvider(this, new EventViewModelFactory(new EventRepository()))
                .get(EventViewModel.class);

        MaterialButtonToggleGroup group = findViewById(R.id.filterGroup);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            filter = checkedId == R.id.btnPast ? "past" : "upcoming";
            load();
        });

        load();
    }

    private void load() {
        eventViewModel.fetchHubEvents(filter, events -> {
            adapter.submitList(events);
            boolean empty = events == null || events.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            tvEmpty.setText(filter.equals("past") ? "No past events" : "No upcoming events");
        });
    }
}
