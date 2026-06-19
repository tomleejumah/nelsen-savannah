package com.app.nisisiafrica.Auth;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.R;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText emailInput;
    private Button btnReset;
    private TextView txtBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgot_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailInput = findViewById(R.id.emailEditText);
        btnReset = findViewById(R.id.btnResetPassword);
        txtBack = findViewById(R.id.txtBackToLogin);

        btnReset.setOnClickListener(v -> sendResetEmail());
        txtBack.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) {
            Toast.makeText(this, "Enter your email address", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        btnReset.setEnabled(false);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnReset.setEnabled(true);
                    Toast.makeText(this,
                            e.getMessage() != null ? e.getMessage() : "Failed to send reset email",
                            Toast.LENGTH_SHORT).show();
                });
    }
}
