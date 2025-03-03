package com.app.nisisiafrica;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.util.regex.Pattern;

public class Utils {
    private static Context appContext;
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public static boolean isValidEmail(String email) {
        return Pattern.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)" +
                "*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$").matcher(email).matches();
    }

    public static boolean isValidPassword(String password) {
        // Password should have at least 6 characters
        return password.length() >= 6;
    }

    public static <T> T getState( String key, T defValue) {
        if (appContext == null) throw new IllegalStateException("Utils not initialized");
        SharedPreferences preferences = appContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//        SharedPreferences preferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        Object result;
        if (defValue instanceof Boolean) {
            result = preferences.getBoolean(key, (Boolean) defValue);
        } else if (defValue instanceof Integer) {
            result = preferences.getInt(key, (Integer) defValue);
        } else if (defValue instanceof Float) {
            result = preferences.getFloat(key, (Float) defValue);
        } else if (defValue instanceof Long) {
            result = preferences.getLong(key, (Long) defValue);
        } else if (defValue instanceof String) {
            result = preferences.getString(key, (String) defValue);
        } else {
            throw new IllegalArgumentException("Type not supported");
        }
        return (T) result;
    }

    public static <T> void saveState( String key, T value) {
//        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (appContext == null) throw new IllegalStateException("Utils not initialized");
        SharedPreferences sharedPreferences = appContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else {
            throw new IllegalArgumentException("Type not supported");
        }
        editor.apply();
    }

    public static void setClickAnimation(View v) {
        v.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(25)
                .withEndAction(() -> {
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(25)
                            .start();
                })
                .start();
    }

    public static void shakeView(final View view) {
        final ObjectAnimator shakeAnimator = ObjectAnimator.ofFloat(view,
                "translationX", 0f, 10f, -10f, 10f, -10f, 5f, -5f, 0f);
        shakeAnimator.setDuration(500);
        shakeAnimator.start();
    }

}
