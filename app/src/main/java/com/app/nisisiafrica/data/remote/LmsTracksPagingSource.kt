package com.app.nisisiafrica.data.remote

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

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CourseItem> {
        val lms = loadFromLms()
        if (lms is LoadResult.Page && lms.data.isNotEmpty()) {
            return lms
        }
        return loadFromFirebase()
    }

    private suspend fun loadFromLms(): LoadResult<Int, CourseItem> {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
                ?: return LoadResult.Page(emptyList(), null, null)
            val token = user.getIdToken(false).await().token
                ?: return LoadResult.Page(emptyList(), null, null)
            val response = ApiClient.getLmsService()
                .tracks("Bearer $token")
                .execute()
            val body: LmsModels.TracksEnvelope? = response.body()
            if (!response.isSuccessful || body == null || !body.ok || body.data?.tracks == null) {
                return LoadResult.Page(emptyList(), null, null)
            }
            val items = body.data.tracks.map { card ->
                CourseItem(
                    courseId = card.courseId ?: card.trackId ?: "",
                    tutorId = card.tutorId ?: "",
                    courseImageUrl = card.courseImageUrl ?: "",
                    tutorAvatarUrl = card.tutorAvatarUrl ?: "",
                    tutorName = card.tutorName ?: "",
                    courseTitle = card.courseTitle ?: "",
                    duration = card.duration ?: "",
                    lessons = card.lessons ?: "",
                    courseLink = card.courseLink ?: "",
                    isLiked = card.isLiked,
                )
            }.filter { it.courseId.isNotBlank() }
            LoadResult.Page(items, prevKey = null, nextKey = null)
        } catch (_: Exception) {
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
            LoadResult.Page(items, prevKey = null, nextKey = null)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CourseItem>): Int? = null
}
