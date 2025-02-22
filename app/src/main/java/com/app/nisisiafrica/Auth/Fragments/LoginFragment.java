package com.app.nisisiafrica.Auth.Fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.app.nisisiafrica.Auth.GoogleAuthHelper;
import com.app.nisisiafrica.BuildConfig;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.R;

import java.util.Objects;


public class LoginFragment extends Fragment {
    private GoogleAuthHelper googleAuthHelper;
    private ActivityResultLauncher<Intent> launcher;
    private static final String TAG = "LoginFragment";
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "onActivityResult triggered");
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "Sign-in successful, checking data...");
                        if (result.getData() != null) {
                            Log.d(TAG, "Intent data: " + result.getData().toString());
                        } else {
                            Log.d(TAG, "onActivityResult: Data is null");
                        }
                        handleGoogleSignIn(result.getData());
                    } else {
                        Log.d(TAG, "Sign-in failed or cancelled. Result Code: " + result.getResultCode());
                    }
                }
        );

           String web_client_id = BuildConfig.WEB_CLIENT_ID;
        googleAuthHelper = new GoogleAuthHelper(
                requireActivity(),
                launcher,
                web_client_id
        );
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view =  inflater.inflate(R.layout.fragment_login, container, false);

        EditText emailEDT = view.findViewById(R.id.emailEditText);
        ImageView emailCheckIcon = view.findViewById(R.id.emailCheckIcon);
        EditText passEDT = view.findViewById(R.id.passwordEditText);
        ImageView passwordToggleIcon = view.findViewById(R.id.passwordToggleIcon);

        emailEDT.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString();
                if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailCheckIcon.setVisibility(View.VISIBLE);
                } else {
                    emailCheckIcon.setVisibility(View.GONE);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        final boolean[] isPasswordVisible = {false}; // Using an array to allow modification inside OnClickListener

        passwordToggleIcon.setOnClickListener(v -> {
            if (isPasswordVisible[0]) {
                passEDT.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordToggleIcon.setImageResource(R.drawable.ic_hidden_pwsd);
            } else {
                passEDT.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordToggleIcon.setImageResource(R.drawable.ic_shown_pwsd);
            }
            isPasswordVisible[0] = !isPasswordVisible[0];
            passEDT.setSelection(passEDT.getText().length()); // Keep cursor at the end
        });

        view.findViewById(R.id.googleBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        view.findViewById(R.id.txtForgotPwsd).setOnClickListener(v -> {
//                Intent intent = new Intent(requireActivity(), ForgotPasswordActivity.class);
//                startActivity(intent);
        });

        view.findViewById(R.id.btnLogin).setOnClickListener(v -> {
//            login();
        });
            return view;
    }

    private void handleGoogleSignIn(Intent data) {
        googleAuthHelper.handleSignInResult(
                data,
                userData -> {
                    // Success - you have all user data here
                    String email = userData.getEmail();
                    String name = userData.getDisplayName();
                    String firstName = userData.getFirstName();
                    String lastName = userData.getLastName();
                    String photoUrl = userData.getPhotoUrl();
                    String userId = userData.getId();

                    // Save to Firebase Realtime Database
                    saveToFireBase(userData);

                    return null;
                },
                exception -> {
                    // Handle error
                    Log.e("Auth", "Sign in failed", exception);
                    Toast.makeText(getContext(), "Sign in failed", Toast.LENGTH_SHORT).show();
                    return null;
                }
        );
    }

    private void saveToFireBase(GoogleAuthHelper.UserData userData) {
        googleAuthHelper.saveUserToFirebase(
                userData,
                isSuccess -> {
                    if (isSuccess) {
                        Intent intent = new Intent(getContext(), MainActivity.class);
                        startActivity(intent);
                    }
                    return null;
                },
                exception -> {
                    Log.e("Firebase", "Error saving user", exception);
                    return null;
                }
        );
    }
}