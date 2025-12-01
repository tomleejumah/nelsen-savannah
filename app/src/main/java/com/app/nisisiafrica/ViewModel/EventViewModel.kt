package com.app.nisisiafrica.ViewModel

import androidx.lifecycle.ViewModel
import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.Repository.EventRepository

class EventViewModel(private val repository: EventRepository) : ViewModel() {

    fun getUserEvents() = repository.events

    fun createEvent(event: Event, onComplete: (Boolean) -> Unit) {
        repository.createEvent(event, onComplete)
    }

//    fun bookMentor(mentorId: String, date: Long, startTime: String, endTime: String) {
//        repository.bookMentor(mentorId, date, startTime, endTime)
//    }
}
