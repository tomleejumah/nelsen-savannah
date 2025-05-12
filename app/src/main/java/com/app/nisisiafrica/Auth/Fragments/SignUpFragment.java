package com.app.nisisiafrica.Auth.Fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.app.nisisiafrica.Auth.FacebookAuthHelper;
import com.app.nisisiafrica.Auth.GoogleAuthHelper;
import com.app.nisisiafrica.BuildConfig;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.Model.UserData;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.SnackbarHandler;
import com.app.nisisiafrica.Utils;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SignUpFragment extends Fragment {
    private static final String TAG = "SignUpFragment";
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

        //init google Auth
        String web_client_id = BuildConfig.WEB_CLIENT_ID;
        googleAuthHelper = new GoogleAuthHelper(
                requireActivity(),
                launcher,
                web_client_id
        );

        // Initialize the Facebook Auth Helper
        facebookAuthHelper = new FacebookAuthHelper(requireActivity());
        facebookAuthHelper.addOnLoginSuccessListener(userData -> {
//            navigateToMainScreen(userData);
            Utils.navigateToMainScreen(requireContext(), MainActivity.class, userData);
            return null;
        });

        facebookAuthHelper.addOnLoginErrorListener(exception -> {
            // Handle login error
            snackbarHandler.showSnackbar("Login failed: " + exception.getMessage(), Snackbar.LENGTH_SHORT, 3);
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

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sign_up, container, false);

        Button googleBtn = view.findViewById(R.id.googleBtn);
        googleBtn.setOnClickListener(v -> {
            Utils.setClickAnimation(v, () -> googleAuthHelper.signIn());
        });

        Button facebookLoginButton = view.findViewById(R.id.facebookBtn);
        facebookLoginButton.setOnClickListener(v -> {
            List<String> permissions = Arrays.asList("email", "public_profile");
            Utils.setClickAnimation(v, () -> facebookAuthHelper.signIn(permissions));
        });

        EditText firstName = view.findViewById(R.id.FirstNameEditText);
        EditText lastName = view.findViewById(R.id.LastNameEditText);
        EditText emailEDT = view.findViewById(R.id.emailEditText);
        emailCheckIcon = view.findViewById(R.id.emailCheckIcon);
        EditText passEDT = view.findViewById(R.id.passwordEditText);
        ImageView passwordToggleIcon = view.findViewById(R.id.passwordToggleIcon);
        TextView namesError = view.findViewById(R.id.namesError);

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

        final boolean[] isPasswordVisible = {false};
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

        view.findViewById(R.id.btnSignUp).setOnClickListener(v -> {
            Utils.setClickAnimation(v, () -> {
                String email = emailEDT.getText().toString();
                String password = passEDT.getText().toString();
                String firstNameText = firstName.getText().toString();
                String lastNameText = lastName.getText().toString();

                if (email.isEmpty() || password.isEmpty() || firstNameText.isEmpty() || lastNameText.isEmpty()) {
                    snackbarHandler.showSnackbar("Please fill in the blanks", Snackbar.LENGTH_SHORT, 2);
                    view.findViewById(R.id.mailError).setVisibility(email.isEmpty() ? View.VISIBLE : View.GONE);
                    view.findViewById(R.id.passError).setVisibility(password.isEmpty() ? View.VISIBLE : View.GONE);
                    if (firstNameText.isEmpty()) {
                        namesError.setText("fill in  first name");
                        Utils.shakeView(view.findViewById(R.id.emailEditText));
                    } else if (lastNameText.isEmpty()) {
                        namesError.setText("fill in  last name");
                        Utils.shakeView(view.findViewById(R.id.passwordEditText));
                    }
                    view.findViewById(R.id.namesError).setVisibility(firstNameText.isEmpty() || lastNameText.isEmpty() ? View.VISIBLE : View.GONE);

                } else if (Utils.isValidPassword(password)) {
                    Utils.shakeView(view.findViewById(R.id.passwordLayout));
                    snackbarHandler.showSnackbar("Password must be at least 6 characters", Snackbar.LENGTH_SHORT, 3);
                } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    snackbarHandler.showSnackbar("Please enter a valid email", Snackbar.LENGTH_SHORT, 2);
                    emailCheckIcon.setImageResource(R.drawable.ic_error);
                    Utils.shakeView(view.findViewById(R.id.emailLayout));
                    emailCheckIcon.setVisibility(View.VISIBLE);
                } else {
                    signUp(firstNameText, lastNameText, email, password);
                }
            });

        });

        emailCheckIcon.setOnClickListener(v -> {
            Drawable current = emailCheckIcon.getDrawable();
            Drawable iconB = ContextCompat.getDrawable(requireContext(), R.drawable.ic_error);

            if (Utils.isSameDrawable(current, iconB)) {
                emailEDT.setText("");
            }
        });

        return view;
    }

    private void signUp(String firstNameText, String lastNameText, String email, String password) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();

        //todo add loading screen
        mAuth.createUserWithEmailAndPassword(email, password).addOnSuccessListener(authResult -> {
            String id = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
            HashMap<String, Object> map = new HashMap<>();
            map.put("lastName", lastNameText);
            map.put("email", email);
            map.put("firstName", firstNameText);
            map.put("displayName", "");
            map.put("ID", id);
            map.put("imageUrl", "default");
            map.put("Bio", "");

            dbRef.child("users").child(mAuth.getCurrentUser().getUid()).setValue(map).
                    addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            dbRef.child("roles").child(id).push().setValue("Mentee");
                            UserData userData = new UserData(
                                    id,
                                    email,
                                    "Mentee",
                                    "",
                                    firstNameText,
                                    lastNameText,
                                    "default",
                                    ""
                            );
                            Utils.navigateToMainScreen(requireContext(), MainActivity.class, userData);
                        } else {
                            String failureMessage = Utils.getErrorString(task);
                            snackbarHandler.showSnackbar(failureMessage, Snackbar.LENGTH_SHORT, 3);
                        }
                    });
        });
    }

    //todo use this
    private void signOut() {
        googleAuthHelper.signOut(() -> {
            // Handle sign out completion
            return null;
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
//                        navigateToMainScreen(userData);
                        Utils.navigateToMainScreen(requireContext(), MainActivity.class, userData);
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
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.putExtra("USER_DATA", userData);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // Sign out
    public void signOut(Runnable onComplete) {
        if (googleAuthHelper != null) {
            googleAuthHelper.signOut(() -> {
                onComplete.run();
                return null;
            });
        } else {
            FirebaseAuth.getInstance().signOut();
            onComplete.run();
        }
    }
}