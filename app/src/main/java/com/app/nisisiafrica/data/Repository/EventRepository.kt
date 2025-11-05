package com.app.nisisiafrica.data.Repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource

class EventRepository {

    val events: LiveData<List<Event>> = FirebaseRemoteDataSource.getUserEvents()

}