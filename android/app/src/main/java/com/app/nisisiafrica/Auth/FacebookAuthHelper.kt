package com.app.nisisiafrica.Auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.app.nisisiafrica.Constants
import com.app.nisisiafrica.data.Model.UserData
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.app.nisisiafrica.Utils.Util
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
    private var signInMode: GoogleSignInMode = GoogleSignInMode.LOGIN

    private val onLoginSuccessListeners = mutableListOf<(UserData) -> Unit>()
    private val onLoginErrorListeners = mutableListOf<(Exception) -> Unit>()

    init {
        if (!FacebookSdk.isInitialized()) {
            FacebookSdk.sdkInitialize(activity.applicationContext)
        }
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                handleFacebookAccessToken(result.accessToken)
            }

            override fun onCancel() {
                Log.d(TAG, "facebook:onCancel")
            }

            override fun onError(error: FacebookException) {
                Log.e(TAG, "facebook:onError", error)
                notifyError(error)
            }
        })
    }

    fun signIn(
        mode: GoogleSignInMode = GoogleSignInMode.LOGIN,
        permissions: List<String> = listOf("email", "public_profile")
    ) {
        signInMode = mode
        LoginManager.getInstance().logInWithReadPermissions(activity, permissions)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    fun addOnLoginSuccessListener(listener: (UserData) -> Unit) {
        onLoginSuccessListeners.add(listener)
    }

    fun addOnLoginErrorListener(listener: (Exception) -> Unit) {
        onLoginErrorListeners.add(listener)
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        val credential = FacebookAuthProvider.getCredential(token.token)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        fetchGraphDataAndComplete(firebaseUser, token)
                    } else {
                        notifyError(IllegalStateException("Sign-in failed"))
                    }
                } else {
                    notifyError(task.exception ?: Exception("Facebook sign-in failed"))
                }
            }
    }

    private fun fetchGraphDataAndComplete(firebaseUser: FirebaseUser, token: AccessToken) {
        val request = GraphRequest.newMeRequest(token) { jsonObject, _ ->
            val firstName = jsonObject?.optString("first_name") ?: ""
            val lastName = jsonObject?.optString("last_name") ?: ""
            val displayName = jsonObject?.optString("name") ?: (firebaseUser.displayName ?: "")
            val email = jsonObject?.optString("email") ?: (firebaseUser.email ?: "")
            val pictureUrl = jsonObject?.optJSONObject("picture")
                ?.optJSONObject("data")
                ?.optString("url") ?: (firebaseUser.photoUrl?.toString() ?: "")

            val userData = UserData(
                id = firebaseUser.uid,
                email = email,
                displayName = displayName,
                firstName = firstName,
                lastName = lastName,
                photoUrl = pictureUrl,
                bio = ""
            )

            Util.saveState(Constants.CURRENT_USER_ID, firebaseUser.uid)
            handlePostAuth(firebaseUser.uid, userData)
        }

        val parameters = Bundle()
        parameters.putString("fields", "id,name,first_name,last_name,email,picture.type(large)")
        request.parameters = parameters
        request.executeAsync()
    }

    private fun handlePostAuth(userId: String, userData: UserData) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
        usersRef.get().addOnCompleteListener { task ->
            val exists = task.isSuccessful && task.result.exists()

            when (signInMode) {
                GoogleSignInMode.LOGIN -> {
                    if (!exists) {
                        auth.signOut()
                        LoginManager.getInstance().logOut()
                        notifyError(Exception("No account found. Please register first."))
                        return@addOnCompleteListener
                    }
                    completeWithRole(userId, userData, updateOnly = true)
                }
                GoogleSignInMode.REGISTER -> {
                    if (exists) {
                        completeWithRole(userId, userData, updateOnly = true)
                    } else {
                        completeWithRole(userId, userData, updateOnly = false)
                    }
                }
            }
        }
    }

    private fun completeWithRole(userId: String, userData: UserData, updateOnly: Boolean) {
        FirebaseRemoteDataSource.getOrAssignUserRole(
            firebaseUserId = userId,
            onSuccess = { role ->
                userData.userRole = role
                if (updateOnly) {
                    FirebaseDatabase.getInstance().getReference("users")
                        .child(userId).child("lastLogin").setValue(ServerValue.TIMESTAMP)
                    notifySuccess(userData)
                } else {
                    saveUserToFirebase(userData) { notifySuccess(userData) }
                }
            },
            onError = { e ->
                Log.e(TAG, "Role error", e)
                notifyError(e)
            }
        )
    }

    private fun saveUserToFirebase(userData: UserData, onComplete: () -> Unit) {
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        FirebaseRemoteDataSource.saveOrUpdateUser(
            userData,
            usersRef,
            onSuccess = { onComplete() },
            onError = { e -> notifyError(e) }
        )
    }

    private fun notifySuccess(userData: UserData) {
        onLoginSuccessListeners.forEach { it.invoke(userData) }
    }

    private fun notifyError(exception: Exception) {
        onLoginErrorListeners.forEach { it.invoke(exception) }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    fun signOut(onComplete: () -> Unit) {
        LoginManager.getInstance().logOut()
        auth.signOut()
        onComplete()
    }

    companion object {
        private const val TAG = "FacebookAuthHelper"
    }
}
