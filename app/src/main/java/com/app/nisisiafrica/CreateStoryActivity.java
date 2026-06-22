package com.app.nisisiafrica;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.data.Model.Story;
import com.app.nisisiafrica.data.remote.StorageUploader;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CreateStoryActivity extends AppCompatActivity {

    private TextInputEditText etCompany, etCaption, etCtaUrl;
    private MaterialAutoCompleteTextView spinnerDuration;
    private MaterialButton btnPublish;
    private ImageView imgPreview;
    private LinearLayout pickHint;

    private Uri selectedImage;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;

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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etCompany = findViewById(R.id.etCompany);
        etCaption = findViewById(R.id.etCaption);
        etCtaUrl = findViewById(R.id.etCtaUrl);
        spinnerDuration = findViewById(R.id.spinnerDuration);
        btnPublish = findViewById(R.id.btnPublishStory);
        imgPreview = findViewById(R.id.imgPreview);
        pickHint = findViewById(R.id.pickHint);
        MaterialCardView cardPickImage = findViewById(R.id.cardPickImage);

        spinnerDuration.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, DURATIONS));
        spinnerDuration.setText(DURATIONS[1], false); // default 24h

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

    private void publish() {
        String company = text(etCompany);
        if (company.isEmpty()) {
            etCompany.setError("Enter a name");
            return;
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
                Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show();
                return;
            }
            saveStory(company, url);
        });
    }

    private void saveStory(String company, String imageUrl) {
        long now = System.currentTimeMillis();
        String uid = FirebaseAuth.getInstance().getUid();

        Story story = new Story();
        story.companyName = company;
        story.mediaUrl = imageUrl;
        story.logoUrl = imageUrl;
        story.caption = text(etCaption);
        story.ctaUrl = normalizeUrl(text(etCtaUrl));
        story.timestamp = now;
        story.expiresAt = now + selectedDurationHours() * 60L * 60L * 1000L;
        story.ownerId = uid;
        story.views = 0;
        story.active = true;

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

    /** Ensures a CTA link has a scheme so ACTION_VIEW can resolve it. */
    static String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }
}
