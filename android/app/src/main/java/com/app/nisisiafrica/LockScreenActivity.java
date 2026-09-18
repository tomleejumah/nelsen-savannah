package com.app.nisisiafrica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.Utils.Util;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LockScreenActivity extends AppCompatActivity {

    private String uid;
    private View[] dots;
    private View pinDotsRow;
    private TextView tvSubtitle;
    private final StringBuilder pin = new StringBuilder();
    private static final int PIN_LENGTH = 6;
    private boolean animating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock_screen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        uid = user.getUid();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Block back — user must unlock
            }
        });

        pinDotsRow = findViewById(R.id.llPinDots);
        tvSubtitle = findViewById(R.id.tvPinSubtitle);
        dots = new View[]{
                findViewById(R.id.dot1),
                findViewById(R.id.dot2),
                findViewById(R.id.dot3),
                findViewById(R.id.dot4),
                findViewById(R.id.dot5),
                findViewById(R.id.dot6)
        };

        int[] keys = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
                R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
                R.id.btn8, R.id.btn9
        };

        for (int id : keys) {
            findViewById(id).setOnClickListener(v ->
                    addDigit(((TextView) v).getText().toString()));
        }

        findViewById(R.id.btnDelete).setOnClickListener(v -> removeDigit());

        if (isBiometricAvailable()) {
            showBiometricPrompt();
        }
    }

    private boolean isBiometricAvailable() {
        BiometricManager bm = BiometricManager.from(this);
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometricPrompt() {
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock")
                .setSubtitle("Verify fingerprint to continue")
                .setNegativeButtonText("Use PIN")
                .build();

        BiometricPrompt prompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        if (tvSubtitle != null) {
                            tvSubtitle.setText("Fingerprint verified");
                        }
                        playFillFeedback(true, () -> unlockSuccess());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        if (tvSubtitle != null) {
                            tvSubtitle.setText("Fingerprint not recognized — try again or use PIN");
                        }
                        playFillFeedback(false, () -> {
                            pin.setLength(0);
                            updateDots();
                        });
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        if (tvSubtitle != null) {
                            tvSubtitle.setText("Enter your PIN to continue");
                        }
                    }
                });

        prompt.authenticate(info);
    }

    private void addDigit(String digit) {
        if (animating || pin.length() >= PIN_LENGTH) return;
        pin.append(digit);
        updateDots();
        if (pin.length() == PIN_LENGTH) verifyPin();
    }

    private void removeDigit() {
        if (animating || pin.length() == 0) return;
        pin.deleteCharAt(pin.length() - 1);
        updateDots();
    }

    private void updateDots() {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(
                    i < pin.length()
                            ? R.drawable.pin_dot_filled
                            : R.drawable.pin_dot_empty
            );
            dots[i].setScaleX(1f);
            dots[i].setScaleY(1f);
            dots[i].setAlpha(1f);
        }
    }

    private void verifyPin() {
        try {
            if (PinManager.verifyPin(this, uid, pin.toString())) {
                if (tvSubtitle != null) tvSubtitle.setText("PIN correct");
                playFillFeedback(true, this::unlockSuccess);
            } else {
                if (tvSubtitle != null) tvSubtitle.setText("Wrong PIN — try again");
                playFillFeedback(false, () -> {
                    pin.setLength(0);
                    updateDots();
                });
            }
        } catch (Exception e) {
            if (tvSubtitle != null) tvSubtitle.setText("Unable to verify PIN — try again");
            playFillFeedback(false, () -> {
                pin.setLength(0);
                updateDots();
            });
        }
    }

    /**
     * Cascading fill on the pin dots, then green (success) or red (error).
     * Used for both PIN entry and fingerprint result feedback.
     */
    private void playFillFeedback(boolean success, Runnable after) {
        if (animating) return;
        animating = true;

        AnimatorSet fill = new AnimatorSet();
        Animator[] pulses = new Animator[dots.length];
        for (int i = 0; i < dots.length; i++) {
            final View dot = dots[i];
            final int index = i;
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, View.SCALE_X, 1f, 1.35f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, View.SCALE_Y, 1f, 1.35f, 1f);
            AnimatorSet pulse = new AnimatorSet();
            pulse.playTogether(scaleX, scaleY);
            pulse.setDuration(140);
            pulse.setStartDelay(i * 55L);
            pulse.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    dot.setBackgroundResource(R.drawable.pin_dot_filled);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (index == dots.length - 1) {
                        int tint = success ? R.drawable.pin_dot_success : R.drawable.pin_dot_error;
                        for (View d : dots) d.setBackgroundResource(tint);
                        if (!success && pinDotsRow != null) {
                            ObjectAnimator shake = ObjectAnimator.ofFloat(
                                    pinDotsRow, View.TRANSLATION_X, 0, 18, -18, 12, -12, 0);
                            shake.setDuration(320);
                            shake.start();
                        }
                    }
                }
            });
            pulses[i] = pulse;
        }
        fill.playTogether(pulses);
        fill.setInterpolator(new AccelerateDecelerateInterpolator());
        fill.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (pinDotsRow == null || isFinishing()) {
                    animating = false;
                    if (after != null && !isFinishing()) after.run();
                    return;
                }
                pinDotsRow.postDelayed(() -> {
                    animating = false;
                    if (!isFinishing() && after != null) after.run();
                }, success ? 280 : 420);
            }
        });
        fill.start();
    }

    private void unlockSuccess() {
        AppLockState.setUnlocked(true);
        AppLockState.markUnlockGrace(2500L);
        // Don't count this transition as "backgrounded" for lock-after delay.
        Util.saveState("lastAppBackground", System.currentTimeMillis());
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    public static class AppLockState {
        private static boolean unlocked = false;
        private static long unlockGraceUntilMs = 0L;

        public static boolean isUnlocked() { return unlocked; }
        public static void setUnlocked(boolean val) { unlocked = val; }
        public static void lock() {
            unlocked = false;
            unlockGraceUntilMs = 0L;
        }

        public static void markUnlockGrace(long durationMs) {
            unlockGraceUntilMs = System.currentTimeMillis() + Math.max(0L, durationMs);
        }

        public static boolean isWithinUnlockGrace() {
            return System.currentTimeMillis() < unlockGraceUntilMs;
        }
    }
}
