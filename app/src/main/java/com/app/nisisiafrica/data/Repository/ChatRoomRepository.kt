package com.app.nisisiafrica.data.Repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.app.nisisiafrica.data.Model.Chatroom
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import kotlinx.coroutines.flow.Flow

class ChatRoomRepository() {

    fun getChatRooms(): Flow<PagingData<Chatroom>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { FirebaseRemoteDataSource.getChatRoomsPagingSource() }
        ).flow
    }

    fun initAnnouncementChatRoom(){
        FirebaseRemoteDataSource.initSpecialChatRooms()
//        FirebaseRemoteDataSource.initAnnouncementChatRoom()
    }

    fun getPinnedChatRooms(): Flow<List<Chatroom>> =
        FirebaseRemoteDataSource.getPinnedChatRooms()

    fun deleteChatRoom(chatroomId: String, onComplete: (Boolean) -> Unit) =
        FirebaseRemoteDataSource.deleteChatRoom(chatroomId, onComplete)
}