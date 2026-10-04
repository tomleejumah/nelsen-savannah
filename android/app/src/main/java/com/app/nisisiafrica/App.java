package com.app.nisisiafrica;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.lifecycle.ProcessLifecycleOwner;

import com.app.nisisiafrica.data.local.Dao.UserDao;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.google.firebase.auth.FirebaseAuth;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Utils.LocaleHelper;
import com.app.nisisiafrica.Utils.NetworkStatusBanner;
import com.app.nisisiafrica.Utils.LmsStudySync;
import com.app.nisisiafrica.Utils.ThemeManager;
import com.app.nisisiafrica.Utils.Util;

import java.util.Map;
import java.util.WeakHashMap;

import me.didit.sdk.DiditSdk;

public class App extends Application {

    private static AppDatabase appDatabase;
    private final Map<Activity, NetworkStatusBanner> networkBanners = new WeakHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();

        // Util must be initialised first: the theme preference is read from it.
        Util.init(this);
        ThemeManager.applyPersistedMode();
        LocaleHelper.applyPersisted(this);
        appDatabase = AppDatabase.getInstance(this);
        LmsStudySync.INSTANCE.schedule(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                NetworkStatusBanner banner = networkBanners.get(activity);
                if (banner == null) {
                    banner = new NetworkStatusBanner(activity);
                    networkBanners.put(activity, banner);
                }
                banner.start();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                NetworkStatusBanner banner = networkBanners.get(activity);
                if (banner != null) banner.stop();
            }

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {
                NetworkStatusBanner banner = networkBanners.remove(activity);
                if (banner != null) banner.stop();
            }
        });
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            if (auth.getCurrentUser() != null) {
                FirebaseRemoteDataSource.INSTANCE.initSpecialChatRooms();
            }
        });
        DiditSdk.INSTANCE.initialize(this);
        ProcessLifecycleOwner.get().getLifecycle()
                .addObserver(new AppLifecycleObserver(this));
    }

    public static AppDatabase getAppDatabase() {
        return appDatabase;
    }

    public static UserDao getUserDao() {
        return appDatabase.userDao();
    }
}
