package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class LockScreenActivity extends AppCompatActivity {

    private String uid;
    private View[] dots;
    private final StringBuilder pin = new StringBuilder();
    private static final int PIN_LENGTH = 6;

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

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

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
                .setNegativeButtonText("Use PIN")
                .build();

        BiometricPrompt prompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        unlockSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        // user dismissed, falls back to PIN UI already visible
                    }
                });

        prompt.authenticate(info);
    }

    private void addDigit(String digit) {
        if (pin.length() >= PIN_LENGTH) return;
        pin.append(digit);
        updateDots();
        if (pin.length() == PIN_LENGTH) verifyPin();
    }

    private void removeDigit() {
        if (pin.length() > 0) {
            pin.deleteCharAt(pin.length() - 1);
            updateDots();
        }
    }

    private void updateDots() {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setBackgroundResource(
                    i < pin.length()
                            ? R.drawable.pin_dot_filled
                            : R.drawable.pin_dot_empty
            );
        }
    }

    private void verifyPin() {
        try {
            String stored = PinManager.getPin(this, uid);
            if (pin.toString().equals(stored)) {
                unlockSuccess();
            } else {
                Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
                pin.setLength(0);
                updateDots();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unlockSuccess() {
        AppLockState.setUnlocked(true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        // block back
    }

    public static class AppLockState {
        private static boolean unlocked = false;

        public static boolean isUnlocked() { return unlocked; }
        public static void setUnlocked(boolean val) { unlocked = val; }
        public static void lock() { unlocked = false; }
    }
}