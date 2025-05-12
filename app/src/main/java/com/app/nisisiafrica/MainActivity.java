package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.customsnackbarlib.CustomSnackbar;
import com.app.nisisiafrica.Auth.FirebaseUserHelper;
import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Model.UserData;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends ComponentActivity {
    private String userRole;
    private UserData userData;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
//        splashScreen.setKeepOnScreenCondition(() -> true);
//        splashScreen.setOnExitAnimationListener(SplashScreenViewProvider::remove);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("USER_DATA")) {
             userData = intent.getParcelableExtra("USER_DATA");

            //todo cache user data in room for a week before logging them out
            if (userData != null) {
                String email = userData.getEmail();
                String name = userData.getDisplayName();
                String firstName = userData.getFirstName();
                String lastName = userData.getLastName();
                String photoUrl = userData.getPhotoUrl();
                String userId = userData.getId();
                userRole = userData.getUserRole();

                Utils.saveState("userId", userId);

                Log.d(TAG, "onCreate: Role "+ userData.getUserRole());

                if (TextUtils.isEmpty(userRole)) {
                    FirebaseDatabase.getInstance().getReference()
                            .child("roles")
                            .child(userId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    //todo rectify this for regular user
                                    String assignedRole = snapshot.getValue(String.class);
                                    Log.d(TAG, "onCreate: 2Role "+ assignedRole);
                                    if (TextUtils.isEmpty(assignedRole)) {
//                                        userRole = Utils.getState("userRole", "Mentee");
                                        userRole = "Mentee";
                                        userData.setUserRole(userRole);
                                        Utils.saveState("userRole", userRole);

                                        // Correctly set the role in Firebase
                                        FirebaseDatabase.getInstance().getReference()
                                                .child("roles")
                                                .child(userId)
                                                .setValue(userRole);
                                    } else {
                                        userRole = assignedRole;
                                        userData.setUserRole(userRole);
                                        Utils.saveState("userRole", userRole);
                                    }

                                    // Show Snackbar after role is determined
                                    CustomSnackbar.show(findViewById(android.R.id.content),
                                            "Welcome, " + name + "!",
                                            Snackbar.LENGTH_SHORT, 5);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    // Handle potential errors
//                                    userRole = Utils.getState("userRole", "Mentee");
                                    CustomSnackbar.show(findViewById(android.R.id.content),
                                            "Welcome, " + name + "!",
                                            Snackbar.LENGTH_SHORT, 5);
                                }
                            });
                } else {
                    // If userRole is already set
                    CustomSnackbar.show(findViewById(android.R.id.content),
                            "Welcome, " + name + "!",
                            Snackbar.LENGTH_SHORT, 5);
                }
            }
        } else {
            //todo cache user data in room for a week before logging them out
            FirebaseUserHelper.INSTANCE.getCurrentUserAndData(userData -> {
                if (userData != null) {
                    // Use the userData here
                    String email = userData.getEmail();
                    String name = userData.getDisplayName();
                    String firstName = userData.getFirstName();
                    String lastName = userData.getLastName();
                    String photoUrl = userData.getPhotoUrl();
                    String id = userData.getId();
                    Log.d(TAG, "onCreate: 3Role"+userData.getUserRole());

                } else {
                    Log.d("User", "No user data found.");
                    startActivity(new Intent(MainActivity.this, LoginSignUpActivity.class));
                    finish();
                }
            });

        }
    }
}