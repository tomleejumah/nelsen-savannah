package com.app.nisisiafrica;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.app.nisisiafrica.Utils.Util;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AppLifecycleObserver implements DefaultLifecycleObserver {
    private final Context appContext;

    public AppLifecycleObserver(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        try {
            if (PinManager.hasPin(appContext, user.getUid())
                    && !LockScreenActivity.AppLockState.isUnlocked()) {
                Intent intent = new Intent(appContext, LockScreenActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        LockScreenActivity.AppLockState.lock();
        Util.saveState("lastAppBackground", System.currentTimeMillis());
    }
}
