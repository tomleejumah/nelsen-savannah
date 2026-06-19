package com.app.nisisiafrica;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class PinManager {
    private static final String TAG = "PinManager";
    private static final String PREFS_FILE = "secure_lock_prefs";
    private static final String HASH_PREFIX = "pin_hash_";
    private static final String SALT_PREFIX = "pin_salt_";
    private static final String LEGACY_PREFIX = "pin_";

    private static SharedPreferences getPrefs(Context ctx) throws Exception {
        try {
            return createEncryptedPrefs(ctx);
        } catch (GeneralSecurityException | IOException e) {
            Log.w(TAG, "Encrypted prefs unreadable; recreating", e);
            ctx.deleteSharedPreferences(PREFS_FILE);
            return createEncryptedPrefs(ctx);
        }
    }

    private static SharedPreferences createEncryptedPrefs(Context ctx) throws Exception {
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
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = hashPin(pin, salt);
        SharedPreferences prefs = getPrefs(ctx);
        prefs.edit()
                .putString(HASH_PREFIX + uid, hash)
                .putString(SALT_PREFIX + uid, Base64.encodeToString(salt, Base64.NO_WRAP))
                .remove(LEGACY_PREFIX + uid)
                .apply();
    }

    public static boolean verifyPin(Context ctx, String uid, String pin) throws Exception {
        SharedPreferences prefs = getPrefs(ctx);
        String storedHash = prefs.getString(HASH_PREFIX + uid, null);
        String storedSalt = prefs.getString(SALT_PREFIX + uid, null);
        if (storedHash != null && storedSalt != null) {
            byte[] salt = Base64.decode(storedSalt, Base64.NO_WRAP);
            return storedHash.equals(hashPin(pin, salt));
        }
        String legacyPin = prefs.getString(LEGACY_PREFIX + uid, null);
        if (legacyPin != null && legacyPin.equals(pin)) {
            savePin(ctx, uid, pin);
            return true;
        }
        return false;
    }

    public static boolean hasPin(Context ctx, String uid) throws Exception {
        SharedPreferences prefs = getPrefs(ctx);
        return prefs.contains(HASH_PREFIX + uid) || prefs.contains(LEGACY_PREFIX + uid);
    }

    public static void clearPin(Context ctx, String uid) throws Exception {
        getPrefs(ctx).edit()
                .remove(HASH_PREFIX + uid)
                .remove(SALT_PREFIX + uid)
                .remove(LEGACY_PREFIX + uid)
                .apply();
    }

    private static String hashPin(String pin, byte[] salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        digest.update(pin.getBytes());
        byte[] hash = digest.digest();
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }
}
