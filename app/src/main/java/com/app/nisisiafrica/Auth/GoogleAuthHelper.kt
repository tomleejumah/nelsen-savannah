package com.app.nisisiafrica.Auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.app.nisisiafrica.Model.UserData
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
                idToken = account.idToken ?: ""
            )

            // Sign in to Firebase
//            Log.d(TAG, "handleSignInResult:ID ${userData.id}")
//            Log.d(TAG, "handleSignInResult:IDToken ${userData.idToken}")
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { authResult ->
                    val firebaseUser = authResult.user?.uid.orEmpty()

                    FirebaseDatabase.getInstance().reference
                        .child("roles")
                        .child(firebaseUser)
                        .get()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val snapshot = task.result
                                if (snapshot.exists() && snapshot.children.iterator().hasNext()) {
                                    val role = snapshot.children.first().getValue(String::class.java) ?: "Mentee"
                                    userData.userRole = role
                                } else {
                                    // No role assigned yet; assign default role
                                    FirebaseDatabase.getInstance().reference
                                        .child("roles")
                                        .child(firebaseUser)
                                        .push()
                                        .setValue("Mentee")
                                    userData.userRole = "Mentee"
                                }
                                onSuccess(userData)
                            } else {
                                onError(task.exception ?: Exception("Failed to get role"))
                            }
                        }
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
//        val loggedInUser = auth.currentUser
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        val loggedInUser = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "saveUserToFirebase: ${loggedInUser.toString()}")
        usersRef.child(loggedInUser.toString()).get().addOnCompleteListener { task ->
            if (task.isSuccessful && task.result.exists()) {
                Log.d("FirebaseDB", "User already exists, updating last login (Google)")
                usersRef.child(loggedInUser.toString()).child("lastLogin").setValue(ServerValue.TIMESTAMP)
                onSuccess(true)
            } else {
                Log.d("FirebaseDB", "User does not exist, saving new user (Google)")
                FirebaseUserHelper.saveOrUpdateUser(userData, usersRef, onSuccess, onError)
            }
        }
    }
}