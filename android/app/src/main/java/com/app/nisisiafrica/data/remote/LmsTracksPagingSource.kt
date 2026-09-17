package com.app.nisisiafrica.data.remote

import android.content.Context
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.app.nisisiafrica.Utils.PathAgeFilter
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.LmsModels
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Personalized course feed from GET /lms/enrollments/me (LMS DB via API).
 * Full catalog: Enroll Schools → GET /lms/tracks.
 */
class LmsTracksPagingSource(
    private val appContext: Context? = null,
) : PagingSource<Int, CourseItem>() {

    companion object {
        private const val TAG = "LmsTracks"
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CourseItem> {
        val lms = loadFromLms()
        if (lms is LoadResult.Page) {
            Log.i(TAG, "LMS has ${lms.data.size} enrolled tracks")
            return lms
        }
        return lms
    }

    private fun ageFilter(items: List<CourseItem>): List<CourseItem> {
        val ctx = appContext ?: return items
        if (!PathAgeFilter.isActive(ctx)) return items
        val filtered = items.filter { PathAgeFilter.matches(ctx, it.programSlug) }
        Log.i(TAG, "age filter ${items.size} → ${filtered.size}")
        return if (filtered.isEmpty()) items else filtered
    }

    private suspend fun loadFromLms(): LoadResult<Int, CourseItem> = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                Log.w(TAG, "No Firebase user — skip LMS")
                return@withContext LoadResult.Page(emptyList(), null, null)
            }
            val token = user.getIdToken(false).await().token
            if (token.isNullOrBlank()) {
                Log.w(TAG, "Empty ID token — skip LMS")
                return@withContext LoadResult.Page(emptyList(), null, null)
            }
            val response = ApiClient.getLmsService()
                .myEnrollments("Bearer $token")
                .execute()
            if (!response.isSuccessful) {
                val err = response.errorBody()?.string()?.take(240)
                Log.w(TAG, "LMS HTTP ${response.code()} $err")
                return@withContext LoadResult.Page(emptyList(), null, null)
            }
            val body: LmsModels.EnrollmentListEnvelope? = response.body()
            if (body == null) {
                Log.w(TAG, "LMS body null after Gson (check duration/lessons types)")
                return@withContext LoadResult.Page(emptyList(), null, null)
            }
            if (!body.ok || body.data?.enrollments == null) {
                Log.w(TAG, "LMS ok=${body.ok} error=${body.error}")
                return@withContext LoadResult.Page(emptyList(), null, null)
            }
            Log.i(TAG, "LMS ok source=${body.source} enrollmentCount=${body.data.enrollments.size}")
            val items = body.data.enrollments.map { enrollment ->
                CourseItem(
                    courseId = enrollment.trackId ?: "",
                    tutorId = enrollment.tutorId ?: enrollment.mentorId ?: "",
                    courseImageUrl = enrollment.courseImageUrl ?: "",
                    tutorAvatarUrl = enrollment.tutorAvatarUrl ?: "",
                    tutorName = enrollment.tutorName ?: "",
                    courseTitle = enrollment.courseTitle ?: enrollment.trackId ?: "",
                    duration = enrollment.duration ?: "",
                    lessons = "${enrollment.lessonsCompleted}/${enrollment.lessonsTotal} lessons",
                    courseLink = "",
                    isLiked = false,
                    programSlug = enrollment.programSlug ?: "",
                )
            }.filter { it.courseId.isNotBlank() }
            LoadResult.Page(ageFilter(items), prevKey = null, nextKey = null)
        } catch (e: Exception) {
            Log.e(TAG, "LMS exception", e)
            LoadResult.Page(emptyList(), null, null)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CourseItem>): Int? = null
}
