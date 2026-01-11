package com.app.nisisiafrica;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.UserData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class VerifyEmailActivity extends AppCompatActivity {
    private UserViewModel sharedUserViewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_verify_email);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }


    @Override
    protected void onStart() {
        super.onStart();

        sharedUserViewModel = new ViewModelProvider((this)).get(UserViewModel.class);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        user.reload().addOnSuccessListener(v -> {
            if (user.isEmailVerified()) {

                Util.saveState("AUTH_STATE", "VERIFIED");

                // Build user data now that verification is real
                UserData userData = new UserData(
                        user.getUid(),
                        user.getEmail(),
                        "Mentee",
                        user.getDisplayName(),
                        "", "", "default", "",
                        System.currentTimeMillis()
                );

                sharedUserViewModel.setUserData(userData);
                sharedUserViewModel.saveUserData(userData);

                Util.saveState(Constants.CURRENT_USER_ID, user.getUid());

                Util.navigateToMainScreen(
                        this,
                        MainActivity.class,
                        true
                );
            }
        });
    }
}