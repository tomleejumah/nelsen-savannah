package com.app.nisisiafrica.Auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.app.nisisiafrica.data.Model.UserData
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.GraphRequest
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class FacebookAuthHelper(private val activity: Activity) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val callbackManager: CallbackManager = CallbackManager.Factory.create()

    init {
        // Initialize Facebook SDK if not already initialized
        if (!FacebookSdk.isInitialized()) {
            FacebookSdk.sdkInitialize(activity.applicationContext)
        }
    }

    fun signIn(permissions: List<String> = listOf("email", "public_profile")) {
        LoginManager.getInstance().logInWithReadPermissions(activity, permissions)
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                Log.d(TAG, "facebook:onSuccess:$result")
                handleFacebookAccessToken(result.accessToken)
            }

            override fun onCancel() {
                Log.d(TAG, "facebook:onCancel")
            }

            override fun onError(error: FacebookException) {
                Log.e(TAG, "facebook:onError", error)
            }
        })
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Pass the activity result back to the Facebook SDK
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        Log.d(TAG, "handleFacebookAccessToken:$token")

        val credential = FacebookAuthProvider.getCredential(token.token)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    // Sign in success
                    Log.d(TAG, "signInWithCredential:success")
                    val firebaseUser = auth.currentUser
                    firebaseUser?.let {
                        // Call Graph API to fetch additional user info then save user data
                        fetchGraphDataAndSaveUser(it, token)
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    onLoginErrorListeners.forEach { listener -> listener.invoke(task.exception ?: Exception("Unknown error")) }
                }
            }
    }
    private fun fetchGraphDataAndSaveUser(firebaseUser: FirebaseUser, token: AccessToken) {
        val request = GraphRequest.newMeRequest(token) { jsonObject, response ->
            // Extract additional user info from the Graph API response
            val firstName = jsonObject?.optString("first_name") ?: ""
            val lastName = jsonObject?.optString("last_name") ?: ""
            val displayName = jsonObject?.optString("name") ?: (firebaseUser.displayName ?: "")
            val email = jsonObject?.optString("email") ?: (firebaseUser.email ?: "")

            // Extract picture URL (using large image type)
            val pictureUrl = jsonObject?.optJSONObject("picture")
                ?.optJSONObject("data")
                ?.optString("url") ?: (firebaseUser.photoUrl?.toString() ?: "")


            val userData = UserData(
                id = firebaseUser.uid,
                email = email,
//                userTYpe = "",
                displayName = displayName,
                firstName = firstName,
                lastName = lastName,
                photoUrl = pictureUrl,
                bio = ""
//                idToken = token.token
            )

//            val userId = FirebaseAuth.getInstance().currentUser?.uid
            val userId = firebaseUser.uid

                FirebaseRemoteDataSource.getOrAssignUserRole(
                    firebaseUserId = userId,
                    onSuccess = { role ->
                        userData.userRole = role
                        // Save the updated user data to Firebase
                        saveUserToFirebase(userData)
                        onLoginSuccessListeners.forEach { listener ->
                            listener.invoke(userData)
                        }
                    },
                    onError = { exception ->
                        Log.e("ROLE", "Error getting role: ${exception.message}")
                    }
                )
        }

        // Request additional fields from the Graph API
        val parameters = Bundle()
        parameters.putString("fields", "id,name,first_name,last_name,email,picture.type(large)")
        request.parameters = parameters
        request.executeAsync()
    }

    private val onLoginSuccessListeners = mutableListOf<(UserData) -> Unit>()
    private val onLoginErrorListeners = mutableListOf<(Exception) -> Unit>()

    fun addOnLoginSuccessListener(listener: (UserData) -> Unit) {
        onLoginSuccessListeners.add(listener)
    }

    fun addOnLoginErrorListener(listener: (Exception) -> Unit) {
        onLoginErrorListeners.add(listener)
    }

    private fun extractUserData(firebaseUser: FirebaseUser, token: AccessToken): UserData {
        return UserData(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName ?: "",
            firstName = "",  // We can get this from Graph API if needed
            lastName = "",   // We can get this from Graph API if needed
            photoUrl = firebaseUser.photoUrl?.toString() ?: "",
//            idToken = token.token
            bio = ""
        )
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    fun signOut(onComplete: () -> Unit) {
        LoginManager.getInstance().logOut()
        auth.signOut()
        onComplete()
    }
    private fun saveUserToFirebase(
        userData: UserData,
        onSuccess: ((Boolean) -> Unit)?=null,
        onError: ((Exception) -> Unit)?=null
    ) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")

        usersRef.child(userData.id).get().addOnCompleteListener { task ->
            if (task.isSuccessful && task.result.exists()) {
                Log.d("FirebaseDB", "User already exists, updating last login (Facebook)")
                usersRef.child(userData.id).child("lastLogin").setValue(ServerValue.TIMESTAMP)
                onSuccess?.invoke(true)
            } else {
                Log.d("FirebaseDB", "User does not exist, saving new user (Facebook)")
                FirebaseRemoteDataSource.saveOrUpdateUser(userData, usersRef, null, null)
            }
        }
    }

    companion object {
        private const val TAG = "FacebookAuthHelper"
    }
}