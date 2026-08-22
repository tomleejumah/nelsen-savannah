package com.app.nisisiafrica.data.Repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.app.nisisiafrica.data.remote.LmsEventsDataSource

class EventRepository {
    private val dataSource = FirebaseRemoteDataSource

    private val _events = MutableLiveData<List<Event>>()
    val events: LiveData<List<Event>> = _events

    suspend fun fetchEventss(uid: String) {
        val hub = LmsEventsDataSource.fetchPublicEvents()
        val announcements = dataSource.fetchUpcomingAnnouncements()
        val merged = (hub + announcements)
            .sortedBy { it.date }
        _events.postValue(merged)
    }
}
