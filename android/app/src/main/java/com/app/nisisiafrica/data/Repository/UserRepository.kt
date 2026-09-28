package com.app.nisisiafrica.data.Repository

import androidx.lifecycle.LiveData
import com.app.nisisiafrica.data.Model.Booking
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.google.firebase.database.ValueEventListener

class UserRepository {

    suspend fun getUserBookedDates(userId: String): List<Booking> {
        return FirebaseRemoteDataSource.getBookedDates(userId)
    }

    fun getUserBookedDatesLive(userId: String): LiveData<List<Booking>> =
        object : LiveData<List<Booking>>() {
            private var listener: ValueEventListener? = null

            override fun onActive() {
                listener = FirebaseRemoteDataSource.observeBookedDates(userId) { postValue(it) }
            }

            override fun onInactive() {
                listener?.let { FirebaseRemoteDataSource.removeBookedDatesListener(userId, it) }
                listener = null
            }
        }
}