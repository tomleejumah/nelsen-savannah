package com.app.nisisiafrica;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.app.nisisiafrica.data.local.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.app.nisisiafrica.Utils.Util;

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
