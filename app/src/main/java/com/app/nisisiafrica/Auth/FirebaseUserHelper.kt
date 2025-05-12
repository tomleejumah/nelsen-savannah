package com.app.nisisiafrica.Auth

import android.util.Log
import com.app.nisisiafrica.Model.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

object FirebaseUserHelper {
    fun saveOrUpdateUser(
        userData: UserData,
        usersRef: DatabaseReference,
        onSuccess: ((Boolean) -> Unit)?,
        onError: ((Exception) -> Unit)?
    ) {
//       val userRole = Utils.getState("userRole","Mentee")
        val user = hashMapOf(
            "email" to userData.email,
            "displayName" to userData.displayName,
            "firstName" to userData.firstName,
            "lastName" to userData.lastName,
            "photoUrl" to userData.photoUrl,
            "lastLogin" to ServerValue.TIMESTAMP,
//            "userRole" to userData.userRole
        )

        usersRef.child(userData.id).updateChildren(user as Map<String, Any>)
            .addOnSuccessListener {
                Log.d("FirebaseDB", "User data saved/updated successfully!")
                onSuccess?.invoke(true)
            }
            .addOnFailureListener { exception ->
                Log.e("FirebaseDB", "Failed to save/update user data", exception)
                onError?.invoke(exception)
            }
    }

    fun getCurrentUserAndData(userDataCallback: UserDataCallback) {
        val user = FirebaseAuth.getInstance().currentUser
        val dbRef = FirebaseDatabase.getInstance().reference

        if (user == null) {
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
                    id = snapshot.child("id").getValue(String::class.java) ?: "",
                    email = snapshot.child("email").getValue(String::class.java) ?: "",
//                            userRole = snapshot.child("userRole").getValue(String::class.java),
                    displayName = snapshot.child("displayName")
                        .getValue(String::class.java),
                    firstName = snapshot.child("firstName").getValue(String::class.java)
                        ?: "",
                    lastName = snapshot.child("lastName").getValue(String::class.java)
                        ?: "",
                    photoUrl = snapshot.child("photoUrl").getValue(String::class.java),
//                    idToken = snapshot.child("idToken").getValue(String::class.java)
                )
                dbRef.child("roles").child(user.uid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val role = snapshot.getValue(String::class.java)
                            userData.userRole = role ?: "Mentee"
                            userDataCallback.onUserDataReceived(userData)
                            if (!snapshot.exists()) {
                                // Correctly set the role in Firebase
                                dbRef.child("roles")
                                    .child(user.uid)
                                    .setValue("Mentee")
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            userDataCallback.onUserDataReceived(userData)
                        }
                    })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error getting user data", error.toException())
            }
        })
    }
}