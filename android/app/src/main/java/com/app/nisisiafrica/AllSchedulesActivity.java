package com.app.nisisiafrica;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.EventAdapter;
import com.app.nisisiafrica.Utils.EventSeatReservation;
import com.app.nisisiafrica.ViewModel.EventViewModel;
import com.app.nisisiafrica.ViewModel.EventViewModelFactory;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.Repository.EventRepository;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class AllSchedulesActivity extends AppCompatActivity {

    private EventViewModel eventViewModel;
    private EventAdapter adapter;
    private TextView tvEmpty;
    private TextView tvResultCount;
    private String filter = "upcoming";
    private UserData userData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_schedules);
        View headerContent = findViewById(R.id.headerContent);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
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

        tvEmpty = findViewById(R.id.tvEmpty);
        tvResultCount = findViewById(R.id.tvResultCount);
        RecyclerView rv = findViewById(R.id.rvSchedules);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter(false);
        adapter.setOnReserveClick(event ->
                EventSeatReservation.show(this, event, userData, e -> load()));
        rv.setAdapter(adapter);

        new ViewModelProvider(this).get(UserViewModel.class)
                .getUserData()
                .observe(this, data -> userData = data);

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
            int n = empty ? 0 : events.size();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            tvEmpty.setText(filter.equals("past") ? "No past events" : "No upcoming events");
            if (tvResultCount != null) {
                String label = filter.equals("past") ? "past" : "upcoming";
                tvResultCount.setText(n == 1 ? "1 " + label : n + " " + label);
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
