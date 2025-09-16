package com.app.nisisiafrica;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Utils.Util;

public class App extends Application {
    /*
    todo : for entire App
    1)theme switch(dark mode etc)
    2)View Model for all Activities/fragment(use the shared view model) global init of the view model
    3) Caching of data
    4) Pagination Imp for courses,mentors,Chats
    5)Backup to google
    6)Notifications and alarms
    7)Sync with calender(google)
    8)
     */

    private static AppDatabase appDatabase;
    @Override
    public void onCreate() {
        super.onCreate();

        //todo switch dark and light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        Util.init(this);
        appDatabase = AppDatabase.getInstance(this);

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
