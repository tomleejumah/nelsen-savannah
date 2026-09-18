package com.app.nisisiafrica.data.Repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.app.nisisiafrica.data.remote.LmsEventsDataSource
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class EventRepository {
    private val dataSource = FirebaseRemoteDataSource

    private val _events = MutableLiveData<List<Event>>()
    val events: LiveData<List<Event>> = _events

    suspend fun fetchEventss(uid: String) {
        val bearer = currentBearer()
        val hub = LmsEventsDataSource.fetchPublicEvents("upcoming", bearer)
        val announcements = dataSource.fetchUpcomingAnnouncements()
        val merged = (hub + announcements).sortedBy { it.date }
        _events.postValue(merged)
    }

    suspend fun fetchHubEvents(filter: String): List<Event> {
        val bearer = currentBearer()
        return LmsEventsDataSource.fetchPublicEvents(filter, bearer)
    }

    private suspend fun currentBearer(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            "Bearer " + user.getIdToken(false).await().token
        } catch (_: Exception) {
            null
        }
    }
}
