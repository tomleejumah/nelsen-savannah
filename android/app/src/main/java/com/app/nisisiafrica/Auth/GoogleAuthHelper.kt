package com.app.nisisiafrica.Auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.app.nisisiafrica.Constants
import com.app.nisisiafrica.data.Model.UserData
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.app.nisisiafrica.Utils.Util
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

enum class GoogleSignInMode {
    LOGIN, REGISTER
}

class GoogleAuthHelper(
    private val activity: Activity,
    private val launcher: ActivityResultLauncher<Intent>,
    private val webClientId: String
) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(webClientId)
            .build()
        GoogleSignIn.getClient(activity, gso)
    }

    fun signIn() {
        launcher.launch(googleSignInClient.signInIntent)
    }

    fun handleSignInResult(
        data: Intent?,
        mode: GoogleSignInMode,
        onSuccess: (UserData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                onError(
                    IllegalStateException(
                        "Google sign-in misconfigured (missing ID token). Check WEB_CLIENT_ID.",
                    ),
                )
                return
            }

            val userData = UserData(
                id = account.id ?: "",
                email = account.email ?: "",
                displayName = account.displayName ?: "",
                firstName = account.givenName ?: "",
                lastName = account.familyName ?: "",
                photoUrl = account.photoUrl?.toString() ?: "",
                bio = ""
            )

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val userId = authResult.user?.uid
                    if (userId == null) {
                        onError(IllegalStateException("Sign-in failed. Please try again."))
                        return@addOnSuccessListener
                    }
                    Util.saveState(Constants.CURRENT_USER_ID, userId)
                    userData.id = userId
                    handlePostAuth(userId, userData, mode, onSuccess, onError)
                }
                .addOnFailureListener { exception -> onError(exception) }
        } catch (e: ApiException) {
            onError(e)
            Log.e(TAG, "signInResult:failed code=${e.statusCode}")
        }
    }

    private fun handlePostAuth(
        userId: String,
        userData: UserData,
        mode: GoogleSignInMode,
        onSuccess: (UserData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        usersRef.get().addOnCompleteListener { task ->
            val exists = task.isSuccessful && task.result.exists()

            when (mode) {
                GoogleSignInMode.LOGIN -> {
                    if (!exists) {
                        signOutFirebaseAndGoogle {
                            onError(Exception("No account found. Please register first."))
                        }
                        return@addOnCompleteListener
                    }
                    loadExistingUser(userId, userData, onSuccess, onError)
                }
                GoogleSignInMode.REGISTER -> {
                    if (exists) {
                        loadExistingUser(userId, userData, onSuccess, onError)
                    } else {
                        assignRoleAndComplete(userId, userData, onSuccess, onError)
                    }
                }
            }
        }
    }

    private fun loadExistingUser(
        userId: String,
        fallback: UserData,
        onSuccess: (UserData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        FirebaseRemoteDataSource.getRemoteUserData(
            userId,
            onSuccess = { remote ->
                val resolved = remote ?: fallback
                FirebaseRemoteDataSource.getOrAssignUserRole(
                    firebaseUserId = userId,
                    onSuccess = { role ->
                        resolved.userRole = role
                        resolved.id = userId
                        FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(userId)
                            .child("lastLogin")
                            .setValue(ServerValue.TIMESTAMP)
                        onSuccess(resolved)
                    },
                    onError = { e ->
                        Log.e(TAG, "Role fetch failed", e)
                        onError(e)
                    }
                )
            },
            onError = { e ->
                Log.e(TAG, "Remote user fetch failed", e)
                onError(e)
            }
        )
    }

    private fun assignRoleAndComplete(
        userId: String,
        userData: UserData,
        onSuccess: (UserData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        FirebaseRemoteDataSource.getOrAssignUserRole(
            firebaseUserId = userId,
            onSuccess = { role ->
                userData.userRole = role
                onSuccess(userData)
            },
            onError = { e ->
                Log.e(TAG, "Role assignment failed", e)
                onError(e)
            }
        )
    }

    private fun signOutFirebaseAndGoogle(onComplete: () -> Unit) {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    fun signOut(onComplete: () -> Unit) {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }

    fun saveUserToFirebase(
        userData: UserData,
        onSuccess: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        val loggedInUser = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onError(IllegalStateException("Not signed in"))
            return
        }
        usersRef.child(loggedInUser).get().addOnCompleteListener { task ->
            if (task.isSuccessful && task.result.exists()) {
                Log.d(TAG, "User already exists, updating last login (Google)")
                usersRef.child(loggedInUser).child("lastLogin").setValue(ServerValue.TIMESTAMP)
                usersRef.child(loggedInUser).child("photoUrl").setValue(userData.photoUrl)
                onSuccess(true)
            } else {
                Log.d(TAG, "Saving new user (Google)")
                userData.id = loggedInUser
                FirebaseRemoteDataSource.saveOrUpdateUser(userData, usersRef, onSuccess, onError)
            }
        }
    }

    companion object {
        private const val TAG = "GoogleAuthHelper"
    }
}
