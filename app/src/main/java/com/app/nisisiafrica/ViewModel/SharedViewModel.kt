package com.app.nisisiafrica.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.MentorItem
import com.app.nisisiafrica.data.Repository.CoursesRepository
import com.app.nisisiafrica.data.Repository.MentorRepository

class SharedViewModel : ViewModel() {
    private val mentorRepository = MentorRepository()
    private val coursesRepository = CoursesRepository()

    fun getMentors(): LiveData<PagingData<MentorItem>> {
        return mentorRepository.getMentorsPagingData()
            .cachedIn(viewModelScope)
            .asLiveData()
    }

    fun getCourses(): LiveData<PagingData<CourseItem>> {
        return coursesRepository.getCoursesPagingData()
            .cachedIn(viewModelScope)
            .asLiveData()
    }
}