package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.customsnackbarlib.CustomSnackbar;
import com.app.nisisiafrica.Auth.FirebaseUserHelper;
import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Model.UserData;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.List;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainActivity extends ComponentActivity {
    private static final String TAG = "MainActivity";
    private String userRole, currentUser, dbRef;
    private UserData userData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // Get Current logged in User
        currentUser = FirebaseAuth.getInstance().getCurrentUser().getUid();
        AppDatabase db = AppDatabase.getInstance(MainActivity.this);
        UserDao userDao = db.userDao();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("USER_DATA")) {
            userData = intent.getParcelableExtra("USER_DATA");

            if (userData != null) {
                String name = userData.getDisplayName();
                String userId = userData.getId();
                userRole = userData.getUserRole();

                if (TextUtils.isEmpty(userRole)) {
                    FirebaseDatabase.getInstance().getReference()
                            .child("roles/" + currentUser)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String assignedRole = snapshot.getValue(String.class);
                                    if (TextUtils.isEmpty(assignedRole)) {
                                        userRole = "Mentee";

                                        // Correctly set the role in Firebase
                                        FirebaseDatabase.getInstance().getReference()
                                                .child("roles")
                                                .child(userId)
                                                .setValue(userRole);
                                    } else {
                                        userRole = assignedRole;

                                    }

                                    Utils.saveState("userRole", userRole);
                                    userData.setUserRole(userRole);
                                    saveToDb(userDao, userData);

                                    // Show Snackbar after role is determined
                                    CustomSnackbar.show(findViewById(android.R.id.content),
                                            "Welcome, " + name + "!",
                                            Snackbar.LENGTH_SHORT, 5);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    userRole = "Mentee"; // Default fallback
                                    userData.setUserRole(userRole);
                                    saveToDb(userDao, userData);
                                    CustomSnackbar.show(findViewById(android.R.id.content),
                                            "Welcome, " + name + "!",
                                            Snackbar.LENGTH_SHORT, 5);
                                }
                            });
                } else {
                    // If userRole is already set
                    Utils.saveState("userRole", userRole);
                    userData.setUserRole(userRole); // Add these missing lines
                    saveToDb(userDao, userData);
                    CustomSnackbar.show(findViewById(android.R.id.content),
                            "Welcome, " + name + "!",
                            Snackbar.LENGTH_SHORT, 5);
                }
            }
        } else {
            // todo cache user data in room for a week before logging them out
            boolean keepMeIn = Utils.getState("keepMeIn", false);
            long cacheDurationDays = keepMeIn ? 7L : 3L;
            long cacheValidDuration = cacheDurationDays * 24 * 60 * 60 * 1000;

            FirebaseUserHelper.INSTANCE.getCurrentUserAndData(userData -> {
                if (userData != null) {
                    // Use the userData here
                    String email = userData.getEmail();
                    String name = userData.getDisplayName();
                    String firstName = userData.getFirstName();
                    String lastName = userData.getLastName();
                    String photoUrl = userData.getPhotoUrl();
                    String id = userData.getId();
                    String lastLogin = userData.getLastLogin();

                    OverlapImageListView overlapImage = findViewById(R.id.overlapImage);

                    ArrayList<Bitmap> imageList = new ArrayList<>();

                    List<Integer> imageResourceList = new ArrayList<>();
                    imageResourceList.add(R.drawable.ic_check_green);
                    imageResourceList.add(R.drawable.ic_google);
                    imageResourceList.add(R.drawable.ic_facebook);

                    for (int i = 0; i < imageResourceList.size(); i++) {
                        int resId = imageResourceList.get(i);
                        Glide.with(this)
                                .asBitmap()
                                .load(resId)
                                .apply(RequestOptions.circleCropTransform())
                                .into(new CustomTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(@NonNull Bitmap resource,
                                                                @Nullable Transition<? super Bitmap> transition) {
                                        imageList.add(resource);

                                        // set the image after everything is loaded
                                        if (imageList.size() == imageResourceList.size()) {
                                            overlapImage.setImageList(imageList);
                                        }
                                    }

                                    @Override
                                    public void onLoadCleared(@Nullable Drawable placeholder) {
                                        // no-op
                                    }
                                });
                    }
                } else {
                    Log.d("User", "No user data found.");
                    startActivity(new Intent(MainActivity.this, LoginSignUpActivity.class));
                    finish();
                }
            });

        }
    }

    @SuppressLint("CheckResult")
    private void saveToDb(UserDao userDao, UserData userData) {
        userDao.insertUserRx(userData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    // success
                    Log.d("DB", "User inserted");
                }, throwable -> {
                    // error
                    Log.e("DB", "Insert failed", throwable);
                });
    }
}
