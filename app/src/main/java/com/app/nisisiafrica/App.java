package com.app.nisisiafrica;

import android.app.Application;
import android.content.Intent;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.app.nisisiafrica.data.local.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Utils.Util;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import me.didit.sdk.DiditSdk;

public class App extends Application {

    /**
    todo : for entire App
        1) Theme toggling (dark mode etc)
        2) View Model for all Activities/fragment(use the shared view model) global init of the view model
        2b) Repositories scoped on feature/domain
        2c) DaggerHilt
        3) Caching of data(to much repetion of init in firebase remote helper)
        4) Pagination Imp for courses,mentors,Chats == done(chats remaining)
        5) Backup to google
        6) Notifications and alarms
        7) Sync with calender(google)
        8) Add FCM for Notifications for the app
     **/

    private static AppDatabase appDatabase;
    @Override
    public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        Util.init(this);
        appDatabase = AppDatabase.getInstance(this);
        DiditSdk.INSTANCE.initialize(this);
        ProcessLifecycleOwner.get().getLifecycle()
                .addObserver(new AppLifecycleObserver(this));

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForeground() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        try {
            if (PinManager.hasPin(this, user.getUid()) && !LockScreenActivity.AppLockState.isUnlocked()) {
                Intent intent = new Intent(this, LockScreenActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackground() {
        LockScreenActivity.AppLockState.lock();
    }
    public static AppDatabase getAppDatabase() {
        return appDatabase;
    }

    public static UserDao getUserDao() {
        return appDatabase.userDao();
    }
}
