package com.app.nisisiafrica.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.Repository.EventRepository
import kotlinx.coroutines.launch

class EventViewModel(private val repository: EventRepository) : ViewModel() {
//    fun getUserEvents() = repository.events

    fun createEvent(event: Event, onComplete: (Boolean) -> Unit) {
//        repository.createEvent(event, onComplete)
    }

    // Java observes this
    val events: LiveData<List<Event>> = repository.events

    // Java calls this to start the "waiting" process
    fun fetchEvents(uid: String) {
        viewModelScope.launch {
            repository.fetchEventss(uid)
        }
    }

//    fun loadMore() = repository.loadMore()

    /*fun bookMentor(mentorId: String, date: Long, startTime: String, endTime: String) {
        repository.bookMentor(mentorId, date, startTime, endTime)
    }
     */
}
