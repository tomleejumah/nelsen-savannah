package com.app.nisisiafrica;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
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
import com.app.nisisiafrica.data.Model.Event;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.app.nisisiafrica.data.remote.NotificationSender;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import kotlin.Unit;

public class CreateEventActivity extends AppCompatActivity {

    private TextInputEditText etTitle, etDescription;
    private TextView tvDate, tvStart, tvEnd;
    private MaterialButton btnSave;

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
        tvDate = findViewById(R.id.tvDate);
        tvStart = findViewById(R.id.tvStartTime);
        tvEnd = findViewById(R.id.tvEndTime);
        btnSave = findViewById(R.id.btnSaveEvent);

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
        String creatorName = user != null && user.getDisplayName() != null ? user.getDisplayName() : "";

        long eventMillis = dateCal.getTimeInMillis();

        // For personal/test events the mentee is the creator; a real booking flow
        // would set a distinct menteeId, in which case that person gets notified.
        final String menteeId = uid;

        Event event = new Event(
                "",                 // eventId (assigned by createEvent)
                title,
                eventMillis,
                startTime,
                endTime,
                "event",
                uid,                // mentorId
                menteeId,           // menteeId (self for personal/test events)
                creatorName,
                creatorName,
                0,                  // status
                description.isEmpty() ? null : description,
                null                // participants
        );

        btnSave.setEnabled(false);
        FirebaseRemoteDataSource.INSTANCE.createEvent(event, uid, menteeId, success -> {
            if (success) {
                // Notify the participant (no-op when scheduling for yourself).
                NotificationSender.event(menteeId, "", title, "scheduled a session with you");
                // Schedule reminders immediately so the new event is picked up.
                WorkManager.getInstance(getApplicationContext())
                        .enqueue(new OneTimeWorkRequest.Builder(EventReminderWorker.class).build());
                Toast.makeText(this, "Event created", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                btnSave.setEnabled(true);
                Toast.makeText(this, "Failed to create event", Toast.LENGTH_SHORT).show();
            }
            return Unit.INSTANCE;
        });
    }
}
