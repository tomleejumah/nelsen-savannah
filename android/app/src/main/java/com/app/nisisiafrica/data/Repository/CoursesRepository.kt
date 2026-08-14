package com.app.nisisiafrica.data.Repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.remote.LmsTracksPagingSource
import kotlinx.coroutines.flow.Flow

class CoursesRepository(private val appContext: Context? = null) {
    /** Top courses: LMS tracks first, Firebase `courses/` if LMS empty/unreachable. */
    fun getCoursesPagingData(): Flow<PagingData<CourseItem>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        pagingSourceFactory = { LmsTracksPagingSource(appContext) }
    ).flow
}
