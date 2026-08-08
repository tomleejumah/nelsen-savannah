package com.app.nisisiafrica.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.LmsModels
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Loads CourseItem rows from GET /lms/tracks (identical catalog JSON as the website).
 * Auth/API failures surface as [LoadResult.Error] so Home can show feedback instead of a silent empty rail.
 */
class LmsTracksPagingSource : PagingSource<Int, CourseItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CourseItem> {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
                ?: return LoadResult.Error(IOException("Sign in required to load courses"))
            val token = user.getIdToken(false).await().token
                ?: return LoadResult.Error(IOException("Missing auth token for courses"))
            val response = ApiClient.getLmsService()
                .tracks("Bearer $token")
                .execute()
            val body: LmsModels.TracksEnvelope? = response.body()
            if (!response.isSuccessful || body == null || !body.ok || body.data?.tracks == null) {
                val msg = body?.error ?: ("Courses request failed (" + response.code() + ")")
                return LoadResult.Error(IOException(msg))
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
            }
            LoadResult.Page(items, prevKey = null, nextKey = null)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CourseItem>): Int? = null
}
