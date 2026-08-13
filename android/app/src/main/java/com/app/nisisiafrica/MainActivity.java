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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import com.app.nisisiafrica.CreateCommunityActivity;
import com.app.nisisiafrica.EditProfileActivity;
import com.app.nisisiafrica.Fragments.BaseFragments.ChatFragment;
import com.app.nisisiafrica.Fragments.BaseFragments.CommunitiesFragment;
import com.app.nisisiafrica.Fragments.BaseFragments.HomeFragment;
import com.app.nisisiafrica.Fragments.BaseFragments.ProfileFragment;
import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.Interfaces.SnackbarHandler;
import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.Worker.BookingWorker;
import com.app.nisisiafrica.Worker.EventReminderWorker;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.local.Dao.UserDao;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

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
    private HomeFragment homeFragment;
    private ChatFragment chatFragment;
    private CommunitiesFragment communitiesFragment;
    private ProfileFragment profileFragment;
    private String userRole, currentUser;
    private UserData userData, cachedUserData;
    private UserDao userDao;
    private Intent intent;
    private UserViewModel sharedUserViewModel1;
    private FragmentManager fragmentManager;
    private Fragment currentlyDisplayedFragment = null;
    //todo init viewmodel in application class
    private int currentTabId = R.id.homeFragment;
    private ImageView fabIcon;
    private View bottomBarRow;
    private View fabCard;
    private ImageView navHomeIcon, navGroupsIcon, navChatIcon, navProfileIcon;
    private TextView navHomeLabel, navGroupsLabel, navChatLabel, navProfileLabel;
    /** True while a conversation is open inside ChatFragment — hides the create-chat FAB. */
    private boolean chatConversationOpen = false;

    private int insetLeft, insetTop, insetRight, insetBottom;

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
            insetLeft = systemBars.left;
            insetTop = systemBars.top;
            insetRight = systemBars.right;
            insetBottom = systemBars.bottom;
            applyMainInsets();
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
        // Dark-mode toggle recreates the activity. FragmentManager restores the
        // previous fragments — adding them again stacks a second Home on top.
        resolveFragments(savedInstanceState);
//TODO: show dialog fragment once everyday  new FullscreenDialogFragment(this).show();

        setupBlurBars();
        setupBottomNav();
        selectTab(currentTabId, false);

        //fcm init
        initFCM();

        getUserBookedDates(this);
    }

    /**
     * Binds the four tab fragments. On a cold start we create + preload them;
     * after a config change (e.g. dark-mode toggle) we reuse the ones the
     * FragmentManager already restored so we never stack duplicates.
     */
    private void resolveFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            communitiesFragment = new CommunitiesFragment();
            chatFragment = new ChatFragment();
            profileFragment = new ProfileFragment();

            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.add(R.id.fragmentContainer, homeFragment, "HOME_FRAGMENT");
            ft.add(R.id.fragmentContainer, communitiesFragment, "COMMUNITIES_FRAGMENT");
            ft.add(R.id.fragmentContainer, chatFragment, "CHAT_FRAGMENT");
            ft.add(R.id.fragmentContainer, profileFragment, "PROFILE_FRAGMENT");
            ft.hide(communitiesFragment);
            ft.hide(chatFragment);
            ft.hide(profileFragment);
            ft.commitNow();
            currentlyDisplayedFragment = homeFragment;
            currentTabId = R.id.homeFragment;
            return;
        }

        homeFragment = (HomeFragment) fragmentManager.findFragmentByTag("HOME_FRAGMENT");
        communitiesFragment = (CommunitiesFragment) fragmentManager.findFragmentByTag("COMMUNITIES_FRAGMENT");
        chatFragment = (ChatFragment) fragmentManager.findFragmentByTag("CHAT_FRAGMENT");
        profileFragment = (ProfileFragment) fragmentManager.findFragmentByTag("PROFILE_FRAGMENT");
        if (homeFragment == null) homeFragment = new HomeFragment();
        if (communitiesFragment == null) communitiesFragment = new CommunitiesFragment();
        if (chatFragment == null) chatFragment = new ChatFragment();
        if (profileFragment == null) profileFragment = new ProfileFragment();

        if (homeFragment.isVisible()) {
            currentlyDisplayedFragment = homeFragment;
            currentTabId = R.id.homeFragment;
        } else if (communitiesFragment.isVisible()) {
            currentlyDisplayedFragment = communitiesFragment;
            currentTabId = R.id.communitiesFragment;
        } else if (chatFragment.isVisible()) {
            currentlyDisplayedFragment = chatFragment;
            currentTabId = R.id.chatFragment;
        } else if (profileFragment.isVisible()) {
            currentlyDisplayedFragment = profileFragment;
            currentTabId = R.id.profileFragment;
        } else {
            currentlyDisplayedFragment = homeFragment;
            currentTabId = R.id.homeFragment;
            replaceFragment(homeFragment);
        }
    }

    private void replaceFragment(Fragment fragmentToShow) {
        if (fragmentToShow == null) return;
        if (fragmentToShow == currentlyDisplayedFragment && fragmentToShow.isVisible()) return;

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        if (currentlyDisplayedFragment != null && currentlyDisplayedFragment.isAdded()) {
            fragmentTransaction.hide(currentlyDisplayedFragment);
        }

        if (fragmentToShow.isAdded()) {
            fragmentTransaction.show(fragmentToShow);
        } else {
            String tag = tagFor(fragmentToShow);
            fragmentTransaction.add(R.id.fragmentContainer, fragmentToShow, tag);
        }

        fragmentTransaction.commitNowAllowingStateLoss();
        currentlyDisplayedFragment = fragmentToShow;
    }

    private String tagFor(Fragment f) {
        if (f == homeFragment) return "HOME_FRAGMENT";
        if (f == communitiesFragment) return "COMMUNITIES_FRAGMENT";
        if (f == chatFragment) return "CHAT_FRAGMENT";
        if (f == profileFragment) return "PROFILE_FRAGMENT";
        return f.getClass().getSimpleName();
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

    /** Translucent (blurred) bottom bar — keep blur; do not hide on scroll. */
    private void setupBlurBars() {
        bottomBarRow = findViewById(R.id.bottomBarRow);
        fabCard = findViewById(R.id.fabCard);
        fabIcon = findViewById(R.id.fabIcon);

        eightbitlab.com.blurview.BlurTarget target = findViewById(R.id.blurTarget);
        eightbitlab.com.blurview.BlurView navBlur = findViewById(R.id.navBlur);
        int overlay = ContextCompat.getColor(this, R.color.blur_overlay);
        try {
            navBlur.setupWith(target).setBlurRadius(22f).setOverlayColor(overlay);
        } catch (Exception e) {
            Log.w(TAG, "Blur setup failed; falling back to solid bars", e);
            int solid = ContextCompat.getColor(this, R.color.surface_card);
            navBlur.setBackgroundColor(solid);
        }
    }

    private void setupBottomNav() {
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navGroupsIcon = findViewById(R.id.navGroupsIcon);
        navChatIcon = findViewById(R.id.navChatIcon);
        navProfileIcon = findViewById(R.id.navProfileIcon);
        navHomeLabel = findViewById(R.id.navHomeLabel);
        navGroupsLabel = findViewById(R.id.navGroupsLabel);
        navChatLabel = findViewById(R.id.navChatLabel);
        navProfileLabel = findViewById(R.id.navProfileLabel);

        findViewById(R.id.homeFragment).setOnClickListener(v -> selectTab(R.id.homeFragment, true));
        findViewById(R.id.communitiesFragment).setOnClickListener(v -> selectTab(R.id.communitiesFragment, true));
        findViewById(R.id.chatFragment).setOnClickListener(v -> selectTab(R.id.chatFragment, true));
        findViewById(R.id.profileFragment).setOnClickListener(v -> selectTab(R.id.profileFragment, true));
        findViewById(R.id.fabCard).setOnClickListener(v -> onContextualFabClicked());
    }

    private void selectTab(int tabId, boolean switchFragment) {
        if (switchFragment) {
            if (tabId == R.id.homeFragment) {
                replaceFragment(homeFragment);
            } else if (tabId == R.id.communitiesFragment) {
                replaceFragment(communitiesFragment);
            } else if (tabId == R.id.chatFragment) {
                replaceFragment(chatFragment);
            } else if (tabId == R.id.profileFragment) {
                replaceFragment(profileFragment);
            }
        }
        currentTabId = tabId;
        applyNavSelection(tabId);
    }

    private void applyNavSelection(int tabId) {
        int active = ContextCompat.getColor(this, R.color.maroon_700);
        int inactive = ContextCompat.getColor(this, R.color.muted);
        tintNav(navHomeIcon, navHomeLabel, tabId == R.id.homeFragment, active, inactive);
        tintNav(navGroupsIcon, navGroupsLabel, tabId == R.id.communitiesFragment, active, inactive);
        tintNav(navChatIcon, navChatLabel, tabId == R.id.chatFragment, active, inactive);
        tintNav(navProfileIcon, navProfileLabel, tabId == R.id.profileFragment, active, inactive);
    }

    private void tintNav(ImageView icon, TextView label, boolean selected, int active, int inactive) {
        int color = selected ? active : inactive;
        if (icon != null) icon.setColorFilter(color);
        if (label != null) label.setTextColor(color);
    }

    private void applyMainInsets() {
        View main = findViewById(R.id.main);
        if (main == null) return;
        // Chat detail paints under the status bar for a continuous glass header.
        int top = chatConversationOpen ? 0 : insetTop;
        main.setPadding(insetLeft, top, insetRight, insetBottom);
    }

    /** Called by ChatFragment when entering/leaving a conversation. */
    public void setChatConversationOpen(boolean open) {
        chatConversationOpen = open;
        if (bottomBarRow != null) {
            bottomBarRow.setVisibility(open ? View.GONE : View.VISIBLE);
        }
        applyMainInsets();
    }

    private void onContextualFabClicked() {
        if (currentTabId == R.id.homeFragment) {
            if (isMentorOrAdmin()) {
                homeFragment.showCreateSheet();
            }
        } else if (currentTabId == R.id.communitiesFragment) {
            if (isMentorOrAdmin()) {
                startActivity(new Intent(this, CreateCommunityActivity.class));
            }
        } else if (currentTabId == R.id.chatFragment) {
            if (isMentorOrAdmin() && !chatConversationOpen) {
                chatFragment.showNewChatPicker();
            }
        } else if (currentTabId == R.id.profileFragment) {
            Intent i = new Intent(this, EditProfileActivity.class);
            i.putExtra(Constants.CURRENT_USER_ID, Util.getState(Constants.CURRENT_USER_ID, ""));
            startActivity(i);
        }
    }

    private boolean isMentorOrAdmin() {
        String role = userRole != null ? userRole : Roles.current();
        return Roles.canCreate(role);
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
        selectTab(R.id.homeFragment, true);
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
                    userRole = remoteUserData.getUserRole();
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
        CharSequence name = "Nelsen Notifications";
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

        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                EventReminderWorker.class,
                isMentor ? 1 : 6,
                TimeUnit.HOURS
        ).setConstraints(constraints).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "event_reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest);

        // Run once now so reminders are scheduled without waiting for the period.
        WorkManager.getInstance(context).enqueue(
                new androidx.work.OneTimeWorkRequest.Builder(EventReminderWorker.class)
                        .setConstraints(constraints)
                        .build());
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
        // Bottom bar stays visible; blur/transparency only — no scroll hide.
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
