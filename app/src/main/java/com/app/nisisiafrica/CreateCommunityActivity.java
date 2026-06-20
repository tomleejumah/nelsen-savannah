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

public class CreateCommunityActivity extends AppCompatActivity {

    private final CommunityRepository repository = new CommunityRepository();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_community);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextInputEditText etName = findViewById(R.id.etName);
        TextInputEditText etDescription = findViewById(R.id.etDescription);
        MaterialButton btnCreate = findViewById(R.id.btnCreate);

        btnCreate.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String desc = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                etName.setError("Enter a name");
                return;
            }
            btnCreate.setEnabled(false);
            repository.createCommunity(name, desc, (success, idOrError) -> {
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
        });
    }
}
