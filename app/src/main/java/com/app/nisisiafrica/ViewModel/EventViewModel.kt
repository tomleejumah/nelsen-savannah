package com.app.nisisiafrica.ViewModel

import androidx.lifecycle.ViewModel
import com.app.nisisiafrica.data.Repository.EventRepository

class EventViewModel(private val repository: EventRepository) : ViewModel() {

    fun getUserEvents() = repository.events

//    fun bookMentor(mentorId: String, date: Long, startTime: String, endTime: String) {
//        repository.bookMentor(mentorId, date, startTime, endTime)
//    }
}
