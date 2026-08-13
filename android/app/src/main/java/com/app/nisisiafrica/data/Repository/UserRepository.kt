package com.app.nisisiafrica.data.Repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.app.nisisiafrica.data.Model.Booking
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource

class UserRepository {

    suspend fun getUserBookedDates(userId: String): List<Booking> {
        return FirebaseRemoteDataSource.getBookedDates(userId)
    }

    fun getUserBookedDatesLive(userId: String): LiveData<List<Booking>> =
        liveData {
            val dates = FirebaseRemoteDataSource.getBookedDates(userId)
            emit(dates)
        }
}