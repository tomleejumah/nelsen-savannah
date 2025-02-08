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
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public static void shakeView(final View view) {
        final ObjectAnimator shakeAnimator = ObjectAnimator.ofFloat(view, "translationX", 0f, 10f, -10f, 10f, -10f, 5f, -5f, 0f);
        shakeAnimator.setDuration(500);
        shakeAnimator.start();
    }

    public static boolean isValidEmail(String email) {
        // email validation logic
        return Pattern.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)" +
                "*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$").matcher(email).matches();
    }

    public static boolean isValidPassword(String password) {
        // Password should have at least 6 characters
        return password.length() >= 6;
    }

    public static boolean getState(Context context, String key,boolean defValue) {
        SharedPreferences preferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return preferences.getBoolean(key, defValue);
    }

    public static void saveState(Context context, String key, boolean value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    public static void savePoints(Context context, String key, int value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    public static int getPoints(Context context, String key) {
        SharedPreferences preferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return preferences.getInt(key, 0);
    }

    public static void saveTime(Context context, String key, Long value) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    public static long getTime(Context context, String key) {
        SharedPreferences preferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return preferences.getLong(key, 0);
    }


    private static final String SPINS_COUNT_KEY = "spins_count";

    public static int getSpinsCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        return prefs.getInt(SPINS_COUNT_KEY, 0);
    }

    public static void incrementSpinsCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        int currentCount = prefs.getInt(SPINS_COUNT_KEY, 1);
        prefs.edit().putInt(SPINS_COUNT_KEY, currentCount + 1).apply();
    }

    public static void resetSpinsCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt(SPINS_COUNT_KEY, 1).apply();
    }

    public static void resetTime(Context context,String key) {
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        prefs.edit().putLong(key, 0).apply();
    }

}
