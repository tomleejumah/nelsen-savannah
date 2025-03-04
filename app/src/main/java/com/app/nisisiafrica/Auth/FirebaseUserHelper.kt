package com.app.nisisiafrica.Auth

import android.util.Log
import com.app.nisisiafrica.Model.UserData
import com.app.nisisiafrica.Utils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
        //todo rectify if they login
//       val userRole = Utils.getState("userRole","Mentee")
        val user = hashMapOf(
            "email" to userData.email,
            "displayName" to userData.displayName,
            "firstName" to userData.firstName,
            "lastName" to userData.lastName,
            "photoUrl" to userData.photoUrl,
            "lastLogin" to ServerValue.TIMESTAMP,
            "userRole" to userData.userRole
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

     fun checkCurrentUser() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // User is signed in, get their data from Firebase
            val userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.uid)

            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // User data found
                        val email = snapshot.child("email").getValue(String::class.java)
                        val name = snapshot.child("displayName").getValue(String::class.java)
                        // ... get other fields
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Firebase", "Error getting user data", error.toException())
                }
            })
        }
    }

}