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
        boolean isFirstTime = Utils.getState(getApplicationContext(), "is-FirstTime", true);
        if (isFirstTime) {
            startActivity(new Intent(this, IntroActivity.class));
        } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
        } else  {
            startActivity(new Intent(this, LoginSignUpActivity.class));
        }
    }
}
