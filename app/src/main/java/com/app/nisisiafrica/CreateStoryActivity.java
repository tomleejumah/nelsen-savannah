package com.app.nisisiafrica;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.data.Model.Story;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CreateStoryActivity extends AppCompatActivity {

    private TextInputEditText etCompany, etMediaUrl, etLogoUrl, etCaption, etCtaUrl;
    private MaterialButton btnPublish;

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
        etMediaUrl = findViewById(R.id.etMediaUrl);
        etLogoUrl = findViewById(R.id.etLogoUrl);
        etCaption = findViewById(R.id.etCaption);
        etCtaUrl = findViewById(R.id.etCtaUrl);
        btnPublish = findViewById(R.id.btnPublishStory);

        btnPublish.setOnClickListener(v -> publish());
    }

    private String text(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    private void publish() {
        String company = text(etCompany);
        String media = text(etMediaUrl);

        if (company.isEmpty()) {
            etCompany.setError("Enter a name");
            return;
        }
        if (media.isEmpty()) {
            etMediaUrl.setError("Add an image URL");
            return;
        }

        Story story = new Story();
        story.companyName = company;
        story.mediaUrl = media;
        story.logoUrl = text(etLogoUrl).isEmpty() ? media : text(etLogoUrl);
        story.caption = text(etCaption);
        story.ctaUrl = text(etCtaUrl);
        story.timestamp = System.currentTimeMillis();
        story.active = true;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("stories").push();
        story.storyId = ref.getKey();
        btnPublish.setEnabled(false);
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
}
