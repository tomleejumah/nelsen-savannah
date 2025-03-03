package com.app.nisisiafrica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AnticipateInterpolator;
import android.window.SplashScreenView;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.customsnackbarlib.CustomSnackbar;
import com.app.nisisiafrica.Model.UserData;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("USER_DATA")) {
            UserData userData = intent.getParcelableExtra("USER_DATA");

            if (userData != null) {
                String email = userData.getEmail();
                String name = userData.getDisplayName();
                String firstName = userData.getFirstName();
                String lastName = userData.getLastName();
                String photoUrl = userData.getPhotoUrl();
                String userId = userData.getId();

                // Show a Snackbar (if you want)
                CustomSnackbar.show(findViewById(android.R.id.content),
                        "Welcome, " + name + "!",
                        Snackbar.LENGTH_SHORT, 5);
            }
        }
    }
}