package com.app.nisisiafrica.Interfaces

import com.app.nisisiafrica.data.Model.CourseItem
import com.app.nisisiafrica.data.Model.MentorItem
import com.app.nisisiafrica.data.Model.UserData

interface FirebaseCallback {
    fun onUserDataReceived(userData: UserData?){}
    fun onMentorDataFetched(mentors: MentorItem?){}
    fun onMentorsFetched(mentors: MutableList<MentorItem>){}
    fun onMentorsIDFetched(mentorIds: MutableList<String?>?){}
    fun onCoursesFetched(courses: MutableList<CourseItem>){}
    fun onError(e: Exception?){}
}