package com.app.nisisiafrica.data.Repository

import androidx.paging.PagingData
import com.app.nisisiafrica.data.Model.Chatroom
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRoomRepository() {

    fun getChatRooms(): Flow<PagingData<Chatroom>> {
        // Live snapshot listener, already sorted newest-first. Wrapped as PagingData
        // so the existing PagingDataAdapter keeps working unchanged.
        return FirebaseRemoteDataSource.getChatRoomsFlow().map { PagingData.from(it) }
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