package com.app.nisisiafrica.Auth.Fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.nisisiafrica.Auth.GoogleAuthHelper;
import com.app.nisisiafrica.BuildConfig;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.R;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SignUpFragment extends Fragment {
    private static final String TAG = "SignUpFragment";
    private GoogleAuthHelper googleAuthHelper;
    private ActivityResultLauncher<Intent> launcher;

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
                web_client_id  // Add this to strings.xml
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sign_up, container, false);

        Button signInButton = view.findViewById(R.id.googleBtn);
        signInButton.setOnClickListener(v -> googleAuthHelper.signIn());

        return view;
    }

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


    private void checkCurrentUser() {
        FirebaseUser user = googleAuthHelper.getCurrentUser();
        if (user != null) {
            // User is signed in, get their data from Firebase
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.getUid());

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // User data found
                        String email = snapshot.child("email").getValue(String.class);
                        String name = snapshot.child("displayName").getValue(String.class);
                        // ... get other fields
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("Firebase", "Error getting user data", error.toException());
                }
            });
        }
    }

}