package com.app.nisisiafrica;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.app.nisisiafrica.Utils.Roles;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.DateFormat;
import java.util.Calendar;

public class CreateCommunityEventActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID = "extra_community_id";
    public static final String EXTRA_COMMUNITY_NAME = "extra_community_name";

    private final CommunityRepository repository = new CommunityRepository();
    private final Calendar when = Calendar.getInstance();
    private boolean whenSet = false;
    private String communityId;

    private TextInputEditText etTitle, etDescription, etLocation;
    private TextView tvWhen;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_community_event);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        communityId = getIntent().getStringExtra(EXTRA_COMMUNITY_ID);
        if (TextUtils.isEmpty(communityId)) {
            finish();
            return;
        }
        if (!Roles.canManageCommunities()) {
            Toast.makeText(this, "Only mentors can create group events", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        String name = getIntent().getStringExtra(EXTRA_COMMUNITY_NAME);
        if (!TextUtils.isEmpty(name)) toolbar.setSubtitle(name);

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        tvWhen = findViewById(R.id.tvWhen);
        btnSave = findViewById(R.id.btnSave);

        tvWhen.setOnClickListener(v -> pickWhen());
        btnSave.setOnClickListener(v -> save());
    }

    private void pickWhen() {
        DatePickerDialog date = new DatePickerDialog(this, (view, y, m, d) -> {
            when.set(Calendar.YEAR, y);
            when.set(Calendar.MONTH, m);
            when.set(Calendar.DAY_OF_MONTH, d);
            new TimePickerDialog(this, (tv, hour, minute) -> {
                when.set(Calendar.HOUR_OF_DAY, hour);
                when.set(Calendar.MINUTE, minute);
                when.set(Calendar.SECOND, 0);
                whenSet = true;
                tvWhen.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(when.getTime()));
            }, when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE), false).show();
        }, when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH));
        date.show();
    }

    private void save() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!whenSet) {
            Toast.makeText(this, "Pick a date and time", Toast.LENGTH_SHORT).show();
            return;
        }
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String byName = user != null && user.getDisplayName() != null ? user.getDisplayName() : "Host";
        btnSave.setEnabled(false);
        repository.createCommunityEvent(communityId, title, description, location,
                when.getTimeInMillis(), byName, (ok, idOrError) -> {
                    btnSave.setEnabled(true);
                    if (ok) {
                        Toast.makeText(this, "Event created", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this,
                                idOrError != null ? idOrError : "Could not create event",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
