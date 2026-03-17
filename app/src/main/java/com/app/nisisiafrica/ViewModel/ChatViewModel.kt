package com.app.nisisiafrica.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.nisisiafrica.data.Model.ChatMessageEntity
import com.app.nisisiafrica.data.Repository.ChatRepository
import kotlinx.coroutines.launch

class ChatViewModel(private val repo: ChatRepository) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessageEntity>>()
    val messages: LiveData<List<ChatMessageEntity>> = _messages

    fun loadMessages(chatroomId: String) {
        // 1. Observe Room immediately
        viewModelScope.launch {
            repo.getMessages(chatroomId).collect { _messages.postValue(it) }
        }
        // 2. Sync from Firestore in background
        repo.syncMessages(chatroomId)
    }

    fun sendMessage(chatroomId: String, message: String, onComplete: (Boolean) -> Unit) {
        repo.sendMessage(chatroomId, message, onComplete)
    }

    class Factory(private val repo: ChatRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repo) as T
        }
    }
}
