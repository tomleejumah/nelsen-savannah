package com.app.nisisiafrica;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.android.material.textfield.TextInputEditText;

import de.hdodenhof.circleimageview.CircleImageView;

public class CreateCommunityActivity extends AppCompatActivity {

    private final CommunityRepository repository = new CommunityRepository();
    private Uri selectedImage;
    private CircleImageView imgCommunity;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_community);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextInputEditText etName = findViewById(R.id.etName);
        TextInputEditText etDescription = findViewById(R.id.etDescription);
        MaterialButton btnCreate = findViewById(R.id.btnCreate);
        imgCommunity = findViewById(R.id.imgCommunity);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) {
                        selectedImage = uri;
                        Glide.with(this).load(uri).into(imgCommunity);
                    }
                });
        findViewById(R.id.pickCommunityImage).setOnClickListener(v -> imagePicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        btnCreate.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String desc = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                etName.setError("Enter a name");
                return;
            }
            btnCreate.setEnabled(false);
            if (selectedImage != null) {
                StorageUploader.upload(selectedImage, "community_icons", (ok, url) ->
                        create(name, desc, ok && url != null ? url : "", btnCreate));
            } else {
                create(name, desc, "", btnCreate);
            }
        });
    }

    private void create(String name, String desc, String iconUrl, MaterialButton btnCreate) {
        repository.createCommunity(name, desc, iconUrl, (success, idOrError) -> {
            if (success && idOrError != null) {
                // Creator auto-joins their community.
                repository.joinCommunity(idOrError, joined -> {
                    Toast.makeText(this, "Community created", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                btnCreate.setEnabled(true);
                Toast.makeText(this, "Failed: " + idOrError, Toast.LENGTH_LONG).show();
            }
        });
    }
}
