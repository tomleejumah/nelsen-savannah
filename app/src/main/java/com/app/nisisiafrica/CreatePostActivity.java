package com.app.nisisiafrica;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CreatePostActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID = "extra_community_id";

    private final CommunityRepository repository = new CommunityRepository();

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
            repository.createPost(communityId, title, body, authorName, (success, idOrError) -> {
                if (success) {
                    Toast.makeText(this, "Posted", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    btnPost.setEnabled(true);
                    Toast.makeText(this, "Failed: " + idOrError, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
