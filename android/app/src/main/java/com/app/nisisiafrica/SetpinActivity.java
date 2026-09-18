package com.app.nisisiafrica;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SetpinActivity extends AppCompatActivity {

    private View[] dots;
    private final StringBuilder pin = new StringBuilder();
    private static final int PIN_LENGTH = 6;

    private String firstPin = null;
    private boolean confirming = false;
    private String uid;
    private TextView tvTitle;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setpin);
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

        tvTitle = findViewById(R.id.tvSetPinTitle);
        tvSubtitle = findViewById(R.id.tvSetPinSubtitle);

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
        findViewById(R.id.ivBack).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void addDigit(String digit) {
        if (pin.length() >= PIN_LENGTH) return;
        pin.append(digit);
        updateDots();
        if (pin.length() == PIN_LENGTH) handleSetMode();
    }

    private void handleSetMode() {
        String entered = pin.toString();
        if (!confirming) {
            firstPin = entered;
            confirming = true;
            reset();
            if (tvTitle != null) tvTitle.setText("Confirm PIN");
            if (tvSubtitle != null) tvSubtitle.setText("Re-enter your PIN to confirm");
        } else {
            if (entered.equals(firstPin)) {
                try {
                    PinManager.savePin(this, uid, entered);
                    LockScreenActivity.AppLockState.setUnlocked(true);
                    LockScreenActivity.AppLockState.markUnlockGrace(2500L);
                    if (tvSubtitle != null) tvSubtitle.setText("PIN saved");
                    setResult(RESULT_OK);
                    finish();
                    // noHistory=true in manifest also drops this activity from the back stack.
                } catch (Exception e) {
                    e.printStackTrace();
                    if (tvSubtitle != null) tvSubtitle.setText("Failed to save PIN — try again");
                    confirming = false;
                    firstPin = null;
                    reset();
                    if (tvTitle != null) tvTitle.setText("Set PIN code");
                }
            } else {
                if (tvSubtitle != null) tvSubtitle.setText("PINs do not match — start again");
                confirming = false;
                firstPin = null;
                reset();
                if (tvTitle != null) tvTitle.setText("Set PIN code");
            }
        }
    }

    private void removeDigit() {
        if (pin.length() > 0) {
            pin.deleteCharAt(pin.length() - 1);
            updateDots();
        }
    }

    private void reset() {
        pin.setLength(0);
        updateDots();
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

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}
