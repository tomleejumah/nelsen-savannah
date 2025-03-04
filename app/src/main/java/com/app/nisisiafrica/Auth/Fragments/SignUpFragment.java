package com.app.nisisiafrica.Auth.Fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
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
            navigateToMainScreen(userData);
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sign_up, container, false);

        Button signInButton = view.findViewById(R.id.googleBtn);
        signInButton.setOnClickListener(v ->{
            Utils.setClickAnimation(v);
            googleAuthHelper.signIn();});
        Button facebookLoginButton = view.findViewById(R.id.facebookBtn);

        // Set click listener
        facebookLoginButton.setOnClickListener(v -> {
            Utils.setClickAnimation(v);
            List<String> permissions = Arrays.asList("email", "public_profile");
            facebookAuthHelper.signIn(permissions);
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
        view.findViewById(R.id.btnSignUp).setOnClickListener(v -> {
            Utils.setClickAnimation(v);
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
                } else if (lastNameText.isEmpty()) {
                    namesError.setText("fill in  last name");
                }
                view.findViewById(R.id.namesError).setVisibility(firstNameText.isEmpty() || lastNameText.isEmpty() ? View.VISIBLE : View.GONE);

            } else if (password.length() < 6) {

                snackbarHandler.showSnackbar("Password must be at least \n 6 characters", Snackbar.LENGTH_SHORT, 3);
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                snackbarHandler.showSnackbar("Please enter a valid email", Snackbar.LENGTH_SHORT, 2);
                emailCheckIcon.setImageResource(R.drawable.ic_error);
                emailCheckIcon.setVisibility(View.VISIBLE);
            } else {
                signUp(firstNameText, lastNameText, email, password);
            }


        });
        return view;
    }

    private void signUp(String firstNameText, String lastNameText, String email, String password) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        String userRole = Utils.getState("userRole", "Mentee");
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
//todo
            FirebaseDatabase.getInstance().getReference().child("roles").
                    child(id).push().setValue(userRole);

            usersRef.child("USERS").child(mAuth.getCurrentUser().getUid()).setValue(map).
                    addOnCompleteListener(task -> {
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
                                } else if (exception instanceof FirebaseAuthUserCollisionException) {
                                    failureMessage = "This email is already in use. Try logging in instead.";
                                }
                            }

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