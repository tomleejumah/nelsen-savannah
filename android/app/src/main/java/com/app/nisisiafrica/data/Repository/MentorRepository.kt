package com.app.nisisiafrica.data.Repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.app.nisisiafrica.data.Model.MentorItem
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow


class MentorRepository {
    fun getMentorsPagingData(): Flow<PagingData<MentorItem>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
            initialLoadSize = 10
        ),
        pagingSourceFactory = {
            FirebaseRemoteDataSource.getMentorsPagingSource(FirebaseDatabase.getInstance().reference)
        }
    ).flow
}
