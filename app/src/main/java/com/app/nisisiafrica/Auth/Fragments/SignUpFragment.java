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
import androidx.lifecycle.ViewModelProvider;

import com.app.nisisiafrica.Auth.FacebookAuthHelper;
import com.app.nisisiafrica.Auth.GoogleSignInMode;
import com.app.nisisiafrica.Auth.GoogleAuthHelper;
import com.app.nisisiafrica.Auth.GoogleSignInMode;
import com.app.nisisiafrica.BuildConfig;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.VerifyEmailActivity;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.Interfaces.SnackbarHandler;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.databinding.FragmentSignUpBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import kotlin.Unit;
//todo switch completely to binding
public class SignUpFragment extends Fragment {
    private static final String TAG = "SignUpFragment";
    private GoogleAuthHelper googleAuthHelper;
    private FacebookAuthHelper facebookAuthHelper;
    private ImageView emailCheckIcon;
    private SnackbarHandler snackbarHandler;
    private FragmentSignUpBinding binding;
    private UserViewModel sharedUserViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedUserViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);

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
            sharedUserViewModel.saveUserData(userData);
            sharedUserViewModel.setUserData(userData);
            Util.saveState(Constants.CURRENT_USER_ID, userData.getId());
            String name = userData.getFirstName() != null && !userData.getFirstName().isEmpty()
                    ? userData.getFirstName() : "there";
            snackbarHandler.showSnackbar("Welcome, " + name + "!", Snackbar.LENGTH_LONG, 4);
            Util.navigateToMainScreen(requireContext(), MainActivity.class, true);
            return Unit.INSTANCE;
        });

        facebookAuthHelper.addOnLoginErrorListener(exception -> {
            // Handle login error
            snackbarHandler.showSnackbar("Login failed: " + exception.getMessage(), Snackbar.LENGTH_SHORT, 3);
            return Unit.INSTANCE;
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
//        View view = inflater.inflate(R.layout.fragment_sign_up, container, false);

        binding = FragmentSignUpBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        Button googleBtn = view.findViewById(R.id.googleBtn);
        googleBtn.setOnClickListener(v -> {
            Util.setClickAnimation(v, () -> googleAuthHelper.signIn());
        });


        binding.facebookBtn.setOnClickListener(v -> {
            List<String> permissions = Arrays.asList("email", "public_profile");
            Util.setClickAnimation(v, () -> facebookAuthHelper.signIn(GoogleSignInMode.REGISTER, permissions));
        });

        EditText firstName = view.findViewById(R.id.FirstNameEditText);
        EditText lastName = view.findViewById(R.id.LastNameEditText);
        EditText emailEDT = view.findViewById(R.id.emailEditText);
        emailCheckIcon = view.findViewById(R.id.emailCheckIcon);
        EditText passEDT = view.findViewById(R.id.passwordEditText);
        ImageView passwordToggleIcon = view.findViewById(R.id.passwordToggleIcon);
        TextView namesError = view.findViewById(R.id.namesError);

        binding.checkKeepMeIn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Util.saveState("keepMeIn", isChecked);
        });
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
            Util.setClickAnimation(v, () -> {
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
                        Util.shakeView(view.findViewById(R.id.emailEditText));
                    } else if (lastNameText.isEmpty()) {
                        namesError.setText("fill in  last name");
                        Util.shakeView(view.findViewById(R.id.passwordEditText));
                    }
                    view.findViewById(R.id.namesError).setVisibility(firstNameText.isEmpty() || lastNameText.isEmpty() ? View.VISIBLE : View.GONE);

                } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    snackbarHandler.showSnackbar("Please enter a valid email", Snackbar.LENGTH_SHORT, 2);
                    emailCheckIcon.setImageResource(R.drawable.ic_error);
                    Util.shakeView(view.findViewById(R.id.emailLayout));
                    emailCheckIcon.setVisibility(View.VISIBLE);
                } else if (Util.isPasswordTooShort(password)) {
                    Util.shakeView(view.findViewById(R.id.passwordLayout));
                    snackbarHandler.showSnackbar("Password must be at least 6 characters", Snackbar.LENGTH_SHORT, 3);
                } else {
                    signUp(firstNameText, lastNameText, email, password);
                }
            });

        });

        emailCheckIcon.setOnClickListener(v -> {
            Drawable current = emailCheckIcon.getDrawable();
            Drawable iconB = ContextCompat.getDrawable(requireContext(), R.drawable.ic_error);

            if (Util.isSameDrawable(current, iconB)) {
                emailEDT.setText("");
            }
        });

        return view;
    }
/*    private void signUp(String firstNameText, String lastNameText, String email, String password) {
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
            map.put("lastLogin", ServerValue.TIMESTAMP);
            map.put("photoUrl", "default");
            map.put("Bio", "");

            dbRef.child("users").child(mAuth.getCurrentUser().getUid()).setValue(map).
                    addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            dbRef.child("roles").child(id).setValue("Mentee");
//                            dbRef.child("roles").child(id).push().setValue("Mentee");
                            UserData userData = new UserData(
                                    id,
                                    email,
                                    "Mentee",
                                    firstNameText+" "+lastNameText, //display name
                                    firstNameText,
                                    lastNameText,
                                    "default",
                                    "",
                                    System.currentTimeMillis()
                            );
                            sharedUserViewModel.setUserData(userData);
                            sharedUserViewModel.saveUserData(userData);
                            Util.saveState(Constants.CURRENT_USER_ID,id);
                            Util.navigateToMainScreen(requireContext(), MainActivity.class, true);
                        } else {
                            String failureMessage = Util.getErrorString(task);
                            snackbarHandler.showSnackbar(failureMessage, Snackbar.LENGTH_SHORT, 3);
                        }
                    });
        });
    }

 */


    private void signUp(String firstNameText, String lastNameText, String email, String password) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) return;

                    String id = user.getUid();

                    HashMap<String, Object> map = new HashMap<>();
                    map.put("lastName", lastNameText);
                    map.put("email", email);
                    map.put("firstName", firstNameText);
                    map.put("displayName", "");
                    map.put("lastLogin", ServerValue.TIMESTAMP);
                    map.put("photoUrl", "default");
                    map.put("Bio", "");

                    dbRef.child("users").child(id).setValue(map)
                            .addOnSuccessListener(v -> {

                                dbRef.child("roles").child(id).setValue("Mentee");

                                // Send verification email
                                user.sendEmailVerification()
                                        .addOnSuccessListener(vv -> {
                                            // Save state: waiting for verification
                                            Util.saveState("AUTH_STATE", "VERIFY_EMAIL");

                                            snackbarHandler.showSnackbar(
                                                    "Verification email sent. Check your inbox.",
                                                    Snackbar.LENGTH_LONG,
                                                    3
                                            );

                                            // Navigate to Verify screen, NOT Main
                                            Util.navigateToMainScreen(
                                                    requireContext(),
                                                    VerifyEmailActivity.class,
                                                    true
                                            );
                                        })
                                        .addOnFailureListener(e -> {
                                            snackbarHandler.showSnackbar(
                                                    "Failed to send verification email",
                                                    Snackbar.LENGTH_SHORT,
                                                    3
                                            );
                                        });

                            })
                            .addOnFailureListener(e -> {
                                snackbarHandler.showSnackbar(
                                        e.getMessage(),
                                        Snackbar.LENGTH_SHORT,
                                        3
                                );
                            });
                })
                .addOnFailureListener(e -> {
                    snackbarHandler.showSnackbar(
                            e.getMessage(),
                            Snackbar.LENGTH_SHORT,
                            3
                    );
                });
    }
    private void handleGoogleSignIn(Intent data) {
        googleAuthHelper.handleSignInResult(
                data,
                GoogleSignInMode.REGISTER,
                userData -> {
                    FirebaseDatabase.getInstance().getReference("users").child(userData.getId()).get()
                            .addOnCompleteListener(task -> {
                                boolean exists = task.isSuccessful() && task.getResult().exists();
                                goToNextActivity(userData, exists);
                            });
                    return Unit.INSTANCE;
                },
                exception -> {
                    Log.e("Auth", "Sign in failed", exception);
                    snackbarHandler.showSnackbar(exception.getMessage() != null
                            ? exception.getMessage() : "Sign in failed", Snackbar.LENGTH_SHORT, 3);
                    return Unit.INSTANCE;
                }
        );
    }

    private void goToNextActivity(UserData userData, boolean isExistingUser) {
        googleAuthHelper.saveUserToFirebase(
                userData,
                isSuccess -> {
                    if (isSuccess) {
                        FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (authUser != null) {
                            userData.setId(authUser.getUid());
                            Util.saveState(Constants.CURRENT_USER_ID, authUser.getUid());
                        }
                        sharedUserViewModel.setUserData(userData);
                        sharedUserViewModel.saveUserData(userData);
                        String name = userData.getFirstName() != null && !userData.getFirstName().isEmpty()
                                ? userData.getFirstName() : "there";
                        String message = isExistingUser
                                ? "Welcome back, " + name + "!"
                                : "Welcome, " + name + "!";
                        snackbarHandler.showSnackbar(message, Snackbar.LENGTH_LONG, 4);
                        Util.navigateToMainScreen(requireContext(), MainActivity.class, true);
                    }
                    return Unit.INSTANCE;
                },
                exception -> {
                    snackbarHandler.showSnackbar("Could not save account. Please try again.", Snackbar.LENGTH_SHORT, 3);
                    return Unit.INSTANCE;
                }
        );
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}