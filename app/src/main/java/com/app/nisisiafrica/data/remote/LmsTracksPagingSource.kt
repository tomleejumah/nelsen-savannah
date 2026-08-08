package com.app.nisisiafrica.data.remote

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.LmsModels
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Prefer LMS GET /lms/tracks; if empty or unreachable, list Firebase RTDB `courses/`.
 * Mentors stay on Firebase via [FirebaseRemoteDataSource.getMentorsPagingSource].
 */
class LmsTracksPagingSource : PagingSource<Int, CourseItem>() {

    companion object {
        private const val TAG = "LmsTracks"
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CourseItem> {
        val lms = loadFromLms()
        if (lms is LoadResult.Page && lms.data.isNotEmpty()) {
            Log.i(TAG, "LMS has ${lms.data.size} tracks — using API (not Firebase)")
            return lms
        }
        if (lms is LoadResult.Page) {
            Log.w(TAG, "LMS empty (0 tracks) — falling back to Firebase courses/")
        } else if (lms is LoadResult.Error) {
            Log.w(TAG, "LMS error: ${lms.throwable.message} — falling back to Firebase courses/")
        }
        return loadFromFirebase()
    }

    private suspend fun loadFromLms(): LoadResult<Int, CourseItem> {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Log.w(TAG, "No Firebase user — skip LMS")
                return LoadResult.Page(emptyList(), null, null)
            }
            val token = user.getIdToken(false).await().token
            if (token.isNullOrBlank()) {
                Log.w(TAG, "Empty ID token — skip LMS")
                return LoadResult.Page(emptyList(), null, null)
            }
            val response = ApiClient.getLmsService()
                .tracks("Bearer $token")
                .execute()
            if (!response.isSuccessful) {
                val err = response.errorBody()?.string()?.take(240)
                Log.w(TAG, "LMS HTTP ${response.code()} $err")
                return LoadResult.Page(emptyList(), null, null)
            }
            val body: LmsModels.TracksEnvelope? = response.body()
            if (body == null) {
                Log.w(TAG, "LMS body null after Gson (check duration/lessons types)")
                return LoadResult.Page(emptyList(), null, null)
            }
            if (!body.ok || body.data?.tracks == null) {
                Log.w(TAG, "LMS ok=${body.ok} error=${body.error}")
                return LoadResult.Page(emptyList(), null, null)
            }
            Log.i(TAG, "LMS ok source=${body.source} trackCount=${body.data.tracks.size}")
            val items = body.data.tracks.map { card ->
                CourseItem(
                    courseId = card.courseId ?: card.trackId ?: "",
                    tutorId = card.tutorId ?: "",
                    courseImageUrl = card.courseImageUrl ?: "",
                    tutorAvatarUrl = card.tutorAvatarUrl ?: "",
                    tutorName = card.tutorName ?: "",
                    courseTitle = card.courseTitle ?: "",
                    duration = card.durationString(),
                    lessons = card.lessonsString(),
                    courseLink = card.courseLink ?: "",
                    isLiked = card.isLiked,
                )
            }.filter { it.courseId.isNotBlank() }
            LoadResult.Page(items, prevKey = null, nextKey = null)
        } catch (e: Exception) {
            Log.e(TAG, "LMS exception", e)
            LoadResult.Page(emptyList(), null, null)
        }
    }

    private suspend fun loadFromFirebase(): LoadResult<Int, CourseItem> {
        return try {
            val snapshot = FirebaseDatabase.getInstance().reference
                .child("courses")
                .orderByKey()
                .get()
                .await()
            val items = snapshot.children.mapNotNull { courseSnapshot ->
                val id = courseSnapshot.child("courseId").getValue(String::class.java)
                    ?: courseSnapshot.key
                    ?: return@mapNotNull null
                if (id.isBlank()) return@mapNotNull null
                CourseItem(
                    courseId = id,
                    tutorId = courseSnapshot.child("tutorId").getValue(String::class.java) ?: "",
                    courseImageUrl = courseSnapshot.child("courseImageUrl").getValue(String::class.java) ?: "",
                    tutorAvatarUrl = courseSnapshot.child("mentorImageUrl").getValue(String::class.java) ?: "",
                    tutorName = courseSnapshot.child("mentorName").getValue(String::class.java) ?: "",
                    courseTitle = courseSnapshot.child("courseTitle").getValue(String::class.java) ?: "",
                    duration = courseSnapshot.child("courseDuration").getValue(String::class.java) ?: "",
                    lessons = courseSnapshot.child("courseLessons").getValue(String::class.java) ?: "",
                    courseLink = courseSnapshot.child("courseLink").getValue(String::class.java) ?: "",
                    isLiked = courseSnapshot.child("isLiked").getValue(Boolean::class.java) ?: false,
                )
            }
            Log.i(TAG, "Firebase courses/ count=${items.size}")
            LoadResult.Page(items, prevKey = null, nextKey = null)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase courses failed", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CourseItem>): Int? = null
}
