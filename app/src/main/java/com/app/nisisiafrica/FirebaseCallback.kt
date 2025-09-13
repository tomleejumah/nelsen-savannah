package com.app.nisisiafrica

import com.app.nisisiafrica.Model.MentorItem
import com.app.nisisiafrica.Model.UserData

interface FirebaseCallback {
    fun onUserDataReceived(userData: UserData?){}
    fun onMentorDataFetched(mentors: MentorItem?){}
    fun onMentorsIDFetched(mentorIds: MutableList<String?>?){}
    fun onError(e: Exception?){}
}