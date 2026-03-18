package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private boolean biometricAvailable;
    private EditText pinInput;

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
        biometricAvailable = isBiometricAvailable();

        if (biometricAvailable) {
            showBiometricPrompt();
        } else {
            showPinUI();
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
                        if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            showPinUI(); // fallback to PIN
                        }
                    }
                });

        prompt.authenticate(info);
    }

    private void showPinUI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(64, 64, 64, 64);

        TextView title = new TextView(this);
        title.setText("Enter PIN to unlock");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);

        pinInput = new EditText(this);
        pinInput.setHint("PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setGravity(Gravity.CENTER);

        Button unlockBtn = new Button(this);
        unlockBtn.setText("Unlock");
        unlockBtn.setOnClickListener(v -> verifyPin());

        layout.addView(title);
        layout.addView(pinInput);
        layout.addView(unlockBtn);

        setContentView(layout);
    }

    private void verifyPin() {
        try {
            String stored = PinManager.getPin(this, uid);
            if (pinInput.getText().toString().equals(stored)) {
                unlockSuccess();
            } else {
                Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show();
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
        // Block back — force auth
    }

    // AppLockState.java
    public static class AppLockState {
        private static boolean unlocked = false;

        public static boolean isUnlocked() {
            return unlocked;
        }

        public static void setUnlocked(boolean val) {
            unlocked = val;
        }

        public static void lock() {
            unlocked = false;
        }
    }
}