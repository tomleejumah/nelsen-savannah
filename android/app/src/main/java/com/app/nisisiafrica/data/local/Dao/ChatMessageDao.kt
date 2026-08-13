package com.app.nisisiafrica.data.local.Dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.nisisiafrica.data.Model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM messages WHERE chatroomId = :roomId ORDER BY timestamp ASC")
    fun getMessages(roomId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ChatMessageEntity)

    @Query("UPDATE messages SET status = :status WHERE messageId = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM messages WHERE status = 'failed' AND chatroomId = :roomId")
    suspend fun getFailedMessages(roomId: String): List<ChatMessageEntity>

    @Query("DELETE FROM messages WHERE messageId = :id")
    suspend fun deleteById(id: String)

    /** Turns a row into a tombstone, clearing the body but keeping the row so replies still resolve. */
    @Query("UPDATE messages SET deleted = 1, message = '', type = 'text' WHERE messageId = :id")
    suspend fun markDeleted(id: String)
}
