package com.app.customsnackbarlib;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

public class CustomSnackbar {
    private static final AnimatorSet animatorSet = new AnimatorSet();
    @SuppressLint("StaticFieldLeak")
    private static View currentSnackbar = null;
    public static void show(Activity activity, String message, int duration, int type) {
        ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();
        View customSnackbarView = createSnackbarView(rootView, message, type);
        showSnackbar(rootView, customSnackbarView, duration);
    }

    public static void customLayout(ViewGroup view, int layoutResId, int duration) {
        View customView = LayoutInflater.from(view.getContext()).inflate(layoutResId, null);
        showSnackbar(view, customView, duration);
    }

    private static View createSnackbarView(ViewGroup rootView, String message, int type) {
        LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
        View customSnackbarView = inflater.inflate(R.layout.custom_snackbar, rootView, false);

        TextView textView = customSnackbarView.findViewById(R.id.snackbar_text);
        ImageView iconView = customSnackbarView.findViewById(R.id.snackbar_icon);
        textView.setText(message);

        int iconResId, backgroundResId;
        switch (type) {
            case 1: // Success
                iconResId = R.drawable.ic_success;
                backgroundResId = R.drawable.snackbar_success_background;
                break;
            case 2: // Warning
                iconResId = R.drawable.ic_warning;
                backgroundResId = R.drawable.snackbar_warning_background;
                break;
            case 3: // Error
                iconResId = R.drawable.ic_error;
                backgroundResId = R.drawable.snackbar_error_background;
                break;
            default:
                iconResId = R.drawable.ic_default;
                backgroundResId = R.drawable.snackbar_default_background;
        }

        iconView.setImageResource(iconResId);
        customSnackbarView.setBackgroundResource(backgroundResId);

        return customSnackbarView;
    }

    private static void showSnackbar(ViewGroup rootView, View customView, int duration) {
        if (currentSnackbar != null && currentSnackbar.getParent() != null) {
            hide(currentSnackbar);
        }
        currentSnackbar = customView;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP;
        params.setMargins(20, 50, 20, 0);

        if (customView.getParent() == null) {
            rootView.addView(customView, params);
        }

        customView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove listener to prevent this from being called multiple times
                customView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                animateIn(customView);
            }
        });
        addSwipeToDismiss(customView);

        int durationInMillis = getDurationInMillis(duration);
        if (durationInMillis != Snackbar.LENGTH_INDEFINITE) {
            customView.postDelayed(() -> hide(customView), durationInMillis);
        }
    }

    public static void customWidth(int widthPx) {
        if (currentSnackbar != null && currentSnackbar.getParent() != null) {
            // Get the current layout params
            ViewGroup.LayoutParams params = currentSnackbar.getLayoutParams();

            // Update the width
            params.height = widthPx;

            // Apply the updated params
            currentSnackbar.setLayoutParams(params);

            // Force layout update
            currentSnackbar.requestLayout();
        }
    }

    private static void animateIn(View view) {
        int distance = -view.getHeight();
        view.setVisibility(View.VISIBLE);
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0, 1),
                ObjectAnimator.ofFloat(view, "translationY", distance, 0)
        );
        animatorSet.setDuration(500);
        animatorSet.start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void addSwipeToDismiss(View customSnackbarView) {
        final float SWIPE_THRESHOLD = 100; // Minimum distance for a swipe to be detected
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final float[] dX = new float[1];
        final float[] dY = new float[1];

        customSnackbarView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    dX[0] = customSnackbarView.getX() - event.getRawX();
                    dY[0] = customSnackbarView.getY() - event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float newX = event.getRawX() + dX[0];
                    float newY = event.getRawY() + dY[0];
                    customSnackbarView.setX(newX);
                    // Limit upward movement only
                    if (newY <= 0) {
                        customSnackbarView.setY(newY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    float endX = event.getRawX();
                    float endY = event.getRawY();
                    float deltaX = endX - startX[0];
                    float deltaY = endY - startY[0];

                    if (Math.abs(deltaX) > SWIPE_THRESHOLD) {
                        // Horizontal swipe detected
                        hide(customSnackbarView);
                    } else if (-deltaY > 10) {
                        int distance = -customSnackbarView.getHeight();

                        // Playing both the translation and alpha animations together (swipe out and fade out)
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(customSnackbarView, "alpha", 1, 0), // Fade out
                                ObjectAnimator.ofFloat(customSnackbarView, "translationY", 0, distance) // Move upward
                        );

                        animatorSet.setDuration(300);

                        // Add listener to handle the view removal after the animation ends
                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                removeView(customSnackbarView);
                            }
                        });
                        animatorSet.start();
                    } else {
                        resetPosition(customSnackbarView);
                    }
                    return true;
            }
            return false;
        });
    }

    private static void resetPosition(View view) {
        view.animate()
                .x(20)
                .setDuration(200)
                .setInterpolator(new AccelerateInterpolator())
                .start();
    }

    public static void hide(View customSnackbarView) {
        float currentX = customSnackbarView.getX();
        float currentY = customSnackbarView.getY();
        float screenWidth = customSnackbarView.getResources().getDisplayMetrics().widthPixels;

        if (Math.abs(currentX) > Math.abs(currentY)) {
            // Horizontal swipe
            float targetX = (currentX > 0) ? screenWidth : -screenWidth;
            customSnackbarView.animate()
                    .x(targetX)
                    .setDuration(300)
                    .withEndAction(removeView(customSnackbarView))
                    .start();
        } else {
            // Vertical swipe (upward)
            customSnackbarView.animate()
                    .translationY(-customSnackbarView.getHeight())
                    .setDuration(300)
                    .withEndAction(removeView(customSnackbarView))
                    .start();
        }
        if (customSnackbarView == currentSnackbar) {
            currentSnackbar = null;
        }
    }

    private static Runnable removeView(final View view) {
        return () -> {
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        };
    }

    private static int getDurationInMillis(int duration) {
        switch (duration) {
            case Snackbar.LENGTH_LONG:
                return 3500;
            case Snackbar.LENGTH_INDEFINITE:
                return Snackbar.LENGTH_INDEFINITE;
            default:
                return 2000;
        }
    }
}