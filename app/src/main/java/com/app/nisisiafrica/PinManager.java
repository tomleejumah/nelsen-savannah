package com.app.nisisiafrica;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class PinManager {
    private static final String PREFS_FILE = "secure_lock_prefs";

    private static SharedPreferences getPrefs(Context ctx) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                ctx, PREFS_FILE, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public static void savePin(Context ctx, String uid, String pin) throws Exception {
        getPrefs(ctx).edit().putString("pin_" + uid, pin).apply();
    }

    public static String getPin(Context ctx, String uid) throws Exception {
        return getPrefs(ctx).getString("pin_" + uid, null);
    }

    public static boolean hasPin(Context ctx, String uid) throws Exception {
        return getPin(ctx, uid) != null;
    }

    public static void clearPin(Context ctx, String uid) throws Exception {
        getPrefs(ctx).edit().remove("pin_" + uid).apply();
    }
}
