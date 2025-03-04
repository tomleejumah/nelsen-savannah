package com.app.nisisiafrica.Auth.Fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.nisisiafrica.Auth.FacebookAuthHelper;
import com.app.nisisiafrica.Auth.ForgotPasswordActivity;
import com.app.nisisiafrica.Auth.GoogleAuthHelper;
import com.app.nisisiafrica.BuildConfig;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.Model.UserData;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.SnackbarHandler;
import com.app.nisisiafrica.Utils;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Arrays;
import java.util.List;


public class LoginFragment extends Fragment {
    private static final String TAG = "LoginFragment";
    private GoogleAuthHelper googleAuthHelper;
    private FacebookAuthHelper facebookAuthHelper;
    private ImageView emailCheckIcon;
    private SnackbarHandler snackbarHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityResultLauncher<Intent> launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "onActivityResult triggered");
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "Sign-in successful, checking data...");
                        if (result.getData() != null) {
                            Log.d(TAG, "Intent data: " + result.getData());
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

        // Initialize the Facebook Auth Helper
        facebookAuthHelper = new FacebookAuthHelper(requireActivity());
        facebookAuthHelper.addOnLoginSuccessListener(userData -> {
            // Handle successful login
            Toast.makeText(requireContext(), "Logged in as " + userData.getDisplayName(), Toast.LENGTH_SHORT).show();
            navigateToMainScreen(userData);
            return null;
        });

        facebookAuthHelper.addOnLoginErrorListener(exception -> {
            // Handle login error
            Toast.makeText(requireContext(), "Login failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        });
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof SnackbarHandler) {
            snackbarHandler = (SnackbarHandler) context;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Pass activity result to the Facebook helper
        facebookAuthHelper.handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        EditText emailEDT = view.findViewById(R.id.emailEditText);
        emailCheckIcon = view.findViewById(R.id.emailCheckIcon);
        EditText passEDT = view.findViewById(R.id.passwordEditText);
        ImageView passwordToggleIcon = view.findViewById(R.id.passwordToggleIcon);

        emailEDT.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString();
                emailCheckIcon.setImageResource(Patterns.EMAIL_ADDRESS.matcher(email).matches()
                        ? R.drawable.ic_check_green : R.drawable.ic_error);
                emailCheckIcon.setVisibility(Patterns.EMAIL_ADDRESS.matcher(email).matches() ?
                        View.VISIBLE : View.GONE);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (view.findViewById(R.id.mailError).getVisibility() == View.VISIBLE) {
                    view.findViewById(R.id.mailError).setVisibility(View.GONE);
                }
            }
        });

        passEDT.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (view.findViewById(R.id.passError).getVisibility() == View.VISIBLE) {
                    view.findViewById(R.id.passError).setVisibility(View.GONE);
                }
            }
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

        view.findViewById(R.id.googleBtn).setOnClickListener(v -> googleAuthHelper.signIn());
        view.findViewById(R.id.facebookBtn).setOnClickListener(v -> {
            Utils.setClickAnimation(v);
            List<String> permissions = Arrays.asList("email", "public_profile");
            facebookAuthHelper.signIn(permissions);
        });

        view.findViewById(R.id.txtForgotPwsd).setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), ForgotPasswordActivity.class);
            startActivity(intent);
        });


        view.findViewById(R.id.btnLogin).setOnClickListener(v -> {
            Utils.shakeView(v);
            String email = emailEDT.getText().toString();
            String password = passEDT.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                snackbarHandler.showSnackbar("Please fill in the blanks", Snackbar.LENGTH_SHORT, 2);
                view.findViewById(R.id.mailError).setVisibility(email.isEmpty() ? View.VISIBLE : View.GONE);
                view.findViewById(R.id.passError).setVisibility(password.isEmpty() ? View.VISIBLE : View.GONE);

            } else if (password.length() < 6) {
                snackbarHandler.showSnackbar("Password must be at least \n 6 characters", Snackbar.LENGTH_SHORT, 3);

            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                snackbarHandler.showSnackbar("Please enter a valid email", Snackbar.LENGTH_SHORT, 2);
                emailCheckIcon.setImageResource(R.drawable.ic_error);
                emailCheckIcon.setVisibility(View.VISIBLE);
            } else {
                login(email, password);
            }
        });

        return view;
    }

    private void login(String email, String password) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {

              //todo add loading screen
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    String userId = user.getUid();
                    // Fetch user data from Realtime Database
                    usersRef.child(userId).get().addOnCompleteListener(dataTask -> {
                        if (dataTask.isSuccessful() && dataTask.getResult().exists()) {
                            UserData userData = dataTask.getResult().getValue(UserData.class);

                            if (userData != null) {
                                navigateToMainScreen(userData);
                            }
                        } else {
                            snackbarHandler.showSnackbar("Failed to fetch your data,\n please retry", Snackbar.LENGTH_LONG, 3);
                        }
                    });
                }

            } else {
                String failureMessage = "Authentication failed. Please try again.";
                Exception exception = task.getException();

                if (exception != null) {
                    if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                        failureMessage = "Invalid credentials. Please check your email or  password.";
                    } else if (exception instanceof FirebaseAuthInvalidUserException) {
                        failureMessage = "No account found with this email. Please sign up.";
                    }
                }

                snackbarHandler.showSnackbar(failureMessage, Snackbar.LENGTH_SHORT, 3);
            }
        });
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
                    goToNextActivity(userData);

                    return null;
                },
                exception -> {
                    // Handle error
                    Log.e("Auth", "Sign in failed", exception);
                    snackbarHandler.showSnackbar("Sign in failed", Snackbar.LENGTH_SHORT, 3);
                    return null;
                }
        );
    }

    private void goToNextActivity(UserData userData) {
        googleAuthHelper.saveUserToFirebase(
                userData,
                isSuccess -> {
                    if (isSuccess) {
                        navigateToMainScreen(userData);
                    }
                    return null;
                },
                exception -> {
                    Log.e("Firebase", "Error saving user", exception);
                    return null;
                }
        );
    }
    private void navigateToMainScreen(UserData userData) {
        // Navigate to your main screen after successful login
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.putExtra("USER_DATA", userData);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

}