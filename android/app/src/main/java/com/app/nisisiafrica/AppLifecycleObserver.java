package com.app.nisisiafrica;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.app.nisisiafrica.Utils.Util;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Relocks after the user-selected background delay (not immediately on every
 * activity transition). A short grace window after unlock avoids the Lock→Main
 * handoff re-locking instantly.
 */
public class AppLifecycleObserver implements DefaultLifecycleObserver {
    public static final String PREF_LOCK_AFTER_MS = "lock_after_ms";
    /** Default: Immediate once the unlock grace ends. */
    public static final long DEFAULT_LOCK_AFTER_MS = 0L;

    private final Context appContext;

    public AppLifecycleObserver(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        try {
            if (!PinManager.hasPin(appContext, user.getUid())) return;
            if (LockScreenActivity.AppLockState.isWithinUnlockGrace()) return;

            long delayMs = Util.getState(PREF_LOCK_AFTER_MS, DEFAULT_LOCK_AFTER_MS);
            long lastBg = Util.getState("lastAppBackground", 0L);
            long away = lastBg > 0 ? System.currentTimeMillis() - lastBg : Long.MAX_VALUE;

            if (LockScreenActivity.AppLockState.isUnlocked()) {
                if (away < delayMs) return;
                LockScreenActivity.AppLockState.lock();
            }

            if (!LockScreenActivity.AppLockState.isUnlocked()) {
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
        // Record background time only — do not flip unlocked=false here.
        // That was locking immediately on Lock→Main / login transitions.
        Util.saveState("lastAppBackground", System.currentTimeMillis());
    }
}
