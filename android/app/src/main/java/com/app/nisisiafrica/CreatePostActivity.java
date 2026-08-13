package com.app.nisisiafrica;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.app.nisisiafrica.data.remote.StorageUploader;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CreatePostActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID = "extra_community_id";

    private final CommunityRepository repository = new CommunityRepository();
    private Uri selectedImage;
    private ImageView imgPreview;
    private LinearLayout pickHint;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        String communityId = getIntent().getStringExtra(EXTRA_COMMUNITY_ID);
        if (communityId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextInputEditText etTitle = findViewById(R.id.etTitle);
        TextInputEditText etBody = findViewById(R.id.etBody);
        MaterialButton btnPost = findViewById(R.id.btnPost);
        imgPreview = findViewById(R.id.imgPostPreview);
        pickHint = findViewById(R.id.postPickHint);
        MaterialCardView cardPostImage = findViewById(R.id.cardPostImage);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) {
                        selectedImage = uri;
                        pickHint.setVisibility(View.GONE);
                        imgPreview.setVisibility(View.VISIBLE);
                        Glide.with(this).load(uri).into(imgPreview);
                    }
                });
        cardPostImage.setOnClickListener(v -> imagePicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String authorName = user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                ? user.getDisplayName() : "User";

        btnPost.setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            String body = etBody.getText() != null ? etBody.getText().toString().trim() : "";
            if (TextUtils.isEmpty(body)) {
                etBody.setError("Write something");
                return;
            }
            btnPost.setEnabled(false);
            if (selectedImage != null) {
                StorageUploader.upload(selectedImage, "community_posts", (ok, url) ->
                        post(communityId, title, body, authorName, ok && url != null ? url : "", btnPost));
            } else {
                post(communityId, title, body, authorName, "", btnPost);
            }
        });
    }

    private void post(String communityId, String title, String body, String authorName,
                      String imageUrl, MaterialButton btnPost) {
        repository.createPost(communityId, title, body, authorName, imageUrl, (success, idOrError) -> {
            if (success) {
                Toast.makeText(this, "Posted", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                btnPost.setEnabled(true);
                Toast.makeText(this, "Failed: " + idOrError, Toast.LENGTH_LONG).show();
            }
        });
    }
}
