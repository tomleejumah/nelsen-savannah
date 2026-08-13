package com.app.nisisiafrica.data.Repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.facebook.appevents.ml.Utils

class EventRepository {
    private val dataSource = FirebaseRemoteDataSource



    private val _events = MutableLiveData<List<Event>>()
    val events: LiveData<List<Event>> = _events

    suspend fun fetchEventss(uid: String) {
        val result = dataSource.getNext3Items(uid)
        _events.postValue(result)
    }


    // This function "waits" for the data source before posting the result
    suspend fun loadMoreEvents(pageSize: Int) {
//        val result = dataSource.getUserEvents(pageSize)
//        _events.postValue(result)
    }
//    val events: LiveData<List<Event>> = dataSource.getUserEvents()
//    fun loadMore() = dataSource.loadNextPage()
//    fun createEvent(event: Event, onComplete: (Boolean) -> Unit) =
//        dataSource.createEvent(event, onComplete)

  /*  private val eventsRef = FirebaseDatabase.getInstance().getReference("events")

    fun updateEventStatuses(userId: String) {
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86400000 // +24 hours

        eventsRef.orderByChild("userId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val event = child.getValue(Event::class.java) ?: return@forEach
                        val newStatus = when {
                            event.date < todayStart -> 1 // completed/past
                            event.date in todayStart..todayEnd -> 2 // today
                            else -> 0 // upcoming
                        }
                        if (event.status != newStatus) {
                            child.ref.child("status").setValue(newStatus)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
   */
}