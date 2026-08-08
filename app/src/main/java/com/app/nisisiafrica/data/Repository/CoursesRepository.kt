package com.app.nisisiafrica.data.Repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.remote.LmsTracksPagingSource
import kotlinx.coroutines.flow.Flow

class CoursesRepository {
    /** Top courses / ViewAll — LMS catalog via GET /lms/tracks (CourseItem-compatible). */
    fun getCoursesPagingData(): Flow<PagingData<CourseItem>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
        pagingSourceFactory = { LmsTracksPagingSource() }
    ).flow
}
