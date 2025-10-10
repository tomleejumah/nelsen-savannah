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
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    fun handleSignInResult(
        data: Intent?,
        onSuccess: (UserData) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            // Get Google Sign In data
            val userData = UserData(
                id = account.id ?: "",
                email = account.email ?: "",
                displayName = account.displayName ?: "",
                firstName = account.givenName ?: "",
                lastName = account.familyName ?: "",
                photoUrl = account.photoUrl?.toString() ?: "",
                bio = ""
//                idToken = account.idToken ?: ""
            )

            // Sign in to Firebase
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val userId = authResult.user?.uid ?: return@addOnSuccessListener
                    Util.saveState(Constants.CURRENT_USER_ID, userId)
                    userData.id = userId

                    FirebaseRemoteDataSource.getOrAssignUserRole(
                        firebaseUserId = userId,
                        onSuccess = { role ->
                            // User role fetched/assigned successfully
                            userData.userRole = role
                            onSuccess(userData)
                        },
                        onError = { exception ->
                            Log.e("ROLE", "Error getting role: ${exception.message}")
                        }
                    )

                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }


        } catch (e: ApiException) {
            onError(e)
            Log.e(TAG, "signInResult:failed code=${e.statusCode}")
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    fun signOut(onComplete: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }

    private fun isMailSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(activity) != null
    }

    companion object {
        private const val TAG = "GoogleAuthHelper"
    }

    fun saveUserToFirebase(
        userData: UserData,
        onSuccess: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        val loggedInUser = FirebaseAuth.getInstance().currentUser?.uid
        usersRef.child(loggedInUser.toString()).get().addOnCompleteListener { task ->
            if (task.isSuccessful && task.result.exists()) {
                Log.d("FirebaseDB", "User already exists, updating last login (Google)")
                usersRef.child(loggedInUser.toString()).child("lastLogin").setValue(ServerValue.TIMESTAMP)
                //todo only update this if profile dp flag is not updated
                usersRef.child(loggedInUser.toString()).child("photoUrl").setValue(userData.photoUrl)
                onSuccess(true)
            } else {
                Log.d("FirebaseDB", "User does not exist, saving new user (Google)")
                userData.id = loggedInUser.toString()
                FirebaseRemoteDataSource.saveOrUpdateUser(userData, usersRef, onSuccess, onError)
            }
        }
    }
}