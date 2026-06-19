package com.app.nisisiafrica;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.app.nisisiafrica.Fragments.BaseFragments.ChatFragment;
import com.app.nisisiafrica.Fragments.BaseFragments.HomeFragment;
import com.app.nisisiafrica.Fragments.BaseFragments.SettingsFragment;
import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.Interfaces.SnackbarHandler;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.Worker.BookingWorker;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.local.Dao.UserDao;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
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
    private static final String CHANNEL_ID = "nisisi_notifications";
    private static int REQUEST_CODE_NOTIFICATIONS = 210;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final HomeFragment homeFragment = new HomeFragment();
    private final ChatFragment chatFragment = new ChatFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    private String userRole, currentUser;
    private UserData userData, cachedUserData;
    private UserDao userDao;
    private Intent intent;
    private UserViewModel sharedUserViewModel1;
    private FragmentManager fragmentManager;
    private Fragment currentlyDisplayedFragment = null;
    //todo init viewmodel in application class
    private FabToBottomNavigationAnim fabToBottomNavigationAnim;
    private FloatingActionButton fabView;

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

        requestNotificationPermission();

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


       fabView .setShapeAppearanceModel(
                fabView.getShapeAppearanceModel()
                        .toBuilder()
                        .setAllCornerSizes(100f)
                        .build()
        );

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
        fragmentTransaction.add(R.id.fragmentContainer, homeFragment, "HOME_FRAGMENT");
        fragmentTransaction.add(R.id.fragmentContainer, chatFragment, "CHAT_FRAGMENT");
        fragmentTransaction.add(R.id.fragmentContainer, settingsFragment, "SETTINGS_FRAGMENT");
        fragmentTransaction.hide(chatFragment);
        fragmentTransaction.hide(settingsFragment);
        fragmentTransaction.commitNow();
    }

//    @Override
//    public boolean onSupporcoursestNavigateUp() {
//        return navController.navigateUp() || super.onSupportNavigateUp();
//    }

    private void replaceFragment(Fragment fragmentToShow) {
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        if (currentlyDisplayedFragment != null) {
            fragmentTransaction.hide(currentlyDisplayedFragment);
        }

        // Show the new fragment
        if (fragmentToShow.isAdded()) {
            fragmentTransaction.show(fragmentToShow);
        } else {
            fragmentTransaction.add(R.id.fragmentContainer, fragmentToShow);
        }

        fragmentTransaction.commit();
        currentlyDisplayedFragment = fragmentToShow;
    }
    private void initFCM() {
        SharedPreferences prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE);

        if (prefs.getBoolean("initial_token_written", false)) {
            return;
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    String userId = Util.getState(Constants.CURRENT_USER_ID, "");
                    if (userId.isEmpty()) return;

                    FirebaseDatabase.getInstance()
                            .getReference("Tokens")
                            .child(userId)
                            .setValue(token)
                            .addOnSuccessListener(v ->
                                    prefs.edit()
                                            .putBoolean("initial_token_written", true)
                                            .apply()
                            );
                });
    }

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

    public void navigateToHomeTab() {
        ChipNavigationBar chipNavigationBar = findViewById(R.id.chipNavigationBar);
        if (chipNavigationBar != null) {
            chipNavigationBar.setItemSelected(R.id.homeFragment, true);
        }
        replaceFragment(homeFragment);
    }

    private void handleCachedUser() {
        boolean keepMeIn = Util.getState("keepMeIn", false);
        long cacheDurationDays = keepMeIn ? 7L : 3L;
        long cacheValidDuration = cacheDurationDays * 24 * 60 * 60 * 1000;
        long lastAppBackground = Util.getState("lastAppBackground", System.currentTimeMillis());
        long currentTime = System.currentTimeMillis();
        boolean cacheExpired = currentTime - lastAppBackground > cacheValidDuration;

        if (cacheExpired) {
            try {
                boolean hasPin = PinManager.hasPin(this, currentUser);
                if (!hasPin || !keepMeIn) {
                    Log.d(TAG, "Cache expired — logging out (hasPin=" + hasPin + ", keepMeIn=" + keepMeIn + ")");
                    logoutAndRedirect();
                    return;
                }
                Log.d(TAG, "Cache expired but PIN + keepMeIn active — refreshing data");
            } catch (Exception e) {
                Log.e(TAG, "PIN check failed during cache expiry", e);
                logoutAndRedirect();
                return;
            }
        }

        syncUserDataFromRemote();
    }

    private void syncUserDataFromRemote() {
        sharedUserViewModel1.fetchingCurrentUserDataFromDB(currentUser).observe(this, localUserData -> {
            FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(currentUser, remoteUserData -> {
                if (remoteUserData != null) {
                    userData = remoteUserData;
                    if (localUserData == null || !localUserData.equals(remoteUserData)) {
                        sharedUserViewModel1.updateUserData(remoteUserData);
                    } else {
                        sharedUserViewModel1.setUserData(localUserData);
                    }
                    Util.saveState(Constants.USER_ROLE, remoteUserData.getUserRole() != null
                            ? remoteUserData.getUserRole() : "Mentee");
                } else if (localUserData != null) {
                    userData = localUserData;
                    sharedUserViewModel1.setUserData(localUserData);
                } else {
                    Log.d(TAG, "No local or remote user data — redirecting to login");
                    redirectToLogin();
                }
                return Unit.INSTANCE;
            }, e -> {
                Log.e(TAG, "Remote user fetch failed", e);
                if (localUserData != null) {
                    userData = localUserData;
                    sharedUserViewModel1.setUserData(localUserData);
                } else {
                    redirectToLogin();
                }
                return Unit.INSTANCE;
            });
        });
    }

    private void logoutAndRedirect() {
        String userId = Util.getState(Constants.CURRENT_USER_ID, "");
        disposables.add(userDao.deleteUserByIdRx(userId)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Log.d(TAG, "User cache deleted"),
                        error -> Log.e(TAG, "Failed to delete user cache", error)
                ));
        FirebaseRemoteDataSource.INSTANCE.signOutAll(this, () -> {
            redirectToLogin();
            return Unit.INSTANCE;
        });
    }

    //todo create multiple channels based with action also migrate them to enum class
    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATIONS);
            }
        } else createNotificationChannel();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createNotificationChannel();
            } else {
                CustomSnackbar.show(MainActivity.this, "To receive updates consider " +
                        "enabling notifications", Snackbar.LENGTH_LONG, 4);
            }
        }
    }


    private void createNotificationChannel() {
        CharSequence name = "Nisisi Notifications";
        String description = "Notifications for likes, comments, and messages";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager =
                getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void getUserBookedDates(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build();

        boolean isMentor = "Mentor".equals(userRole) || Util.getState(Constants.USER_ROLE, "Mentee").equals("Mentor");

        PeriodicWorkRequest periodicWorkRequest;
        if (isMentor) {

            periodicWorkRequest = new PeriodicWorkRequest.Builder(
                    BookingWorker.class,
                    1,
                    TimeUnit.HOURS
            ).setConstraints(constraints).build();
        } else {
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
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onParentScroll(int oldY, int newY) {
        if ((oldY > newY)) {
            showBottomBar();
        } else {
            hideBottomBar();
        }
    }

    @Override
    public void onUserDataReceived(@Nullable UserData fetchedUserData) {


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
