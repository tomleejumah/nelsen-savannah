package com.app.nisisiafrica;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.Worker.EventReminderWorker;
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.Model.ProgrammeItem;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.app.nisisiafrica.data.remote.ApiClient;
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
import java.util.Map;

public class CreateEventActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID = "extra_community_id";
    public static final String EXTRA_COMMUNITY_NAME = "extra_community_name";
    public static final String EXTRA_LIVE_MODE = "extra_live_mode";
    public static final String EXTRA_SCHEDULE_LIVE = "extra_schedule_live";
    private static final int REQUEST_LIVE_PERMISSIONS = 4107;

    private TextInputEditText etTitle, etDescription, etLocation, etMeetingLink, etSeats, etPrice;
    private MaterialAutoCompleteTextView etProgram, etLiveCourse;
    private TextView tvDate, tvStart, tvEnd, tvLiveAudienceHint;
    private MaterialButton btnSave, btnModeOnline;
    private TextInputLayout tilLocation, tilMeetingLink, tilProgram, tilLiveCourse;
    private MaterialButtonToggleGroup toggleMode, liveAudienceToggle;
    private MaterialButton btnLiveSchool, btnLiveCourse, btnLivePlatform;
    private View eventCommercialRow, liveAudienceSection;
    private boolean liveMode = false;
    private boolean scheduleLive = false;

    private final List<LmsModels.TrackCard> liveTrackOptions = new ArrayList<>();
    private String liveAudienceScope = "school";
    private String liveSchoolId = "";
    private String liveSchoolName = "";
    private String liveTrackId = "";
    private String liveTrackTitle = "";

    private final Calendar dateCal = Calendar.getInstance();
    private boolean dateSet = false;
    private String startTime = "";
    private String endTime = "";
    private String communityId;
    private String communityName;

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

        communityId = getIntent().getStringExtra(EXTRA_COMMUNITY_ID);
        communityName = getIntent().getStringExtra(EXTRA_COMMUNITY_NAME);
        liveMode = getIntent().getBooleanExtra(EXTRA_LIVE_MODE, false);
        scheduleLive = liveMode && getIntent().getBooleanExtra(EXTRA_SCHEDULE_LIVE, false);
        if (!Roles.canCreate()) {
            Toast.makeText(
                    this,
                    "Only mentors, school admins and super admins can create events",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }
        if (liveMode) {
            toolbar.setTitle(scheduleLive ? "Schedule Live" : "Go Live");
            toolbar.setSubtitle(scheduleLive ? "Choose when your Nelsen live starts" : "Nelsen Live");
        } else if (communityId != null && !communityId.isEmpty()) {
            toolbar.setTitle(R.string.group_create_event);
            if (communityName != null && !communityName.isEmpty()) {
                toolbar.setSubtitle(communityName);
            }
        }

        etTitle = findViewById(R.id.etEventTitle);
        etDescription = findViewById(R.id.etEventDescription);
        etLocation = findViewById(R.id.etEventLocation);
        etMeetingLink = findViewById(R.id.etMeetingLink);
        etProgram = findViewById(R.id.etProgram);
        etLiveCourse = findViewById(R.id.etLiveCourse);
        liveAudienceToggle = findViewById(R.id.liveAudienceToggle);
        btnLiveSchool = findViewById(R.id.btnLiveSchool);
        btnLiveCourse = findViewById(R.id.btnLiveCourse);
        btnLivePlatform = findViewById(R.id.btnLivePlatform);
        etSeats = findViewById(R.id.etSeats);
        etPrice = findViewById(R.id.etPrice);
        tvDate = findViewById(R.id.tvDate);
        tvStart = findViewById(R.id.tvStartTime);
        tvEnd = findViewById(R.id.tvEndTime);
        btnSave = findViewById(R.id.btnSaveEvent);
        btnModeOnline = findViewById(R.id.btnModeOnline);
        tilLocation = findViewById(R.id.tilLocation);
        tilMeetingLink = findViewById(R.id.tilMeetingLink);
        tilProgram = findViewById(R.id.tilProgram);
        tilLiveCourse = findViewById(R.id.tilLiveCourse);
        tvLiveAudienceHint = findViewById(R.id.tvLiveAudienceHint);
        liveAudienceSection = findViewById(R.id.liveAudienceSection);
        toggleMode = findViewById(R.id.toggleMode);
        eventCommercialRow = findViewById(R.id.eventCommercialRow);

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

        toggleMode.check(R.id.btnModePhysical);
        toggleMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean online = checkedId == R.id.btnModeOnline;
            tilMeetingLink.setVisibility(online ? View.VISIBLE : View.GONE);
            tilLocation.setVisibility(online ? View.GONE : View.VISIBLE);
        });

        if (liveMode) {
            toggleMode.check(R.id.btnModeOnline);
            toggleMode.setVisibility(View.GONE);
            tilLocation.setVisibility(View.GONE);
            // Nelsen creates the broadcast/stream automatically; hosts never paste a link.
            tilMeetingLink.setVisibility(View.GONE);
            if (eventCommercialRow != null) eventCommercialRow.setVisibility(View.GONE);
            if (tilProgram != null) tilProgram.setVisibility(View.GONE);
            if (liveAudienceSection != null) liveAudienceSection.setVisibility(View.VISIBLE);

            if (!scheduleLive) {
                dateCal.setTimeInMillis(System.currentTimeMillis());
                dateSet = true;
                startTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(dateCal.getTime());
                tvDate.setVisibility(View.GONE);
                View liveTimeRow = (View) tvStart.getParent();
                if (liveTimeRow != null) liveTimeRow.setVisibility(View.GONE);
            } else {
                tvDate.setVisibility(View.VISIBLE);
                View liveTimeRow = (View) tvStart.getParent();
                if (liveTimeRow != null) liveTimeRow.setVisibility(View.VISIBLE);
            }

            setupLiveAudienceControls();
            loadLiveAudienceContext();
            btnSave.setText(scheduleLive ? "Schedule Live" : "Go Live");
        }

        tvDate.setOnClickListener(v -> pickDate());
        tvStart.setOnClickListener(v -> pickTime(true));
        tvEnd.setOnClickListener(v -> pickTime(false));
        btnSave.setOnClickListener(v -> saveEvent());
    }

    private void setupLiveAudienceControls() {
        btnLivePlatform.setVisibility(Roles.isSuperAdmin() ? View.VISIBLE : View.GONE);
        liveAudienceToggle.check(R.id.btnLiveSchool);
        liveAudienceScope = "school";
        liveAudienceToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnLiveCourse) liveAudienceScope = "course";
            else if (checkedId == R.id.btnLivePlatform) liveAudienceScope = "platform";
            else liveAudienceScope = "school";
            liveTrackId = "";
            liveTrackTitle = "";
            etLiveCourse.setText("", false);
            updateLiveAudienceUi();
        });
        updateLiveAudienceUi();
    }

    private void updateLiveAudienceUi() {
        if (!liveMode) return;
        boolean course = "course".equals(liveAudienceScope);
        tilLiveCourse.setVisibility(course ? View.VISIBLE : View.GONE);
        if (course) {
            tvLiveAudienceHint.setText(
                    liveTrackOptions.isEmpty()
                            ? "No courses are available for this account in the active school."
                            : "Choose a course. Only its enrolled learners can join this live.");
        } else if ("platform".equals(liveAudienceScope)) {
            tvLiveAudienceHint.setText("All signed-in Nelsen users will see this live session.");
        } else if (liveSchoolId.isEmpty()) {
            tvLiveAudienceHint.setText("No active school is linked to this account.");
        } else {
            String label = liveSchoolName.isEmpty() ? liveSchoolId : liveSchoolName;
            tvLiveAudienceHint.setText(label + " is your active school. Only its active members will see this live.");
        }
    }

    private void loadLiveAudienceContext() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        user.getIdToken(false).addOnSuccessListener(tokenResult -> {
            String bearer = "Bearer " + tokenResult.getToken();
            Executors.newSingleThreadExecutor().execute(() -> {
                String schoolId = "";
                String schoolName = "";
                List<LmsModels.TrackCard> tracks = new ArrayList<>();
                try {
                    retrofit2.Response<LmsModels.MeEnvelope> meRes =
                            ApiClient.getLmsService().me(bearer).execute();
                    LmsModels.MeEnvelope me = meRes.body();
                    if (meRes.isSuccessful() && me != null && me.ok && me.data != null) {
                        schoolId = value(me.data.get("activeSchoolId"));
                        if (schoolId.isEmpty()) schoolId = value(me.data.get("schoolId"));
                        schoolName = value(me.data.get("schoolName"));
                    }

                    retrofit2.Response<LmsModels.TracksEnvelope> trackRes =
                            ApiClient.getLmsService().tracks(
                                    bearer, schoolId.isEmpty() ? null : schoolId).execute();
                    LmsModels.TracksEnvelope trackBody = trackRes.body();
                    if (trackRes.isSuccessful()
                            && trackBody != null
                            && trackBody.ok
                            && trackBody.data != null
                            && trackBody.data.tracks != null) {
                        tracks.addAll(trackBody.data.tracks);
                    }
                } catch (Exception ignored) {
                }

                final String resolvedSchoolId = schoolId;
                final String resolvedSchoolName = schoolName;
                final List<LmsModels.TrackCard> resolvedTracks = tracks;
                runOnUiThread(() -> {
                    liveSchoolId = resolvedSchoolId;
                    liveSchoolName = resolvedSchoolName;
                    liveTrackOptions.clear();
                    liveTrackOptions.addAll(resolvedTracks);

                    List<String> labels = new ArrayList<>();
                    for (LmsModels.TrackCard track : liveTrackOptions) {
                        String title = track.courseTitle != null && !track.courseTitle.trim().isEmpty()
                                ? track.courseTitle.trim()
                                : track.trackId;
                        labels.add(title + " · " + track.trackId);
                    }
                    etLiveCourse.setAdapter(new ArrayAdapter<>(
                            this, android.R.layout.simple_dropdown_item_1line, labels));
                    etLiveCourse.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < 0 || position >= liveTrackOptions.size()) return;
                        LmsModels.TrackCard selected = liveTrackOptions.get(position);
                        liveTrackId = selected.trackId != null ? selected.trackId : "";
                        liveTrackTitle = selected.courseTitle != null ? selected.courseTitle : "";
                        if (selected.schoolId != null && !selected.schoolId.trim().isEmpty()) {
                            liveSchoolId = selected.schoolId.trim();
                        }
                    });
                    updateLiveAudienceUi();
                });
            });
        });
    }

    private static String value(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
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
        if (liveMode && !scheduleLive && !hasLivePermissions()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    REQUEST_LIVE_PERMISSIONS
            );
            return;
        }

        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        if (title.isEmpty()) {
            etTitle.setError("Enter a title");
            return;
        }
        if (!dateSet && (!liveMode || scheduleLive)) {
            Toast.makeText(this, "Pick a date", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : Util.getState(Constants.CURRENT_USER_ID, "");
        if (uid.isEmpty()) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        long eventMillis;
        if (liveMode && !scheduleLive) {
            dateCal.setTimeInMillis(System.currentTimeMillis());
            eventMillis = dateCal.getTimeInMillis();
            startTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(dateCal.getTime());
            endTime = "";
        } else {
            eventMillis = dateCal.getTimeInMillis();
        }

        boolean online = liveMode || btnModeOnline.isChecked();
        String location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String meetingLink = etMeetingLink.getText() != null ? etMeetingLink.getText().toString().trim() : "";

        if (!liveMode && online && meetingLink.isEmpty()) {
            etMeetingLink.setError("Add a meeting link");
            return;
        }
        if (!liveMode && !online && location.isEmpty()) {
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

        if (liveMode) {
            if ("course".equals(liveAudienceScope)) {
                if (liveTrackId.isEmpty()) {
                    tilLiveCourse.setError("Select a course");
                    return;
                }
                tilLiveCourse.setError(null);
                if (!liveTrackTitle.isEmpty()) program = liveTrackTitle;
            } else if ("school".equals(liveAudienceScope) && liveSchoolId.isEmpty()) {
                Toast.makeText(this, "Select an active school before linking this live", Toast.LENGTH_SHORT).show();
                return;
            } else if ("platform".equals(liveAudienceScope) && !Roles.isSuperAdmin()) {
                Toast.makeText(this, "Only a super admin can create a platform live", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        btnSave.setEnabled(false);

        if (!liveMode && communityId != null && !communityId.isEmpty()) {
            saveCommunityEvent(title, description, location, meetingLink,
                    online ? "online" : "physical", eventMillis, program, seats, price);
            return;
        }

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
                liveMode ? 0 : seats,
                liveMode ? "" : price
        );
        if (liveMode) {
            body.eventType = "live";
            body.liveStatus = "scheduled";
            body.audienceScope = liveAudienceScope;
            body.schoolId = "platform".equals(liveAudienceScope) ? "" : liveSchoolId;
            body.trackId = "course".equals(liveAudienceScope) ? liveTrackId : "";
            body.youtubePrivacy = "unlisted";
        }

        FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
        if (authUser == null) {
            btnSave.setEnabled(true);
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        authUser.getIdToken(false).addOnSuccessListener(tokenResult -> {
            String bearer = "Bearer " + tokenResult.getToken();
            Executors.newSingleThreadExecutor().execute(() -> {
                if (liveMode) {
                    kotlin.Pair<LmsModels.YouTubeLiveData, String> result =
                            LmsEventsDataSource.createYouTubeLiveBlocking(bearer, body);
                    runOnUiThread(() -> {
                        LmsModels.YouTubeLiveData live = result.getFirst();
                        if (live != null && live.event != null && scheduleLive) {
                            WorkManager.getInstance(getApplicationContext())
                                    .enqueue(new OneTimeWorkRequest.Builder(EventReminderWorker.class).build());
                            Toast.makeText(this, "Live scheduled", Toast.LENGTH_SHORT).show();
                            finish();
                        } else if (live != null && live.ingestUrl != null && !live.ingestUrl.isEmpty()) {
                            Intent intent = new Intent(this, GoLiveActivity.class);
                            intent.putExtra(GoLiveActivity.EXTRA_INGEST_URL, live.ingestUrl);
                            intent.putExtra(GoLiveActivity.EXTRA_TITLE, title);
                            intent.putExtra(
                                    GoLiveActivity.EXTRA_EVENT_ID,
                                    live.event != null ? live.event.eventId : "");
                            intent.putExtra(GoLiveActivity.EXTRA_YOUTUBE_URL, live.youtubeUrl);
                            startActivity(intent);
                            finish();
                        } else {
                            btnSave.setEnabled(true);
                            String error = result.getSecond();
                            if (error != null && error.toLowerCase(Locale.US).contains("not connected")) {
                                error = "Live streaming is not configured yet. Ask a School Admin or SuperAdmin to finish setup in Nelsen settings.";
                            }
                            Toast.makeText(
                                    this,
                                    error != null ? error : "Could not start live",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                    return;
                }

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

    private boolean hasLivePermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LIVE_PERMISSIONS) return;
        if (hasLivePermissions()) {
            saveEvent();
        } else {
            Toast.makeText(
                    this,
                    "Camera and microphone permission are required to go live",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void saveCommunityEvent(
            String title,
            String description,
            String location,
            String meetingLink,
            String mode,
            long startsAt,
            String program,
            int seats,
            String price
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String byName = user != null && user.getDisplayName() != null ? user.getDisplayName() : "Host";
        new CommunityRepository().createCommunityEvent(
                communityId,
                title,
                description,
                location,
                meetingLink,
                mode,
                startsAt,
                endTime,
                program,
                seats,
                price,
                byName,
                (ok, idOrError) -> runOnUiThread(() -> {
                    if (ok) {
                        Toast.makeText(this, "Event created", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        btnSave.setEnabled(true);
                        Toast.makeText(this,
                                idOrError != null ? idOrError : "Could not create event",
                                Toast.LENGTH_SHORT).show();
                    }
                })
        );
    }
}
