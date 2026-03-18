package com.app.nisisiafrica;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class SetpinActivity extends AppCompatActivity {

    private View[] dots;
    private final StringBuilder pin = new StringBuilder();
    private static final int PIN_LENGTH = 6;

    private String firstPin = null;
    private boolean confirming = false;
    private String uid;

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
            toast("Re-enter PIN");
        } else {
            if (entered.equals(firstPin)) {
                try {
                    PinManager.savePin(this, uid, entered);
                    toast("PIN set");
                    setResult(RESULT_OK);
                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                    toast("Failed to save PIN");
                }
            } else {
                toast("PINs do not match");
                confirming = false;
                firstPin = null;
                reset();
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}
