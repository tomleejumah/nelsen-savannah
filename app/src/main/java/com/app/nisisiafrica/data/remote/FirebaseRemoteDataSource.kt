package com.app.nisisiafrica.data.remote

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.app.nisisiafrica.Constants
import com.app.nisisiafrica.Interfaces.FirebaseCallback
import com.app.nisisiafrica.Utils.Util
import com.app.nisisiafrica.data.Model.Booking
import com.app.nisisiafrica.data.Model.Chatroom
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.Model.MentorItem
import com.app.nisisiafrica.data.Model.UserData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
            "id" to Util.getState(Constants.CURRENT_USER_ID, ""),
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


    //todo switch to view paging and update the ui to a scrollable (each item should have its event)
    suspend fun getBookedDates(userId: String): List<Booking> = suspendCoroutine { cont ->
        val datesRef = FirebaseDatabase.getInstance().reference.child("bookedDates").child(userId)

        datesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dates = mutableListOf<Booking>()
                for (dateSnapshot in snapshot.children) {
                    val booking = Booking(
                        id = dateSnapshot.child("id").getValue(Int::class.java) ?: 0,
                        mentorId = dateSnapshot.child("mentorId").getValue(String::class.java)
                            ?: "",
                        studentId = dateSnapshot.child("studentId").getValue(String::class.java)
                            ?: "",
                        date = dateSnapshot.child("date").getValue(String::class.java) ?: "",
                        time = dateSnapshot.child("time").getValue(String::class.java) ?: ""
                    )

                    dates.add(booking)
                }
                cont.resume(dates)
            }

            override fun onCancelled(error: DatabaseError) {
                cont.resumeWithException(error.toException())
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
        onSuccess: ((Boolean) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val coursesRef = FirebaseDatabase.getInstance().reference.child("courses")
        var courseId = courseData.courseId
//            ?: coursesRef.push().key
        if (courseId.isEmpty()){
            courseId = coursesRef.push().key.toString()
        }


//        if (courseId == null) {
//
//            onError?.invoke(Exception("Failed to generate course ID"))
//            return
//        }

        val course = hashMapOf(
            "courseId" to courseId,
            "tutorId" to tutorId,
            "courseImageUrl" to courseData.courseImageUrl,
            "mentorImageUrl" to courseData.tutorAvatarUrl,
            "mentorName" to courseData.tutorName,
            "courseTitle" to courseData.courseTitle,
            "courseDuration" to courseData.duration,
            "courseLessons" to courseData.lessons,
            "courseLink" to courseData.courseLink
        )

        coursesRef.child(courseId)
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
                        courseId = courseSnapshot.child("courseId").getValue(String::class.java)
                            ?: "",
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
                        .limitToFirst(pageSize + 1)

                    if (key.startAfter != null) {
                        query = query.startAfter(key.startAfter)
                    }

                    val snapshot = query.get().await()
                    val allItems = snapshot.children.mapNotNull { courseSnapshot ->
                        courseSnapshot.key?.let { key ->
                            CourseItem(
                                courseId = courseSnapshot.child("courseId")
                                    .getValue(String::class.java) ?: "",
                                tutorId = courseSnapshot.child("tutorId")
                                    .getValue(String::class.java) ?: "",
                                courseImageUrl = courseSnapshot.child("courseImageUrl")
                                    .getValue(String::class.java) ?: "",
                                tutorAvatarUrl = courseSnapshot.child("mentorImageUrl")
                                    .getValue(String::class.java) ?: "",
                                tutorName = courseSnapshot.child("mentorName")
                                    .getValue(String::class.java) ?: "",
                                courseTitle = courseSnapshot.child("courseTitle")
                                    .getValue(String::class.java) ?: "",
                                duration = courseSnapshot.child("courseDuration")
                                    .getValue(String::class.java) ?: "",
                                lessons = courseSnapshot.child("courseLessons")
                                    .getValue(String::class.java) ?: "",
                                courseLink = courseSnapshot.child("courseLink")
                                    .getValue(String::class.java) ?: "",
                                isLiked = courseSnapshot.child("isLiked")
                                    .getValue(Boolean::class.java) ?: false
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
                                mentorId = mentorSnapshot.child("mentorId")
                                    .getValue(String::class.java) ?: "",
                                mentorImageUrl = mentorSnapshot.child("mentorImageUrl")
                                    .getValue(String::class.java) ?: "",
                                mentorName = mentorSnapshot.child("mentorName")
                                    .getValue(String::class.java) ?: "",
                                mentorDescription = mentorSnapshot.child("mentorDescription")
                                    .getValue(String::class.java) ?: "",
                                studentsCount = mentorSnapshot.child("studentsCount")
                                    .getValue(String::class.java),
                                studentImages = mentorSnapshot.child("studentImages")
                                    .getValue(Map::class.java)
                                    ?.values?.mapNotNull { it.toString() } as? List<String>,
                                bookedDates = mentorSnapshot.child("bookedDates")
                                    .getValue(Map::class.java)
                                    ?.values?.mapNotNull {
                                        try {
                                            LocalDate.parse(it.toString())
                                        } catch (_: Exception) {
                                            null
                                        }
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

    fun initAnnouncementChatRoom(){
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("chatRooms").document("announcements")
            .set(
                hashMapOf(
                    "chatroomId" to "announcements",
                    "userIds" to FieldValue.arrayUnion(userId),
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "lastMessage" to "Welcome to announcements"
                ),
                SetOptions.merge()
            )
    }

    fun getChatRoomsPagingSource(): PagingSource<QuerySnapshot, Chatroom> {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUserId = auth.currentUser?.uid
            ?: return InvalidPagingSource()

        val query = firestore.collection("chatRooms")
            .whereArrayContains("userIds", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        return object : PagingSource<QuerySnapshot, Chatroom>() {
            override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, Chatroom> {
                return try {
                    val pageSize = params.loadSize.coerceAtLeast(20)

                    val currentQuery = params.key?.let {
                        query.startAfter(it.documents.last()).limit(pageSize.toLong())
                    } ?: query.limit(pageSize.toLong())

                    val snapshot = currentQuery.get().await()

                    LoadResult.Page(
                        data = snapshot.toObjects(Chatroom::class.java),
                        prevKey = null,
                        nextKey = if (snapshot.size() == pageSize) snapshot else null
                    )
                } catch (e: Exception) {
                    LoadResult.Error(e)
                }
            }

            override fun getRefreshKey(state: PagingState<QuerySnapshot, Chatroom>) = null
        }
    }

    private class InvalidPagingSource<K : Any, V : Any> : PagingSource<K, V>() {
        override suspend fun load(params: LoadParams<K>) =
            LoadResult.Error<K, V>(IllegalStateException("User not authenticated"))
        override fun getRefreshKey(state: PagingState<K, V>) = null
    }

    private val database = FirebaseDatabase.getInstance()
    private val eventsRef = database.getReference("events")

    fun createEvent(event: Event, callback: (Boolean) -> Unit) {
        val eventId = eventsRef.push().key ?: return
        eventsRef.child(eventId).setValue(event.copy(eventId = eventId))
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun getUserEvents(): LiveData<List<Event>> {
        val liveData = MutableLiveData<List<Event>>()
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            return object : LiveData<List<Event>>() {
                private val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val events = snapshot.children.mapNotNull {
                            it.getValue(Event::class.java)
                        }.sortedBy { it.date }
                        value = events
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("EventRepo", "Error: ${error.message}")
                    }
                }

                override fun onActive() {
                    eventsRef.orderByChild("userId").equalTo(userId)
                        .addValueEventListener(listener)
                }

                override fun onInactive() {
                    eventsRef.removeEventListener(listener)
                }
            }

    }

    fun bookMentor(mentorId: String, date: Long, startTime: String, endTime: String) {
//        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
//        val event = Event(
//            userId = userId,
//            title = "Meet Mentor",
//            date = date,
//            startTime = startTime,
//            endTime = endTime,
//            eventType = 1,
//            mentorId = mentorId,
//            status = 0
//        )
//        createEvent(event) { success ->
//            Log.d("EventRepo", if (success) "Event created" else "Failed")
//        }
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