package com.app.nisisiafrica.data.Repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow

class CoursesRepository {
    fun getCoursesPagingData(): Flow<PagingData<CourseItem>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
            initialLoadSize = 10
        ),
        pagingSourceFactory = {
            FirebaseRemoteDataSource.getCoursesPagingSource(FirebaseDatabase.getInstance().reference)
        }
    ).flow
}