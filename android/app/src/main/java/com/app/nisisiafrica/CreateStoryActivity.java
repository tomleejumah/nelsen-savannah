package com.app.nisisiafrica;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.data.Model.Story;
import com.app.nisisiafrica.data.remote.StorageUploader;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CreateStoryActivity extends AppCompatActivity {

    private TextInputEditText etCompany, etCaption, etCtaUrl;
    private TextInputLayout tilCompany, tilCtaUrl;
    private MaterialAutoCompleteTextView spinnerDuration;
    private MaterialButton btnPublish;
    private ImageView imgPreview;
    private LinearLayout pickHint;
    private TextView tvBlurb;
    private MaterialToolbar toolbar;

    private Uri selectedImage;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;
    private boolean corporateMode;

    private static final String[] DURATIONS = {"12 hours", "24 hours", "48 hours"};
    private static final long[] DURATION_HOURS = {12L, 24L, 48L};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_story);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        corporateMode = Roles.postsCorporateStories();

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etCompany = findViewById(R.id.etCompany);
        etCaption = findViewById(R.id.etCaption);
        etCtaUrl = findViewById(R.id.etCtaUrl);
        tilCompany = findViewById(R.id.tilCompany);
        tilCtaUrl = findViewById(R.id.tilCtaUrl);
        spinnerDuration = findViewById(R.id.spinnerDuration);
        btnPublish = findViewById(R.id.btnPublishStory);
        imgPreview = findViewById(R.id.imgPreview);
        pickHint = findViewById(R.id.pickHint);
        tvBlurb = findViewById(R.id.tvStoryBlurb);
        MaterialCardView cardPickImage = findViewById(R.id.cardPickImage);

        applyRoleUi();

        spinnerDuration.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, DURATIONS));
        spinnerDuration.setText(DURATIONS[1], false);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) {
                        selectedImage = uri;
                        pickHint.setVisibility(View.GONE);
                        imgPreview.setVisibility(View.VISIBLE);
                        Glide.with(this).load(uri).into(imgPreview);
                    }
                });

        cardPickImage.setOnClickListener(v -> imagePicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        btnPublish.setOnClickListener(v -> publish());
    }

    private void applyRoleUi() {
        if (corporateMode) {
            toolbar.setTitle("Corporate story");
            tvBlurb.setText("Marketplace / brand stories appear on Home and expire automatically.");
            tilCompany.setVisibility(View.VISIBLE);
            tilCtaUrl.setVisibility(View.VISIBLE);
            btnPublish.setText("Publish brand story");
        } else {
            toolbar.setTitle("Your story");
            tvBlurb.setText("Share a personal story with the community. It appears on Home and expires automatically.");
            tilCompany.setVisibility(View.GONE);
            tilCtaUrl.setVisibility(View.GONE);
            btnPublish.setText("Publish story");
        }
    }

    private String text(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    private long selectedDurationHours() {
        String sel = spinnerDuration.getText() != null ? spinnerDuration.getText().toString() : "";
        for (int i = 0; i < DURATIONS.length; i++) {
            if (DURATIONS[i].equals(sel)) return DURATION_HOURS[i];
        }
        return 24L;
    }

    private String personalDisplayName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && !TextUtils.isEmpty(user.getDisplayName())) {
            return user.getDisplayName();
        }
        return "Member";
    }

    private void publish() {
        String label;
        if (corporateMode) {
            label = text(etCompany);
            if (label.isEmpty()) {
                etCompany.setError("Brand / company name is required");
                etCompany.requestFocus();
                return;
            }
        } else {
            label = personalDisplayName();
        }

        if (selectedImage == null) {
            Toast.makeText(this, "Select an image", Toast.LENGTH_SHORT).show();
            return;
        }

        btnPublish.setEnabled(false);
        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();
        StorageUploader.upload(selectedImage, "stories", (success, url) -> {
            if (!success || url == null) {
                btnPublish.setEnabled(true);
                Toast.makeText(this, "Upload failed — check connection and try again",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            saveStory(label, url);
        });
    }

    private void saveStory(String label, String imageUrl) {
        long now = System.currentTimeMillis();
        String uid = FirebaseAuth.getInstance().getUid();

        Story story = new Story();
        story.companyName = label;
        story.mediaUrl = imageUrl;
        story.logoUrl = imageUrl;
        story.caption = text(etCaption);
        story.ctaUrl = corporateMode ? normalizeUrl(text(etCtaUrl)) : "";
        story.timestamp = now;
        story.expiresAt = now + selectedDurationHours() * 60L * 60L * 1000L;
        story.ownerId = uid;
        story.views = 0;
        story.active = true;
        story.storyType = corporateMode ? Story.TYPE_CORPORATE : Story.TYPE_PERSONAL;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("stories").push();
        story.storyId = ref.getKey();
        ref.setValue(story)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Story published", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnPublish.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    static String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }
}
