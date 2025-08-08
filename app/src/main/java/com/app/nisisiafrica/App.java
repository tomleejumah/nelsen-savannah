package com.app.nisisiafrica;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.app.nisisiafrica.Dao.UserDao;
import com.app.nisisiafrica.DataBase.AppDatabase;
import com.google.firebase.auth.FirebaseAuth;

public class App extends Application {
    private static AppDatabase appDatabase;
    @Override
    public void onCreate() {
        super.onCreate();
        appDatabase = AppDatabase.getInstance(this);

    }
    public static AppDatabase getAppDatabase() {
        return appDatabase;
    }

    public static UserDao getUserDao() {
        return appDatabase.userDao();
    }
}
