package com.app.nisisiafrica;

import android.app.Application;

import androidx.lifecycle.ProcessLifecycleOwner;

import com.app.nisisiafrica.data.local.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Utils.ThemeManager;
import com.app.nisisiafrica.Utils.Util;

import me.didit.sdk.DiditSdk;

public class App extends Application {

    private static AppDatabase appDatabase;

    @Override
    public void onCreate() {
        super.onCreate();

        // Util must be initialised first: the theme preference is read from it.
        Util.init(this);
        ThemeManager.applyPersistedMode();
        appDatabase = AppDatabase.getInstance(this);
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
