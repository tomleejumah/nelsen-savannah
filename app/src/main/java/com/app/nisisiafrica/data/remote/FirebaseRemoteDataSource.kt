package com.app.nisisiafrica.data.remote

import android.content.Context
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.app.nisisiafrica.Constants
import com.app.nisisiafrica.Interfaces.FirebaseCallback
import com.app.nisisiafrica.Utils.Util
import com.app.nisisiafrica.data.Model.Announcement
import com.app.nisisiafrica.data.Model.Booking
import com.app.nisisiafrica.data.Model.ChatMessage
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
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object FirebaseRemoteDataSource {
    private const val TAG = "FirebaseUserHelper"
    private val db = FirebaseDatabase.getInstance().getReference()

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
            "Bio" to userData.bio
        )

//        usersRef.child(FirebaseAuth.getInstance().currentUser?.uid.toString())
        usersRef.child(Util.getState(Constants.CURRENT_USER_ID, ""))
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

        val rolesRef =db
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
        val datesRef = db.child("bookedDates").child(userId)

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
//                cont.resumeWithException(error.toException())
                Log.e("FirebaseRemoteDS", "Database Error: ${error.message}")
                cont.resume(emptyList())
            }
        })
    }


    fun getRemoteUserData(
        userId: String,
        onSuccess: ((UserData?) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val userRef = db.child("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.d("Firebase", "User not found: $userId")
                    onSuccess?.invoke(null)
                    return
                }

                // User data found - create UserData object
                val userData = UserData(
                    id = userId,
                    email = snapshot.child("email").getValue(String::class.java) ?: "",
                    displayName = snapshot.child("displayName").getValue(String::class.java) ?: "",
                    firstName = snapshot.child("firstName").getValue(String::class.java) ?: "",
                    lastName = snapshot.child("lastName").getValue(String::class.java) ?: "",
                    photoUrl = snapshot.child("photoUrl").getValue(String::class.java) ?: "",
                    bio = snapshot.child("Bio").getValue(String::class.java) ?: "",
                    lastLogin = snapshot.child("lastLogin").getValue(Long::class.java) ?: 0
                )

                // Get user role
                getOrAssignUserRole(
                    firebaseUserId = userId,
                    onSuccess = { role ->
                        // Role fetched successfully
                        userData.userRole = role
                        Log.d("ROLE", "User role is $role")
                        onSuccess?.invoke(userData)
                    },
                    onError = { exception ->
                        // Role fetch failed, but still return userData with default role
                        Log.e("ROLE", "Error getting role: ${exception.message}")
                        userData.userRole = "Mentee"
                        onSuccess?.invoke(userData)
                    }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error getting user data", error.toException())
                onError?.invoke(error.toException())
            }
        })
    }

    fun getMentorData(
        userID: String,
        onSuccess: ((MentorItem?) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        if (userID.isEmpty()) {
            onSuccess?.invoke(null)
            return
        }
        val mentorRef = db.child("mentors").child(userID)
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
                    onSuccess?.invoke(mentorData)
                } else {
                    onSuccess?.invoke(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onError?.invoke(error.toException())
                Log.e("Firebase", "Error getting user data", error.toException())
            }
        })
    }

    fun fetchCoursesByMentorId(
        mentorId: String,
        firebaseCallback: FirebaseCallback
    ) {
        val coursesRef = db.child("courses").orderByChild("tutorId").equalTo(mentorId)
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
        val coursesRef = db.child("courses")
        var courseId = courseData.courseId
//            ?: coursesRef.push().key
        if (courseId.isEmpty()){
            courseId = coursesRef.push().key.toString()
        }

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
        val mentorsRef = db.child("mentors")
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
//        val dbRef = FirebaseDatabase.getInstance().reference
        val coursesRef = db.child("courses")

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
//        val dbRef = FirebaseDatabase.getInstance().reference
        val mentorsRef = db.child("mentors")
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

    //chats
    fun getPinnedChatRooms(): Flow<List<Chatroom>> = callbackFlow {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@callbackFlow

        val pinnedIds = listOf("announcements", "ai_assistant_$userId")

        val listener = db.collection("chatRooms")
            .whereIn(FieldPath.documentId(), pinnedIds)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                val rooms = snapshot?.toObjects(Chatroom::class.java) ?: emptyList()

                // Sort: Ensure Announcements (system) is always above AI
                val sorted = rooms.sortedByDescending { it.type == "system" }
                trySend(sorted)
            }
        awaitClose { listener.remove() }
    }
    fun initSpecialChatRooms() {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val announcementsRef = db.collection("chatRooms").document("announcements")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(announcementsRef)

            if (!snapshot.exists()) {
                transaction.set(
                    announcementsRef,
                    hashMapOf(
                        "chatroomId" to "announcements",
                        "userIds" to listOf(userId),
                        "lastMessage" to "Welcome to announcements",
                        "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                        "type" to "system"
                    )
                )
            } else {
                transaction.update(
                    announcementsRef,
                    "userIds",
                    FieldValue.arrayUnion(userId)
                )
            }
        }

        val aiChatId = "ai_assistant_$userId"
        db.collection("chatRooms").document(aiChatId)
            .set(
                hashMapOf(
                    "chatroomId" to aiChatId,
                    "userIds" to listOf(userId),
                    "lastMessage" to "How can I help you today?",
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "type" to "ai"
                ),
                SetOptions.merge()
            )
    }

    fun createOrGetDirectChatRoom(
        otherUserId: String,
        otherUserName: String,
        currentUserName: String,
        onComplete: (String?) -> Unit
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onComplete(null)
            return
        }

        // Create deterministic chatroom ID (always same regardless of who initiates)
        val chatroomId = if (currentUserId < otherUserId) {
            "${currentUserId}_${otherUserId}"
        } else {
            "${otherUserId}_${currentUserId}"
        }

        val db = FirebaseFirestore.getInstance()

        // Check if chatroom exists first
        db.collection("chatRooms").document(chatroomId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Chatroom already exists
                    Log.d("ChatRoom", "Chatroom already exists: $chatroomId")
                    onComplete(chatroomId)
                } else {
                    // Create new chatroom
                    val chatroom = hashMapOf(
                        "chatroomId" to chatroomId,
                        "userIds" to listOf(currentUserId, otherUserId),
                        "userNames" to mapOf(
                            currentUserId to currentUserName,
                            otherUserId to otherUserName
                        ),
                        "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                        "lastMessage" to "No Messages Yet",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "type" to "direct"
                    )

                    db.collection("chatRooms").document(chatroomId)
                        .set(chatroom)
                        .addOnSuccessListener {
                            Log.d("ChatRoom", "Chatroom created: $chatroomId")
                            onComplete(chatroomId)
                        }
                        .addOnFailureListener { e ->
                            Log.e("ChatRoom", "Error creating chatroom", e)
                            onComplete(null)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatRoom", "Error checking chatroom", e)
                onComplete(null)
            }
    }

    // In ChatRepository
    fun getChatRoomsPagingSource(): PagingSource<QuerySnapshot, Chatroom> {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUserId = auth.currentUser?.uid ?: return InvalidPagingSource()

        val query = firestore.collection("chatRooms")
            .whereEqualTo("type", "direct")
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

                    // Filter out announcements
                    val chatrooms = snapshot.toObjects(Chatroom::class.java)
//                        .filter { it.chatroomId != "announcements" }

                    Log.d("ChatRooms", "Loaded ${chatrooms.size} chatrooms")

                    LoadResult.Page(
                        data = chatrooms,
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

    fun getMessagesRealtime(chatroomId: String, onMessagesChanged: (List<ChatMessage>) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("chatRooms").document(chatroomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                snapshot?.toObjects(ChatMessage::class.java)?.let { onMessagesChanged(it) }
            }
    }

    fun sendMessage(chatroomId: String, message: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val user = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)

        val messageId = db.collection("chatRooms").document(chatroomId)
            .collection("messages").document().id

        val chatMessage = hashMapOf(
            "messageId" to messageId,
            "senderId" to user.uid,
            "senderName" to (user.displayName ?: "User"),
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to "text"
        )

        db.collection("chatRooms").document(chatroomId)
            .collection("messages").document(messageId)
            .set(chatMessage)
            .addOnSuccessListener {
                db.collection("chatRooms").document(chatroomId).update(
                    "lastMessage", message,
                    "lastMessageTimestamp", FieldValue.serverTimestamp()
                )
                onComplete(true)
            }
            .addOnFailureListener { onComplete(false) }
    }
    // Add this to FirebaseRemoteDataSource
    fun getAnnouncementChatroom(onComplete: (Chatroom?) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("chatRooms")
            .document("announcements")
            .addSnapshotListener { doc, e ->
                if (e != null) {
                    onComplete(null)
                    return@addSnapshotListener
                }
                onComplete(doc?.toObject(Chatroom::class.java))
            }
    }

    private val eventsRef = db.child("Events")

    suspend fun getNext3Items(uid: String): List<Event> {
        val now = System.currentTimeMillis()

        try {
            // 1. Fetch user's events (from now onwards)
            val userEventIds = db.child("UserEvents/$uid")
                .orderByValue()  // Order by timestamp
                .startAt(now.toDouble())  // From now onwards
                .limitToFirst(5)  // Get a few extra for filtering
                .get()
                .await()
                .children
                .mapNotNull { it.key }

            val events = userEventIds.mapNotNull { eventId ->
                eventsRef.child(eventId)
                    .get()
                    .await()
                    .getValue(Event::class.java)
            }

            // 2. Fetch announcements (from now onwards)
            val announcements = db.child("Announcements")
                .orderByChild("date")
                .startAt(now.toDouble())
                .limitToFirst(5)
                .get()
                .await()
                .children
                .mapNotNull { it.getValue(Announcement::class.java) }

            // 3. Merge both lists
            val allItems = mutableListOf<Event>()

            events.forEach { event ->
                allItems.add(Event(
                    eventId = event.eventId,
                    //todo pass this in on create event
                    title = event.getTitleForUser(uid),
                    date = event.date,
                    startTime = event.startTime,
                    eventType = "event",
                    endTime = event.endTime,
                ))
            }

            announcements.forEach { announcement ->
                allItems.add(Event(
                    eventId = announcement.id,
                    title = announcement.title,
                    date = announcement.date,
                    eventType = "announcement"
                ))
            }

            // 4. Sort by date (earliest first) and take next 3
            return allItems
                .filter { it.date >= now }  // Ensure all are future
                .sortedBy { it.date }
                .take(3)

        } catch (e: Exception) {
            Log.e("RemoteDataSource", "Error fetching next items", e)
            return emptyList()
        }
    }

    fun createEvent(
        event: Event,
        mentorId: String,
        menteeId: String,
        onComplete: (Boolean) -> Unit
    ) {
        val eventId = eventsRef.push().key ?: return onComplete(false)
        val finalEvent = event.copy(eventId = eventId)

        // Atomic multi-path update
        val updates = hashMapOf<String, Any>(
            "/Events/$eventId" to finalEvent.toMap(),
            "/UserEvents/$mentorId/$eventId" to finalEvent.date,
            "/UserEvents/$menteeId/$eventId" to finalEvent.date
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("FirebaseDataSource", "Event created successfully")
                onComplete(true)
            }
            .addOnFailureListener { error ->
                Log.e("FirebaseDataSource", "Event creation failed", error)
                onComplete(false)
            }
    }

    fun updateEvent(eventId: String, updates: Map<String, Any>, onComplete: (Boolean) -> Unit) {
        // Only update in ONE place - Events node
        eventsRef.child(eventId)
            .updateChildren(updates)
            .addOnCompleteListener { onComplete(it.isSuccessful) }

        // UserEvents index doesn't need updating
        // (unless date changes)
    }

    // If date changes, update index too
    fun rescheduleEvent(eventId: String, newDate: Long, mentorId: String, menteeId: String) {
        val updates = hashMapOf<String, Any>(
            "/Events/$eventId/date" to newDate,
            "/UserEvents/$mentorId/$eventId" to newDate,
            "/UserEvents/$menteeId/$eventId" to newDate
        )

        db.updateChildren(updates)
    }

    fun deleteEvent(event: Event, onComplete: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any?>(
            "/Events/${event.eventId}" to null,
            "/UserEvents/${event.mentorId}/${event.eventId}" to null,
            "/UserEvents/${event.menteeId}/${event.eventId}" to null
        )

        db.updateChildren(updates)
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun Event.toMap(): Map<String, Any?> {
        return mapOf(
            "eventId" to eventId,
            "title" to title,
            "date" to date,
            "startTime" to startTime,
            "endTime" to endTime,
            "eventType" to eventType,
            "mentorId" to mentorId,
            "menteeId" to menteeId,
            "mentorName" to mentorName,
            "menteeName" to menteeName,
            "status" to status,
            "description" to description
        )
    }

    fun Event.getTitleForUser(uid: String): String {
        return when {
            mentorId == uid -> "Session with $menteeName"
            menteeId == uid -> "Session with $mentorName"
            else -> title  // Fallback for announcements
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
//        val dbRef = FirebaseDatabase.getInstance().reference
        val userRef = db.child("users").child(id)
        userRef.child("Bio").setValue(description)
    }
}