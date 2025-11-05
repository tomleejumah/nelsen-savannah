package com.app.nisisiafrica;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.app.customsnackbarlib.CustomSnackbar;
import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Worker.BookingWorker;
import com.app.nisisiafrica.data.local.Dao.UserDao;
import com.app.nisisiafrica.Fragments.BaseFragments.ChatFragment;
import com.app.nisisiafrica.Fragments.BaseFragments.HomeFragment;
import com.app.nisisiafrica.Fragments.BaseFragments.SettingsFragment;
import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.app.nisisiafrica.Interfaces.SnackbarHandler;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.ismaeldivita.chipnavigation.ChipNavigationBar;
import com.trinitymirror.fabtobottomnavigation.FabToBottomNavigationAnim;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;

public class MainActivity extends AppCompatActivity implements HomeFragment.onScrollChangeListener, FirebaseCallback {
    private static final String TAG = "MainActivity";
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final HomeFragment homeFragment = new HomeFragment();
    private final ChatFragment chatFragment = new ChatFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    private String userRole, currentUser;
    private UserData userData,cachedUserData;
    private UserDao userDao;
    private Intent intent;
    private UserViewModel sharedUserViewModel1;
    private FragmentManager fragmentManager;
    private Fragment currentlyDisplayedFragment = null;
    private FabToBottomNavigationAnim fabToBottomNavigationAnim;
    private FloatingActionButton fabView;
    //todo init viewmodel in application class

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        userDao = App.getUserDao();

        // Check for logged-in user
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            redirectToLogin();
            return;
        }
        currentUser = firebaseUser.getUid();
        Util.saveState(Constants.CURRENT_USER_ID, currentUser);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sharedUserViewModel1 = new ViewModelProvider(this).get(UserViewModel.class);
        intent = getIntent();
        boolean isFromAuth = intent.getBooleanExtra("IS_FROM_AUTH", false);
        if (isFromAuth) {
            //handle fresh data
            sharedUserViewModel1.fetchingCurrentUserDataFromDB(currentUser).observe(this, this::handleFreshUserData);
        } else handleCachedUser();

        fragmentManager = getSupportFragmentManager();
        preloadAllFragments();

        if (savedInstanceState == null) {
            replaceFragment(homeFragment);
        }
//TODO: show dialog fragment once everyday  new FullscreenDialogFragment(this).show();

        CardView cardChipNavigation = findViewById(R.id.cardChipNavigation);
        fabView = findViewById(R.id.fab);
        ChipNavigationBar chipNavigationBar = findViewById(R.id.chipNavigationBar);
        chipNavigationBar.setItemSelected(R.id.homeFragment, true);

        chipNavigationBar.setOnItemSelectedListener(i -> {
            if (i == R.id.homeFragment) {
                replaceFragment(homeFragment);
            } else if (i == R.id.chatFragment) {
                replaceFragment(chatFragment);
            } else if (i == R.id.settingsFragment) {
                replaceFragment(settingsFragment);
            }
        });

        fabToBottomNavigationAnim = new FabToBottomNavigationAnim(fabView, cardChipNavigation);

        fabView.setOnClickListener(v -> {
            fabToBottomNavigationAnim.showNavigationView();
        });

        //fcm init
        initFCM();

        getUserBookedDates(this);
    }
    private void preloadAllFragments() {
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.navHostFragment, homeFragment, "HOME_FRAGMENT");
        fragmentTransaction.add(R.id.navHostFragment, chatFragment, "CHAT_FRAGMENT");
        fragmentTransaction.add(R.id.navHostFragment, settingsFragment, "SETTINGS_FRAGMENT");
        fragmentTransaction.hide(chatFragment);
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

    private void initFCM() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "Token fetch failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    String userId = Util.getState(Constants.CURRENT_USER_ID, "");

                    if (!userId.isEmpty()) {
                        DatabaseReference ref = FirebaseDatabase.getInstance()
                                .getReference("Tokens")
                                .child(userId);
                        ref.setValue(token)
                                .addOnSuccessListener(aVoid -> Log.d("FCM", "Token saved"))
                                .addOnFailureListener(e -> Log.e("FCM", "Token save failed", e));
                    }
                });
    }

//    @Override
//    public boolean onSupporcoursestNavigateUp() {
//        return navController.navigateUp() || super.onSupportNavigateUp();
//    }

    public void hideBottomBar() {
        fabToBottomNavigationAnim.hideNavigationView();
    }

    public void showBottomBar() {
        fabToBottomNavigationAnim.showNavigationView();
    }

    private void handleFreshUserData(UserData userData) {
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
        Util.saveState(Constants.USER_ROLE, userRole);
        userData.setUserRole(userRole);
        sharedUserViewModel1.updateUserData(userData);
        SnackbarHandler snackbarHandler = (message, duration, type) -> {
            CustomSnackbar.show(MainActivity.this, message, duration, type);
        };
        snackbarHandler.showSnackbar("Welcome, " + userData.getFirstName() + "!", Snackbar.LENGTH_LONG, 4);
    }

    private void handleCachedUser() {
        boolean keepMeIn = Util.getState("keepMeIn", false);
        long cacheDurationDays = keepMeIn ? 7L : 3L;
        long cacheValidDuration = cacheDurationDays * 24 * 60 * 60 * 1000;
        long lastAppBackground = Util.getState("lastAppBackground", System.currentTimeMillis());
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastAppBackground > cacheValidDuration) {
            Log.d(TAG, "Cache expired, logging out user");
            disposables.add(userDao.deleteUserByIdRx(userData.getId())
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            () -> Log.d(TAG, "User cache deleted"),
                            error -> Log.e(TAG, "Failed to delete user cache", error)
                    ));

            FirebaseRemoteDataSource.INSTANCE.signOutAll(this, () -> {
                redirectToLogin();
                return Unit.INSTANCE;
            });
            return;
        }

        sharedUserViewModel1.fetchingCurrentUserDataFromDB(currentUser).observe(this, userData -> {
            cachedUserData = userData;
            FirebaseRemoteDataSource.INSTANCE.getUserAndData(MainActivity.this);
        });

    }

    private void getUserBookedDates(Context context){
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();

        boolean isMentor ="Mentor".equals(userRole) || Util.getState(Constants.USER_ROLE, "Mentee").equals("Mentor");

        PeriodicWorkRequest periodicWorkRequest;
        if (isMentor) {

            periodicWorkRequest = new PeriodicWorkRequest.Builder(
                    BookingWorker.class,
                    1,
                    TimeUnit.HOURS
            ).setConstraints(constraints).build();
        }else {
            periodicWorkRequest = new PeriodicWorkRequest.Builder(
                    BookingWorker.class,
                    1,
                    TimeUnit.DAYS).setConstraints(constraints).build();
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "periodic_backup",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onParentScroll(int oldY, int newY) {
//        if (oldY>newY)
        if ((oldY > newY)) {
//            showBottomBar();
        } else {
//            hideBottomBar();
        }

    }

    @Override
    public void onUserDataReceived(@Nullable UserData fetchedUserData) {
        if (fetchedUserData != null) {
            userData = !cachedUserData.equals(fetchedUserData)
                    ? fetchedUserData : cachedUserData;

            if (!userData.equals(cachedUserData)) {
                sharedUserViewModel1.updateUserData(userData);
            }
            //will update last login todo
        Log.d(TAG, "handleCachedUser: updating cache with " + (cachedUserData == null ? "fetched" : "cached") + " data");
        } else {
            Log.d(TAG, "No fetched data available...re using cached data");
            if (cachedUserData != null) {
                userData = cachedUserData;
                sharedUserViewModel1.setUserData(cachedUserData);
                Log.d(TAG, "Using cached data as fallback");
            } else {
                Log.d(TAG, "No cached or cloud data, redirecting to login");
                redirectToLogin();
            }
        }

    }

    @Override
    public void onMentorDataFetched(@Nullable MentorItem mentors) {
    }

    @Override
    public void onMentorsIDFetched(@Nullable List<String> mentorIds) {
    }

    @Override
    public void onCoursesFetched(@Nullable List<CourseItem> courses) {
        Log.d(TAG, "onCoursesFetched: ");
    }

    @Override
    public void onMentorsFetched(@NotNull List<@NotNull MentorItem> mentors) {
    }

    @Override
    public void onError(@Nullable Exception e) {
        Log.e(TAG, "Error fetching data...Ru using cached data", e);
    }
}
