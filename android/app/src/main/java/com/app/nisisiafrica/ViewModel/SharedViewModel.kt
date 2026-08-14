package com.app.nisisiafrica.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.MentorItem
import com.app.nisisiafrica.data.Repository.CoursesRepository
import com.app.nisisiafrica.data.Repository.MentorRepository

class SharedViewModel(application: Application) : AndroidViewModel(application) {
    private val mentorRepository = MentorRepository()
    private val coursesRepository = CoursesRepository(application.applicationContext)

    fun getMentors(): LiveData<PagingData<MentorItem>> {
        return mentorRepository.getMentorsPagingData()
            .cachedIn(viewModelScope)
            .asLiveData()
    }

    fun getCourses(): LiveData<PagingData<CourseItem>> {
        // No cachedIn — age filter from Find My Path must re-apply after questionnaire.
        return coursesRepository.getCoursesPagingData().asLiveData()
    }
}
