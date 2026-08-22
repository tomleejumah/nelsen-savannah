package com.app.nisisiafrica;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.Worker.EventReminderWorker;
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.Model.ProgrammeItem;
import com.app.nisisiafrica.data.remote.LmsEventsDataSource;
import com.app.nisisiafrica.data.remote.ProgrammesDataSource;
import android.view.View;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateEventActivity extends AppCompatActivity {

    private TextInputEditText etTitle, etDescription, etLocation, etMeetingLink, etSeats, etPrice;
    private MaterialAutoCompleteTextView etProgram;
    private TextView tvDate, tvStart, tvEnd;
    private MaterialButton btnSave, btnModeOnline;
    private TextInputLayout tilLocation, tilMeetingLink;

    private final Calendar dateCal = Calendar.getInstance();
    private boolean dateSet = false;
    private String startTime = "";
    private String endTime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_event);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle = findViewById(R.id.etEventTitle);
        etDescription = findViewById(R.id.etEventDescription);
        etLocation = findViewById(R.id.etEventLocation);
        etMeetingLink = findViewById(R.id.etMeetingLink);
        etProgram = findViewById(R.id.etProgram);
        etSeats = findViewById(R.id.etSeats);
        etPrice = findViewById(R.id.etPrice);
        tvDate = findViewById(R.id.tvDate);
        tvStart = findViewById(R.id.tvStartTime);
        tvEnd = findViewById(R.id.tvEndTime);
        btnSave = findViewById(R.id.btnSaveEvent);
        btnModeOnline = findViewById(R.id.btnModeOnline);
        tilLocation = findViewById(R.id.tilLocation);
        tilMeetingLink = findViewById(R.id.tilMeetingLink);

        ProgrammesDataSource.fetch(programmes -> {
            List<String> titles = new ArrayList<>();
            titles.add(""); // optional blank
            for (ProgrammeItem p : programmes) {
                if (p.title != null && !p.title.isEmpty()) titles.add(p.title);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_dropdown_item_1line, titles);
            etProgram.setAdapter(adapter);
        });
        tvStart = findViewById(R.id.tvStartTime);
        tvEnd = findViewById(R.id.tvEndTime);
        btnSave = findViewById(R.id.btnSaveEvent);
        btnModeOnline = findViewById(R.id.btnModeOnline);
        tilLocation = findViewById(R.id.tilLocation);
        tilMeetingLink = findViewById(R.id.tilMeetingLink);

        MaterialButtonToggleGroup toggleMode = findViewById(R.id.toggleMode);
        toggleMode.check(R.id.btnModePhysical);
        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean online = checkedId == R.id.btnModeOnline;
            tilMeetingLink.setVisibility(online ? View.VISIBLE : View.GONE);
            tilLocation.setVisibility(online ? View.GONE : View.VISIBLE);
        });

        tvDate.setOnClickListener(v -> pickDate());
        tvStart.setOnClickListener(v -> pickTime(true));
        tvEnd.setOnClickListener(v -> pickTime(false));
        btnSave.setOnClickListener(v -> saveEvent());
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            dateCal.set(Calendar.YEAR, year);
            dateCal.set(Calendar.MONTH, month);
            dateCal.set(Calendar.DAY_OF_MONTH, day);
            dateSet = true;
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
            tvDate.setText(sdf.format(dateCal.getTime()));
        }, dateCal.get(Calendar.YEAR), dateCal.get(Calendar.MONTH), dateCal.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void pickTime(boolean isStart) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> {
            String formatted = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            if (isStart) {
                startTime = formatted;
                tvStart.setText(formatted);
                dateCal.set(Calendar.HOUR_OF_DAY, hour);
                dateCal.set(Calendar.MINUTE, minute);
                dateCal.set(Calendar.SECOND, 0);
            } else {
                endTime = formatted;
                tvEnd.setText(formatted);
            }
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private void saveEvent() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        if (title.isEmpty()) {
            etTitle.setError("Enter a title");
            return;
        }
        if (!dateSet) {
            Toast.makeText(this, "Pick a date", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : Util.getState(Constants.CURRENT_USER_ID, "");
        if (uid.isEmpty()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        long eventMillis = dateCal.getTimeInMillis();

        boolean online = btnModeOnline.isChecked();
        String location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String meetingLink = etMeetingLink.getText() != null ? etMeetingLink.getText().toString().trim() : "";

        if (online && meetingLink.isEmpty()) {
            etMeetingLink.setError("Add a meeting link");
            return;
        }
        if (!online && location.isEmpty()) {
            etLocation.setError("Add a location");
            return;
        }

        String program = etProgram.getText() != null ? etProgram.getText().toString().trim() : "";
        String price = etPrice.getText() != null ? etPrice.getText().toString().trim() : "";
        int seats = 0;
        String seatsRaw = etSeats.getText() != null ? etSeats.getText().toString().trim() : "";
        if (!seatsRaw.isEmpty()) {
            try {
                seats = Integer.parseInt(seatsRaw);
            } catch (NumberFormatException ignored) {
                etSeats.setError("Enter a number");
                return;
            }
        }

        btnSave.setEnabled(false);
        LmsModels.CreateHubEventBody body = new LmsModels.CreateHubEventBody(
                title,
                eventMillis,
                startTime,
                endTime,
                description.isEmpty() ? null : description,
                online ? "online" : "physical",
                location,
                meetingLink,
                program,
                seats,
                price
        );

        FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
        if (authUser == null) {
            btnSave.setEnabled(true);
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        authUser.getIdToken(false).addOnSuccessListener(tokenResult -> {
            String bearer = "Bearer " + tokenResult.getToken();
            Executors.newSingleThreadExecutor().execute(() -> {
                boolean ok = LmsEventsDataSource.createHubEventBlocking(bearer, body);
                runOnUiThread(() -> {
                    if (ok) {
                        WorkManager.getInstance(getApplicationContext())
                                .enqueue(new OneTimeWorkRequest.Builder(EventReminderWorker.class).build());
                        Toast.makeText(this, "Event published to Hub & site", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        btnSave.setEnabled(true);
                        Toast.makeText(this, "Failed to create event", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }).addOnFailureListener(e -> {
            btnSave.setEnabled(true);
            Toast.makeText(this, "Auth failed", Toast.LENGTH_SHORT).show();
        });
    }
}
