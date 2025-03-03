package com.app.nisisiafrica;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import com.app.nisisiafrica.Auth.LoginSignUpActivity;
import com.google.firebase.auth.FirebaseAuth;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Utils.init(this);
        boolean isFirstTime = Utils.getState("is-FirstTime", true);
        Intent intent;
        if (isFirstTime) {
            intent = new Intent(this, IntroActivity.class);
        } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginSignUpActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
