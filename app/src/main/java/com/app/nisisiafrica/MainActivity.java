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
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.app.customsnackbarlib.CustomSnackbar;
import com.app.nisisiafrica.Auth.FirebaseUserHelper;
import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Auth.UserDataCallback;
import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Fragments.HomeFragments.HomeFragment;
import com.app.nisisiafrica.Fragments.HomeFragments.NotificationsFragment;
import com.app.nisisiafrica.Fragments.HomeFragments.SettingsFragment;
import com.app.nisisiafrica.Model.UserData;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.ismaeldivita.chipnavigation.ChipNavigationBar;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private final CompositeDisposable disposables = new CompositeDisposable(); // For RxJava cleanup
    private String userRole, currentUser;
    private UserData userData;
    private UserDao userDao;
    private Intent intent;
    private SharedUserViewModel viewModel;
    private FragmentManager fragmentManager;
    private final HomeFragment homeFragment = new HomeFragment();
    private final NotificationsFragment notificationsFragment = new NotificationsFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    ChipNavigationBar chipNavigationBar;
    private Fragment currentlyDisplayedFragment = null;
    CardView cardChipNavigation;

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

//        intent = getIntent();
//        if (intent != null && intent.hasExtra("USER_DATA")) {
//            userData = intent.getParcelableExtra("USER_DATA");
//            handleFreshUserData(userData);
//        } else {
//            handleCachedUser();
//        }

        
        viewModel = new ViewModelProvider(this).get(SharedUserViewModel.class);
        viewModel.getUserData().observe(this, data -> {
            Log.d(TAG, "onCreate: weeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
            if (data != null) {
                handleFreshUserData(data);
            }else handleCachedUser();
        });

        fragmentManager = getSupportFragmentManager();
        preloadAllFragments();

            if (savedInstanceState == null) {
                replaceFragment(homeFragment);
            }

        cardChipNavigation = findViewById(R.id.cardChipNavigation);
        chipNavigationBar = findViewById(R.id.chipNavigationBar);
        chipNavigationBar.setItemSelected(R.id.homeFragment, true);

        chipNavigationBar.setOnItemSelectedListener(i -> {
            if (i == R.id.homeFragment) {
                replaceFragment(homeFragment);
            } else if (i == R.id.notificationsFragment) {
                replaceFragment(notificationsFragment);
            } else if (i == R.id.settingsFragment) {
                replaceFragment(settingsFragment);
            }
        });
    }

//    @Override
//    public boolean onSupportNavigateUp() {
//        return navController.navigateUp() || super.onSupportNavigateUp();
//    }

    private void preloadAllFragments() {
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.navHostFragment, homeFragment, "HOME_FRAGMENT");
        fragmentTransaction.add(R.id.navHostFragment, notificationsFragment, "NOTIFICATIONS_FRAGMENT");
        fragmentTransaction.add(R.id.navHostFragment, settingsFragment, "SETTINGS_FRAGMENT");
        fragmentTransaction.hide(notificationsFragment);
        fragmentTransaction.hide(settingsFragment);
        fragmentTransaction.commitNow();
    }

    private void replaceFragment(Fragment fragmentToShow) {
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        if (currentlyDisplayedFragment != null) {
            fragmentTransaction.hide(currentlyDisplayedFragment);
        }

        // Show the new fragment
        if (fragmentToShow.isAdded()) {
            fragmentTransaction.show(fragmentToShow);
        } else {
            fragmentTransaction.add(R.id.navHostFragment, fragmentToShow);
        }

        fragmentTransaction.commit();
        currentlyDisplayedFragment = fragmentToShow;
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
        viewModel.setUserData(userData);
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
                viewModel.setUserData(userData);

                Log.d(TAG, "handleCachedUser: updating cache with " + (cachedUserData == null ? "fetched" : "cached") + " data");
            } else {
                    Log.d(TAG, "No fetched data available");
                if (cachedUserData != null) {
                    userData = cachedUserData;
                    viewModel.setUserData(cachedUserData);
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

}
