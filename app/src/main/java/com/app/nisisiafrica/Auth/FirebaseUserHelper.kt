package com.app.nisisiafrica.Auth

import android.content.Context
import android.util.Log
import com.app.nisisiafrica.Model.UserData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirebaseUserHelper {
    private const val TAG = "FirebaseUserHelper"
    fun saveOrUpdateUser(
        userData: UserData,
        usersRef: DatabaseReference,
        onSuccess: ((Boolean) -> Unit)?,
        onError: ((Exception) -> Unit)?
    ) {
        val user = hashMapOf(
            "email" to userData.email,
            "displayName" to userData.displayName,
            "firstName" to userData.firstName,
            "lastName" to userData.lastName,
            "photoUrl" to userData.photoUrl,
            "lastLogin" to System.currentTimeMillis(),
            "Bio" to ""
        )

        usersRef.child(FirebaseAuth.getInstance().currentUser?.uid.toString())
            .updateChildren(user as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "User data saved/updated successfully!")
                onSuccess?.invoke(true)
            }
            .addOnFailureListener { exception ->
                Log.e("FirebaseDB", "Failed to save/update user data", exception)
                onError?.invoke(exception)
            }
    }


    fun getOrAssignUserRole(
        firebaseUserId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val rolesRef = FirebaseDatabase.getInstance().reference

        rolesRef.child("roles/${firebaseUserId}")
        rolesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.hasChild(firebaseUserId)) {
                    val role = snapshot.child(firebaseUserId).getValue(String::class.java)
                    Log.d("FirebaseDB", "User role fetched: $role")
                    onSuccess(role ?: "Mentee")
                } else {
                    // Assign default role
                    rolesRef.child("roles").child(firebaseUserId).setValue("Mentee")
                    onSuccess("Mentee")
                }

            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
            }
        })
    }

    fun getCurrentUserAndData(userDataCallback: UserDataCallback) {
        val user = FirebaseAuth.getInstance().currentUser
        val dbRef = FirebaseDatabase.getInstance().reference

        if (user == null) {
            Log.d("Helper", "getCurrentUserAndData: No user found")
            userDataCallback.onUserDataReceived(null)
            return
        }

        val userRef = dbRef.child("users").child(user.uid)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    userDataCallback.onUserDataReceived(null)
                    return
                }

                // User data found
                val userData = UserData(
                    id = user.uid,
                    email = snapshot.child("email").getValue(String::class.java) ?: "",
//                    userRole = snapshot.child("userRole").getValue(String::class.java) ?: "Mentee",
                    displayName = snapshot.child("displayName").getValue(String::class.java) ?: "",
                    firstName = snapshot.child("firstName").getValue(String::class.java) ?: "",
                    lastName = snapshot.child("lastName").getValue(String::class.java) ?: "",
                    photoUrl = snapshot.child("photoUrl").getValue(String::class.java) ?: "",
                    bio = snapshot.child("Bio").getValue(String::class.java) ?: ""

//                    idToken = snapshot.child("idToken").getValue(String::class.jaServerValue.TIMESTAMP.toString()va)
                )

                getOrAssignUserRole(
                    firebaseUserId = user.uid,
                    onSuccess = { role ->
                        // User role fetched/assigned successfully
                        userData.userRole = role
                        userDataCallback.onUserDataReceived(userData)
                        Log.d("ROLE", "User role is $role")
                    },
                    onError = { exception ->
                        Log.e("ROLE", "Error getting role: ${exception.message}")
                        userDataCallback.onUserDataReceived(userData)
                    }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error getting user data", error.toException())
            }
        })
    }

    fun signOutAll(context: Context, onComplete: () -> Unit) {
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            .addOnCompleteListener {
                FirebaseAuth.getInstance().signOut()
                onComplete()
            }
    }

}