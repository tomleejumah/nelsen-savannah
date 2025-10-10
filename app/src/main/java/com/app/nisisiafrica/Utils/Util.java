package com.app.nisisiafrica.Utils;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

import java.util.regex.Pattern;

public class Util {
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
        return password.length() < 6;
    }

    public static <T> T getState( String key, T defValue) {
//        if (appContext == null) throw new IllegalStateException("Utils not initialized");
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

    public static void setClickAnimation(View v,Runnable endAction) {
        v.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(20)
                .withEndAction(() -> {
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(25)
                            .withEndAction(endAction)
                            .start();
                }).start();
    }

    public static void shakeView(final View view) {
        final ObjectAnimator shakeAnimator = ObjectAnimator.ofFloat(view,
                "translationX", 0f, 10f, -10f, 10f, -10f, 5f, -5f, 0f);
        shakeAnimator.setDuration(500);
        shakeAnimator.start();
    }
    @NonNull
    public static String getErrorString(Task<?> task) {
        String failureMessage = "Authentication failed. Please try again.";
        Exception exception = task.getException();

        if (exception != null) {
            if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                failureMessage = "Invalid credentials. Please check your email or  password.";
            } else if (exception instanceof FirebaseAuthInvalidUserException) {
                failureMessage = "No account found with this email. Please sign up.";
            }
        }
        return failureMessage;
    }

    public static void navigateToMainScreen(Context context, Class<?> destinationActivity, boolean isFromAuth) {
        Intent intent = new Intent(context, destinationActivity);
//        if (userData != null) { intent.putExtra("USER_DATA", userData); }
        intent.putExtra("IS_FROM_AUTH", isFromAuth);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }


    // check to see if two drawable resources are the same
    public static boolean isSameDrawable(Drawable current, Drawable iconB) {
        if (current == null || iconB == null) {
            return false;
        }

        // Option 1: Simple bitmap comparison
        Bitmap bitmap1 = drawableToBitmap(current);
        Bitmap bitmap2 = drawableToBitmap(iconB);
        boolean result = bitmap1.sameAs(bitmap2);

        // Clean up bitmaps
        bitmap1.recycle();
        bitmap2.recycle();

        return result;
    }

    // Helper method to convert drawables to bitmaps
    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        // Ensure valid dimensions (at least 1x1)
        width = width > 0 ? width : 1;
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
