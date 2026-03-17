package com.app.nisisiafrica.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.nisisiafrica.data.Model.Chatroom
import com.app.nisisiafrica.data.Repository.ChatRepository

class ChatViewModel(private val chatRepository: ChatRepository): ViewModel() {

    val chatRooms: LiveData<PagingData<Chatroom>> = chatRepository.getChatRooms()
        .cachedIn(viewModelScope)
        .asLiveData()

    fun initPinnedChats() {
        chatRepository.initAnnouncementChatRoom()
    }

    val pinnedChatRooms: LiveData<List<Chatroom>> =
        chatRepository.getPinnedChatRooms().asLiveData()
}