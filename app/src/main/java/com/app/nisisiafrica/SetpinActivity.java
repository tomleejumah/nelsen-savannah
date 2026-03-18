package com.app.nisisiafrica;

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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class SetpinActivity extends AppCompatActivity {
    private EditText pinInput, confirmInput;
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

        // Build UI programmatically
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(64, 64, 64, 64);

        TextView title = new TextView(this);
        title.setText("Set App PIN");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);

        pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setGravity(Gravity.CENTER);

        confirmInput = new EditText(this);
        confirmInput.setHint("Confirm PIN");
        confirmInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirmInput.setGravity(Gravity.CENTER);

        Button saveBtn = new Button(this);
        saveBtn.setText("Save PIN");
        saveBtn.setOnClickListener(v -> savePin());

        layout.addView(title);
        layout.addView(pinInput);
        layout.addView(confirmInput);
        layout.addView(saveBtn);

        setContentView(layout);
    }

    private void savePin() {
        String pin = pinInput.getText().toString().trim();
        String confirm = confirmInput.getText().toString().trim();

        if (pin.isEmpty() || pin.length() < 4) {
            Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pin.equals(confirm)) {
            Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            PinManager.savePin(this, uid, pin);
            Toast.makeText(this, "PIN set successfully", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK); // signal success back to settings
            finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        // If user backs out without setting PIN, signal cancel
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}