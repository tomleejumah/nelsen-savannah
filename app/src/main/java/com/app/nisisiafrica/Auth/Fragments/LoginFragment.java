package com.app.nisisiafrica.Auth.Fragments;

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
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.nisisiafrica.Auth.FacebookAuthHelper;
import com.app.nisisiafrica.Auth.GoogleSignInMode;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.app.nisisiafrica.Auth.ForgotPasswordActivity;
import com.app.nisisiafrica.Auth.GoogleAuthHelper;
import com.app.nisisiafrica.Auth.GoogleSignInMode;
import com.app.nisisiafrica.BuildConfig;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Interfaces.SnackbarHandler;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.databinding.FragmentLoginBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Arrays;
import java.util.List;

import kotlin.Unit;

//todo switch completely to binding also imp the firebase db helper
public class LoginFragment extends Fragment {
    private static final String TAG = "LoginFragment";
    private FragmentLoginBinding binding;
    private GoogleAuthHelper googleAuthHelper;
    private FacebookAuthHelper facebookAuthHelper;
    private ImageView emailCheckIcon;
    private SnackbarHandler snackbarHandler;
    private UserViewModel sharedUserViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedUserViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);

        ActivityResultLauncher<Intent> launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "Sign-in successful, fetching data...");
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
            sharedUserViewModel.saveUserData(userData);
            sharedUserViewModel.setUserData(userData);
            String name = userData.getFirstName() != null && !userData.getFirstName().isEmpty()
                    ? userData.getFirstName() : "back";
            snackbarHandler.showSnackbar("Welcome back, " + name + "!", Snackbar.LENGTH_LONG, 4);
            Util.navigateToMainScreen(requireContext(), MainActivity.class, true);
            return Unit.INSTANCE;
        });

        facebookAuthHelper.addOnLoginErrorListener(exception -> {
            // Handle login error
            Log.e("Facebook", "Login failed", exception);
            snackbarHandler.showSnackbar("Login failed please retry", Snackbar.LENGTH_SHORT, 3);
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
        facebookAuthHelper.handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
//        return inflater.inflate(R.layout.fragment_login, container, false);
        binding = FragmentLoginBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
                view.findViewById(R.id.mailError).setVisibility(View.GONE);
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
                view.findViewById(R.id.passError).setVisibility(View.GONE);
            }
        });

        emailCheckIcon.setOnClickListener(v -> {
            Drawable current = emailCheckIcon.getDrawable();
            Drawable iconB = ContextCompat.getDrawable(requireContext(), R.drawable.ic_error);

            if (Util.isSameDrawable(current, iconB)) {
                emailEDT.setText("");
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
            passEDT.setSelection(passEDT.getText().length());
        });

        view.findViewById(R.id.googleBtnL).setOnClickListener(v -> {
            Util.setClickAnimation(v, () -> googleAuthHelper.signIn());
        });

        view.findViewById(R.id.facebookBtn).setOnClickListener(v -> {
            List<String> permissions = Arrays.asList("email", "public_profile");
            Util.setClickAnimation(v, () -> facebookAuthHelper.signIn(GoogleSignInMode.LOGIN, permissions));
        });

        view.findViewById(R.id.txtForgotPwsd).setOnClickListener(v -> {
            Util.setClickAnimation(v, () -> {
                startActivity(new Intent(requireActivity(), ForgotPasswordActivity.class));
            });
        });

        binding.checkKeepMeIn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Util.saveState("keepMeIn", isChecked);
        });

        view.findViewById(R.id.btnLogin).setOnClickListener(v -> {
            Util.setClickAnimation(v, () -> {
                String email = emailEDT.getText().toString();
                String password = passEDT.getText().toString();

                if (email.isEmpty() || password.isEmpty()) {
                    snackbarHandler.showSnackbar("Please fill in the blanks", Snackbar.LENGTH_SHORT, 2);
                    view.findViewById(R.id.mailError).setVisibility(email.isEmpty() ? View.VISIBLE : View.GONE);
                    view.findViewById(R.id.passError).setVisibility(password.isEmpty() ? View.VISIBLE : View.GONE);
                    Util.shakeView(view.findViewById(R.id.passParent));
                    Util.shakeView(view.findViewById(R.id.mailParent));

                } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    snackbarHandler.showSnackbar("Please enter a valid email", Snackbar.LENGTH_SHORT, 2);
                    emailCheckIcon.setImageResource(R.drawable.ic_error);
                    view.findViewById(R.id.mailError).setVisibility(View.VISIBLE);
                    emailCheckIcon.setVisibility(View.VISIBLE);
                    Util.shakeView(view.findViewById(R.id.mailParent));

                } else if (Util.isPasswordTooShort(password)) {
                    snackbarHandler.showSnackbar("Password must be at least 6 characters", Snackbar.LENGTH_SHORT, 3);
                    Util.shakeView(view.findViewById(R.id.passParent));

                } else {
                    login(email, password);
                }
            });
        });
    }

    private void login(String email, String password) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                com.google.firebase.auth.FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser == null) {
                    snackbarHandler.showSnackbar("Sign-in failed. Please try again.", Snackbar.LENGTH_SHORT, 3);
                    return;
                }
                FirebaseRemoteDataSource.INSTANCE.getRemoteUserData(currentUser.getUid(), userData -> {
                    if (userData == null) {
                        snackbarHandler.showSnackbar("Account data not found. Please contact support.", Snackbar.LENGTH_LONG, 3);
                        return Unit.INSTANCE;
                    }
                    String userId = currentUser.getUid();
                    Util.saveState(Constants.CURRENT_USER_ID, userId);
                    userData.setId(userId);
                    sharedUserViewModel.saveUserData(userData);
                    sharedUserViewModel.setUserData(userData);
                    Util.navigateToMainScreen(getContext(), MainActivity.class, true);
                    Log.d(TAG, "login: Success");
                    return Unit.INSTANCE;
                }, e -> {
                    snackbarHandler.showSnackbar("Could not load profile. Please try again.", Snackbar.LENGTH_SHORT, 3);
                    return Unit.INSTANCE;
                });
            } else {
                String failureMessage = Util.getErrorString(task);
                snackbarHandler.showSnackbar(failureMessage, Snackbar.LENGTH_SHORT, 3);
            }
        });
    }

    private void handleGoogleSignIn(Intent data) {
        googleAuthHelper.handleSignInResult(
                data,
                GoogleSignInMode.LOGIN,
                userData -> {
                    goToNextActivity(userData);
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

    private void goToNextActivity(UserData userData) {
        sharedUserViewModel.saveUserData(userData);
        sharedUserViewModel.setUserData(userData);
        String name = userData.getFirstName() != null && !userData.getFirstName().isEmpty()
                ? userData.getFirstName() : "back";
        snackbarHandler.showSnackbar("Welcome back, " + name + "!", Snackbar.LENGTH_LONG, 4);
        Util.navigateToMainScreen(requireContext(), MainActivity.class, true);
    }
}
