package com.app.nisisiafrica.Auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
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
//        val isMailSinged = isMailSignedIn()

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
                idToken = account.idToken ?: ""
            )

            // Sign in to Firebase
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    // You can get additional Firebase user data here
                    val firebaseUser = authResult.user
                    onSuccess(userData)
                }.addOnFailureListener { exception ->
                    onError(exception)
                }

        } catch (e: ApiException) {
            onError(e)
            Log.e(TAG, "signInResult:failed code=${e.statusCode}")
        }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    data class UserData(
        val id: String,
        val email: String,
        val displayName: String,
        val firstName: String,
        val lastName: String,
        val photoUrl: String,
        val idToken: String
    )

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

        usersRef.child(userData.id).get().addOnCompleteListener { task ->
            if (task.isSuccessful && task.result.exists()) {
                Log.d("FirebaseDB", "User already exists, skipping save")
                onSuccess(true)
            } else {
                Log.d("FirebaseDB", "User does not exist, saving new user")

                val user = hashMapOf(
                    "email" to userData.email,
                    "displayName" to userData.displayName,
                    "firstName" to userData.firstName,
                    "lastName" to userData.lastName,
                    "photoUrl" to userData.photoUrl,
                    "lastLogin" to ServerValue.TIMESTAMP
                )

                usersRef.child(userData.id).updateChildren(user as Map<String, Any>)
                    .addOnSuccessListener {
                        Log.d("FirebaseDB", "User data saved successfully!")
                        onSuccess(true)
                    }
                    .addOnFailureListener { exception ->
                        Log.e("FirebaseDB", "Failed to save user data", exception)
                        onError(exception)
                    }
            }
        }
    }
}