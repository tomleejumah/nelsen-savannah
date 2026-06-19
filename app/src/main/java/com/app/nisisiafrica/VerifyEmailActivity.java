package com.app.nisisiafrica;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import kotlin.Unit;

public class VerifyEmailActivity extends AppCompatActivity {
    private static final String TAG = "VerifyEmailActivity";
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

        sharedUserViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        user.reload()
                .addOnSuccessListener(v -> {
                    if (!user.isEmailVerified()) return;

                    Util.saveState("AUTH_STATE", "VERIFIED");
                    FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(user.getUid(), remoteData -> {
                        UserData userData;
                        if (remoteData != null) {
                            userData = remoteData;
                            userData.setId(user.getUid());
                        } else {
                            String role = "Mentee";
                            userData = new UserData(
                                    user.getUid(),
                                    user.getEmail() != null ? user.getEmail() : "",
                                    role,
                                    user.getDisplayName(),
                                    "", "", "default", "",
                                    System.currentTimeMillis()
                            );
                            FirebaseRemoteDataSource.INSTANCE.getOrAssignUserRole(
                                    user.getUid(),
                                    assignedRole -> {
                                        userData.setUserRole(assignedRole);
                                        finishVerification(user, userData);
                                        return Unit.INSTANCE;
                                    },
                                    e -> {
                                        Log.e(TAG, "Role fetch failed", e);
                                        userData.setUserRole(role);
                                        finishVerification(user, userData);
                                        return Unit.INSTANCE;
                                    }
                            );
                            return Unit.INSTANCE;
                        }

                        if (userData.getUserRole() == null || userData.getUserRole().isEmpty()) {
                            FirebaseRemoteDataSource.INSTANCE.getOrAssignUserRole(
                                    user.getUid(),
                                    role -> {
                                        userData.setUserRole(role);
                                        finishVerification(user, userData);
                                        return Unit.INSTANCE;
                                    },
                                    e -> {
                                        userData.setUserRole("Mentee");
                                        finishVerification(user, userData);
                                        return Unit.INSTANCE;
                                    }
                            );
                        } else {
                            finishVerification(user, userData);
                        }
                        return Unit.INSTANCE;
                    }, e -> {
                        Log.e(TAG, "Failed to load user profile", e);
                        return Unit.INSTANCE;
                    });
                })
                .addOnFailureListener(e -> Log.e(TAG, "Email reload failed", e));
    }

    private void finishVerification(FirebaseUser user, UserData userData) {
        sharedUserViewModel.setUserData(userData);
        sharedUserViewModel.saveUserData(userData);
        Util.saveState(Constants.CURRENT_USER_ID, user.getUid());
        Util.saveState(Constants.USER_ROLE, userData.getUserRole() != null ? userData.getUserRole() : "Mentee");
        Util.navigateToMainScreen(this, MainActivity.class, true);
    }
}
