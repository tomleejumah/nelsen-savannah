package com.app.nisisiafrica

import com.app.nisisiafrica.Model.CourseItem
import com.app.nisisiafrica.Model.MentorItem
import com.app.nisisiafrica.Model.UserData

interface FirebaseCallback {
    fun onUserDataReceived(userData: UserData?){}
    fun onMentorDataFetched(mentors: MentorItem?){}
    fun onMentorsFetched(mentors: MutableList<MentorItem>){}
    fun onMentorsIDFetched(mentorIds: MutableList<String?>?){}
    fun onCoursesFetched(courses: MutableList<CourseItem>){}
    fun onError(e: Exception?){}
}