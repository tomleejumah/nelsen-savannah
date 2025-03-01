package com.app.nisisiafrica.Auth

import android.util.Log
import com.app.nisisiafrica.Model.UserData
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue

object FirebaseUserHelper {
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
            "lastLogin" to ServerValue.TIMESTAMP
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
}