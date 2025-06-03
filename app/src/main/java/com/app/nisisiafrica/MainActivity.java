package com.app.nisisiafrica;

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
import com.app.nisisiafrica.Auth.UserDataCallback;
import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Model.UserData;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;

public class MainActivity extends ComponentActivity {
    private static final String TAG = "MainActivity";
    private final CompositeDisposable disposables = new CompositeDisposable(); // For RxJava cleanup
    private String userRole, currentUser;
    private UserData userData;
    private UserDao userDao;
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize database
        AppDatabase appDatabase = AppDatabase.getInstance(this);
        userDao = appDatabase.userDao();

        // Check for logged-in user
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Log.e(TAG, "No logged-in user, redirecting to login");
            redirectToLogin();
            return;
        }
        currentUser = firebaseUser.getUid();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        intent = getIntent();
        if (intent != null && intent.hasExtra("USER_DATA")) {
            userData = intent.getParcelableExtra("USER_DATA");
            handleFreshUserData(userData);
        } else {
            handleCachedUser();
        }
        overlapImage();
    }

    private void handleFreshUserData(UserData userData) {
        if (userData == null) {
            Log.e(TAG, "UserData is null, redirecting to login");
            redirectToLogin();
            return;
        }

        userRole = userData.getUserRole();

        if (TextUtils.isEmpty(userRole)) {
            DatabaseReference roleRef = FirebaseDatabase.getInstance().getReference("roles/" + currentUser);
            Disposable disposable = Single.create((SingleEmitter<DataSnapshot> emitter) ->
                            roleRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    emitter.onSuccess(snapshot);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    emitter.onError(error.toException());
                                }
                            })).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            snapshot -> {
                                String assignedRole = snapshot.getValue(String.class);
                                userRole = TextUtils.isEmpty(assignedRole) ? "Mentee" : assignedRole;

                                if (TextUtils.isEmpty(assignedRole)) {
                                    FirebaseDatabase.getInstance().getReference("roles")
                                            .child(currentUser)
                                            .setValue(userRole);
                                }

                                updateUserDataAndShowWelcome(userData);
                            },
                            error -> {
                                Log.e(TAG, "Failed to fetch role", error);
                                userRole = "Mentee"; // Default fallback
                                updateUserDataAndShowWelcome(userData);
                            }
                    );
            disposables.add(disposable);
        } else {
            updateUserDataAndShowWelcome(userData);
        }
    }

    private void updateUserDataAndShowWelcome(UserData userData) {
        Utils.saveState("userRole", userRole);
        userData.setUserRole(userRole);
        saveToDb(userData);
        CustomSnackbar.show(findViewById(android.R.id.content),
                "Welcome, " + userData.getFirstName() + "!",
                Snackbar.LENGTH_SHORT, 5);
    }

    private void handleCachedUser() {
        boolean keepMeIn = Utils.getState("keepMeIn", false);
        long cacheDurationDays = keepMeIn ? 7L : 3L;
        long cacheValidDuration = cacheDurationDays * 24 * 60 * 60 * 1000;
        long lastAppBackground = Utils.getState("lastAppBackground", System.currentTimeMillis());
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAppBackground > cacheValidDuration) {
            Log.d(TAG, "Cache expired, logging out user");
            disposables.add(userDao.deleteUserByIdRx(currentUser)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            () -> Log.d(TAG, "User cache deleted"),
                            error -> Log.e(TAG, "Failed to delete user cache", error)
                    ));

            FirebaseUserHelper.INSTANCE.signOutAll(this, () -> {
                redirectToLogin();
                return Unit.INSTANCE;
            });
            return;
        }

       Log.d(TAG, "About to call getUserByIdRx with currentUser: " + currentUser);
        disposables.add(userDao.getUserByIdRx(currentUser)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        cachedUserData -> {
                            Log.d(TAG, "Using cached data");

                            fetchAndCompareUserData(cachedUserData);

                        },
                        error -> {
                            Log.e(TAG, "Error fetching cached user", error);
                            fetchAndCompareUserData(null);
                        },
                        () -> {
                            // onComplete - no data found (Maybe completed without emitting)
                            Log.d(TAG, "No cached data found");
                            fetchAndCompareUserData(null);
                        }
                ));
    }

    private void fetchAndCompareUserData(UserData cachedUserData) {
        FirebaseUserHelper.INSTANCE.getCurrentUserAndData(fetchedUserData -> {
            if (fetchedUserData != null) {
                userData = (cachedUserData == null || !cachedUserData.equals(fetchedUserData))
                        ? fetchedUserData : cachedUserData;
                updateDb(userData);
                Log.d(TAG, "handleCachedUser: updating cache with " + (cachedUserData == null ? "fetched" : "cached") + " data");
            } else {
                    Log.d(TAG, "No fetched data available");
                if (cachedUserData != null) {
                    userData = cachedUserData;
                    Log.d(TAG, "Using cached data as fallback");
                } else {
                    Log.d(TAG, "No cached or cloud data, redirecting to login");
                    redirectToLogin();
                }
            }
        });
    }

    private void saveToDb(UserData userData) {
        disposables.add(userDao.insertUserRx(userData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Log.d(TAG, "User inserted into DB"),
                        throwable -> Log.e(TAG, "Insert failed", throwable)
                ));
    }
    private void updateDb(UserData userData) {
        disposables.add(userDao.updateUserRx(userData)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Log.d(TAG, "User updated in DB"),
                        throwable -> Log.e(TAG, "Failed to update user in DB", throwable)
                ));
    }
    private void redirectToLogin() {
        startActivity(new Intent(this, LoginSignUpActivity.class));
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save app background time
        Utils.saveState("lastAppBackground", System.currentTimeMillis());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    private void overlapImage() {
        if (isDestroyed() || isFinishing()) return;
        OverlapImageListView overlapImage = findViewById(R.id.overlapImage);

        ArrayList<Bitmap> imageList = new ArrayList<>();

        List<Integer> imageResourceList = new ArrayList<>();
        imageResourceList.add(R.drawable.ic_check_green);
        imageResourceList.add(R.drawable.ic_google);
        imageResourceList.add(R.drawable.ic_facebook);

        for (int i = 0; i < imageResourceList.size(); i++) {
            int resId = imageResourceList.get(i);
            Glide.with(MainActivity.this)
                    .asBitmap()
                    .load(resId)
                    .apply(RequestOptions.circleCropTransform())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            if (isDestroyed() || isFinishing()) return;
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
    }
}
