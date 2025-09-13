package com.app.nisisiafrica.Utils

import android.content.Context
import android.util.Log
import com.app.nisisiafrica.FirebaseCallback
import com.app.nisisiafrica.Model.CourseItem
import com.app.nisisiafrica.Model.MentorItem
import com.app.nisisiafrica.Model.UserData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalDate

object FirebaseUserHelper {
    private const val TAG = "FirebaseUserHelper"
    fun saveOrUpdateUser(
        userData: UserData,
        usersRef: DatabaseReference,
        onSuccess: ((Boolean) -> Unit)?,
        onError: ((Exception) -> Unit)?
    ) {
        val user = hashMapOf(
            "id" to FirebaseAuth.getInstance().currentUser?.uid.toString(),
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

        val rolesRef = FirebaseDatabase.getInstance()
            .reference
            .child("roles")
            .child(firebaseUserId)

        rolesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val role = snapshot.getValue(String::class.java)
                Log.d("FirebaseDB", "User $firebaseUserId role: $role")
                if (snapshot.exists()) {
                    onSuccess(role ?: "Mentee")
                } else {
                    // Assign default role
                    rolesRef.setValue("Mentee")
                    Log.d("FirebaseDB", "User role assigned: Mentee")
                    onSuccess("Mentee")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
            }
        })

    }

    fun getUserAndData(firebaseCallback: FirebaseCallback) {
        val user = FirebaseAuth.getInstance().currentUser
        val dbRef = FirebaseDatabase.getInstance().reference

        if (user == null) {
            Log.d("Helper", "getCurrentUserAndData: No user found")
            firebaseCallback.onUserDataReceived(null)
            return
        }

        val userRef = dbRef.child("users").child(user.uid)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    firebaseCallback.onUserDataReceived(null)
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
                        firebaseCallback.onUserDataReceived(userData)
                        Log.d("ROLE", "User role is $role")
                    },
                    onError = { exception ->
                        Log.e("ROLE", "Error getting role: ${exception.message}")
                        firebaseCallback.onUserDataReceived(userData)
                    }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                firebaseCallback.onError(error.toException())
                Log.e("Firebase", "Error getting user data", error.toException())
            }
        })
    }

    //todo update this when mentors get fed to db
    fun getMentorData(userID: String, firebaseCallback: FirebaseCallback) {
        val dbRef = FirebaseDatabase.getInstance().reference
        if (userID.isEmpty()) {
            firebaseCallback.onMentorDataFetched(null)
            return
        }
        val mentorRef = dbRef.child("mentors").child(userID)

        mentorRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val mentorData = MentorItem(
                        mentorId = snapshot.child("mentorId").getValue(String::class.java) ?: "",
                        mentorImageUrl = snapshot.child("mentorImageUrl")
                            .getValue(String::class.java) ?: "",
                        mentorName = snapshot.child("mentorName").getValue(String::class.java)
                            ?: "",
                        mentorDescription = snapshot.child("mentorDescription")
                            .getValue(String::class.java) ?: "",
                        studentsCount = snapshot.child("studentsCount").getValue(String::class.java)
                            ?: "",
                        studentImages = snapshot.child("studentImages")
                            .getValue(List::class.java) as? List<String> ?: listOf(),
                        bookedDates = snapshot.child("bookedDates")
                            .getValue(Set::class.java) as? Set<LocalDate> ?: setOf(),
                        courses = snapshot.child("courses")
                            .getValue(List::class.java) as? List<CourseItem> ?: listOf(),

                        )
                    firebaseCallback.onMentorDataFetched(mentorData)
                } else {
                    firebaseCallback.onMentorDataFetched(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error getting user data", error.toException())
            }
        })
    }

    fun saveOrUpdateMentor(
        mentorData: MentorItem,
        mentorsRef: DatabaseReference,
        onSuccess: ((Boolean) -> Unit)?,
        onError: ((Exception) -> Unit)?
    ) {
        val mentor = hashMapOf(
            "mentorId" to mentorData.mentorId,
            "mentorImageUrl" to mentorData.mentorImageUrl,
            "mentorName" to mentorData.mentorName,
            "mentorDescription" to mentorData.mentorDescription,
            "studentsCount" to mentorData.studentsCount,
            "studentImages" to mentorData.studentImages,
            "bookedDates" to mentorData.bookedDates,
            "courses" to mentorData.courses
        )
    }


    fun signOutAll(context: Context, onComplete: () -> Unit) {
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            .addOnCompleteListener {
                FirebaseAuth.getInstance().signOut()
                onComplete()
            }
    }

}