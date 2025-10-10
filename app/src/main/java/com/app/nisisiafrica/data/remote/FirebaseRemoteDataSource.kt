package com.app.nisisiafrica.data.remote

import android.content.Context
import android.util.Log
import com.app.nisisiafrica.Constants
import com.app.nisisiafrica.Interfaces.FirebaseCallback
import com.app.nisisiafrica.Utils.Util
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.MentorItem
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import kotlinx.coroutines.flow.Flow
import com.app.nisisiafrica.data.Model.UserData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

object FirebaseRemoteDataSource {
    private const val TAG = "FirebaseUserHelper"

    //todo update to use paging and migrate to use suspending functions
    fun saveOrUpdateUser(
           userData: UserData,
           usersRef: DatabaseReference,
           onSuccess: ((Boolean) -> Unit)?,
           onError: ((Exception) -> Unit)?
    ) {
        val user = hashMapOf(
            "id" to Util.getState(Constants.CURRENT_USER_ID,""),
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

    fun getUserAndData(
        firebaseCallback: FirebaseCallback
    ) {
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

    fun getMentorData(
        userID: String,
        firebaseCallback: FirebaseCallback
    ) {
        val dbRef = FirebaseDatabase.getInstance().reference
        if (userID.isEmpty()) {
            firebaseCallback.onMentorDataFetched(null)
            return
        }
        val mentorRef = dbRef.child("mentors").child(userID)
        mentorRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
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

    fun fetchCoursesByMentorId(
        mentorId: String,
        firebaseCallback: FirebaseCallback
    ) {
        val dbRef = FirebaseDatabase.getInstance().reference
        val coursesRef = dbRef.child("courses").orderByChild("tutorId").equalTo(mentorId)
        coursesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val courses = mutableListOf<CourseItem>()
                for (courseSnapshot in snapshot.children) {
                    val course = CourseItem(
                        tutorId = courseSnapshot.child("tutorId").getValue(String::class.java)
                            ?: "",
                        courseImageUrl = courseSnapshot.child("courseImageUrl")
                            .getValue(String::class.java) ?: "",
                        tutorAvatarUrl = courseSnapshot.child("tutorAvatarUrl")
                            .getValue(String::class.java) ?: "",
                        tutorName = courseSnapshot.child("tutorName").getValue(String::class.java)
                            ?: "",
                        courseTitle = courseSnapshot.child("courseTitle")
                            .getValue(String::class.java) ?: "",
                        duration = courseSnapshot.child("duration").getValue(String::class.java)
                            ?: "",
                        lessons = courseSnapshot.child("lessons").getValue(String::class.java)
                            ?: "",
                        courseLink = courseSnapshot.child("courseLink").getValue(String::class.java)
                            ?: "",
                        isLiked = courseSnapshot.child("isLiked").getValue(Boolean::class.java)
                            ?: false
                    )
                    courses.add(course)
                }
                firebaseCallback.onCoursesFetched(courses)
            }

            override fun onCancelled(error: DatabaseError) {
                firebaseCallback.onError(error.toException())
            }
        })
    }

    fun saveOrUpdateCourse(
        courseData: CourseItem,
        tutorId: String,
        onSuccess: ((Boolean) -> Unit)?,
        onError: ((Exception) -> Unit)?
    ) {
        val coursesRef = FirebaseDatabase.getInstance().reference.child("courses")
        val courseId: String = coursesRef.push().key.toString()
        val course = hashMapOf(
            "tutorId" to tutorId,
            "courseImageUrl" to courseData.courseImageUrl,
            "mentorImageUrl" to courseData.tutorAvatarUrl,
            "mentorName" to courseData.tutorName,
            "courseTitle" to courseData.courseTitle,
            "courseDuration" to courseData.duration,
            "courseLessons" to courseData.lessons,
            "courseLink" to courseData.courseLink
        )
        coursesRef.child(coursesRef.push().key.toString())
            .updateChildren(course as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Course data saved/updated successfully!")
                onSuccess?.invoke(true)
            }
            .addOnFailureListener { exception ->
                Log.e("FirebaseDB", "Failed to save/update course data", exception)
                onError?.invoke(exception)
            }
    }

    fun saveOrUpdateMentor(
        mentorData: MentorItem,
        onSuccess: ((Boolean) -> Unit)?,
        onError: ((Exception) -> Unit)?
    ) {
        val mentorsRef = FirebaseDatabase.getInstance().reference.child("mentors")
        val mentor = hashMapOf(
            "mentorId" to mentorData.mentorId,
            "mentorImageUrl" to mentorData.mentorImageUrl,
            "mentorName" to mentorData.mentorName,
            "mentorDescription" to mentorData.mentorDescription,
            "studentsCount" to mentorData.studentsCount,
            "studentImages" to mentorData.studentImages?.toList(),
            "bookedDates" to mentorData.bookedDates?.toList(),
//            "courses" to mentorData.courses?.toList()
        )

        mentorsRef.child(mentorData.mentorId)
            .updateChildren(mentor as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Mentor data saved/updated successfully!")
                onSuccess?.invoke(true)
            }
            .addOnFailureListener { exception ->
                Log.e("FirebaseDB", "Failed to save/update mentor data", exception)
                onError?.invoke(exception)
            }
    }

    fun fetchCourses(
        firebaseCallback: FirebaseCallback
    ) {
        val dbRef = FirebaseDatabase.getInstance().reference
        val coursesRef = dbRef.child("courses")

        coursesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val courses = mutableListOf<CourseItem>()
                for (courseSnapshot in snapshot.children) {
                    val course = CourseItem(
                        tutorId = courseSnapshot.child("mentorId").getValue(String::class.java)
                            ?: "",
                        courseImageUrl = courseSnapshot.child("courseImageUrl")
                            .getValue(String::class.java) ?: "",
                        tutorAvatarUrl = courseSnapshot.child("mentorImageUrl")
                            .getValue(String::class.java) ?: "",
                        tutorName = courseSnapshot.child("mentorName").getValue(String::class.java)
                            ?: "",
                        courseTitle = courseSnapshot.child("courseTitle")
                            .getValue(String::class.java) ?: "",
                        duration = courseSnapshot.child("courseDuration")
                            .getValue(String::class.java) ?: "",
                        lessons = courseSnapshot.child("courseLessons").getValue(String::class.java)
                            ?: "",
                        courseLink = courseSnapshot.child("courseLink").getValue(String::class.java)
                            ?: "",
                        isLiked = courseSnapshot.child("isLiked").getValue(Boolean::class.java)
                            ?: false
                    )
                    courses.add(course)
                }
                courses.shuffle()
                firebaseCallback.onCoursesFetched(courses)
            }

            override fun onCancelled(error: DatabaseError) {
                firebaseCallback.onError(error.toException())
            }
        })
    }

    fun fetchMentors(
        firebaseCallback: FirebaseCallback
    ) {
        val dbRef = FirebaseDatabase.getInstance().reference
        val mentorsRef = dbRef.child("mentors")
        mentorsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val mentors = mutableListOf<MentorItem>()
                for (mentorSnapshot in snapshot.children) {
                    val mentor = MentorItem(
                        mentorId = mentorSnapshot.child("mentorId").getValue(String::class.java)
                            ?: "",
                        mentorImageUrl = mentorSnapshot.child("mentorImageUrl")
                            .getValue(String::class.java) ?: "",
                        mentorName = mentorSnapshot.child("mentorName").getValue(String::class.java)
                            ?: "",
                        mentorDescription = mentorSnapshot.child("mentorDescription")
                            .getValue(String::class.java) ?: "",
                        studentsCount = mentorSnapshot.child("studentsCount")
                            .getValue(String::class.java),
                        studentImages = mentorSnapshot.child("studentImages")
                            .getValue(List::class.java) as? List<String>,
                        bookedDates = mentorSnapshot.child("bookedDates")
                            .getValue(Set::class.java) as? Set<LocalDate>
                    )
                    mentors.add(mentor)
                }
                mentors.shuffle()
                firebaseCallback.onMentorsFetched(mentors)
            }

            override fun onCancelled(error: DatabaseError) {
                firebaseCallback.onError(error.toException())
            }
        })
    }

    data class PagingKey(val startAfter: String?, val limit: Int)

    fun getCoursesPagingSource(dbRef: DatabaseReference): PagingSource<PagingKey, CourseItem> {
        return object : PagingSource<PagingKey, CourseItem>() {
            override suspend fun load(params: LoadParams<PagingKey>): LoadResult<PagingKey, CourseItem> {
                return try {
                    val pageSize = params.loadSize.coerceAtLeast(10)
                    val key = params.key ?: PagingKey(startAfter = null, limit = pageSize)

                    var query = dbRef.child("courses")
                        .orderByKey()
                        .limitToFirst(pageSize + 1) // +1 to check if more pages exist

                    if (key.startAfter != null) {
                        query = query.startAfter(key.startAfter)
                    }

                    val snapshot = query.get().await()
                    val allItems = snapshot.children.mapNotNull { courseSnapshot ->
                        courseSnapshot.key?.let { key ->
                            CourseItem(
                                tutorId = courseSnapshot.child("mentorId").getValue(String::class.java) ?: "",
                                courseImageUrl = courseSnapshot.child("courseImageUrl").getValue(String::class.java) ?: "",
                                tutorAvatarUrl = courseSnapshot.child("mentorImageUrl").getValue(String::class.java) ?: "",
                                tutorName = courseSnapshot.child("mentorName").getValue(String::class.java) ?: "",
                                courseTitle = courseSnapshot.child("courseTitle").getValue(String::class.java) ?: "",
                                duration = courseSnapshot.child("courseDuration").getValue(String::class.java) ?: "",
                                lessons = courseSnapshot.child("courseLessons").getValue(String::class.java) ?: "",
                                courseLink = courseSnapshot.child("courseLink").getValue(String::class.java) ?: "",
                                isLiked = courseSnapshot.child("isLiked").getValue(Boolean::class.java) ?: false
                            ) to key
                        }
                    }

                    val hasMore = allItems.size > pageSize
                    val courses = allItems.take(pageSize).map { it.first }.shuffled()
                    val keys = allItems.take(pageSize).map { it.second }

                    LoadResult.Page(
                        data = courses,
                        prevKey = null, // Forward pagination only
                        nextKey = if (hasMore) PagingKey(keys.lastOrNull(), pageSize) else null
                    )
                } catch (e: Exception) {
                    LoadResult.Error(e)
                }
            }

            override fun getRefreshKey(state: PagingState<PagingKey, CourseItem>): PagingKey? {
                return null
            }
        }
    }

    fun getMentorsPagingSource(dbRef: DatabaseReference): PagingSource<PagingKey, MentorItem> {
        return object : PagingSource<PagingKey, MentorItem>() {
            override suspend fun load(params: LoadParams<PagingKey>): LoadResult<PagingKey, MentorItem> {
                return try {
                    val pageSize = params.loadSize.coerceAtLeast(10)
                    val key = params.key ?: PagingKey(startAfter = null, limit = pageSize)

                    var query = dbRef.child("mentors")
                        .orderByKey()
                        .limitToFirst(pageSize + 1)

                    if (key.startAfter != null) {
                        query = query.startAfter(key.startAfter)
                    }

                    val snapshot = query.get().await()
                    val allItems = snapshot.children.mapNotNull { mentorSnapshot ->
                        mentorSnapshot.key?.let { key ->
                            MentorItem(
                                mentorId = mentorSnapshot.child("mentorId").getValue(String::class.java) ?: "",
                                mentorImageUrl = mentorSnapshot.child("mentorImageUrl").getValue(String::class.java) ?: "",
                                mentorName = mentorSnapshot.child("mentorName").getValue(String::class.java) ?: "",
                                mentorDescription = mentorSnapshot.child("mentorDescription").getValue(String::class.java) ?: "",
                                studentsCount = mentorSnapshot.child("studentsCount").getValue(String::class.java),
                                studentImages = mentorSnapshot.child("studentImages").getValue(Map::class.java)
                                    ?.values?.mapNotNull { it.toString() } as? List<String>,
                                bookedDates = mentorSnapshot.child("bookedDates").getValue(Map::class.java)
                                    ?.values?.mapNotNull {
                                        try { LocalDate.parse(it.toString()) } catch (_: Exception) { null }
                                    }?.toSet()
                            ) to key
                        }
                    }

                    val hasMore = allItems.size > pageSize
                    val mentors = allItems.take(pageSize).map { it.first }.shuffled()
                    val keys = allItems.take(pageSize).map { it.second }

                    LoadResult.Page(
                        data = mentors,
                        prevKey = null,
                        nextKey = if (hasMore) PagingKey(keys.lastOrNull(), pageSize) else null
                    )
                } catch (e: Exception) {
                    LoadResult.Error(e)
                }
            }

            override fun getRefreshKey(state: PagingState<PagingKey, MentorItem>): PagingKey? {
                return null
            }
        }
    }

    fun signOutAll(context: Context, onComplete: () -> Unit) {
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            .addOnCompleteListener {
                FirebaseAuth.getInstance().signOut()
                onComplete()
            }
    }

    fun updateUserBio(id: String, description: String) {
        val dbRef = FirebaseDatabase.getInstance().reference
        val userRef = dbRef.child("users").child(id)
        userRef.child("Bio").setValue(description)
    }
}