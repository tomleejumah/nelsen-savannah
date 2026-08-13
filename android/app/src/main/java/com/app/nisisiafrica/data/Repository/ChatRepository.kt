package com.app.nisisiafrica.data.Repository

import android.util.Log
import com.app.nisisiafrica.DataBase.AppDatabase
import com.app.nisisiafrica.data.Model.ChatMessage
import com.app.nisisiafrica.data.Model.ChatMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatRepository(private val appDatabase: AppDatabase) {
    private val dao = appDatabase.chatMessageDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getMessages(chatroomId: String): Flow<List<ChatMessageEntity>> =
        dao.getMessages(chatroomId)

    @JvmOverloads
    fun sendMessage(
        chatroomId: String,
        message: String,
        receiverId: String?,
        replyTo: ChatMessageEntity? = null,
        onComplete: (Boolean) -> Unit
    ) {
        writeMessage(chatroomId, message, "text", message, receiverId, replyTo, onComplete)
    }

    @JvmOverloads
    fun sendImageMessage(
        chatroomId: String,
        imageUrl: String,
        receiverId: String?,
        replyTo: ChatMessageEntity? = null,
        onComplete: (Boolean) -> Unit
    ) {
        writeMessage(chatroomId, imageUrl, "image", "\uD83D\uDCF7 Photo", receiverId, replyTo, onComplete)
    }

    /**
     * Sends a non-image attachment (document or audio). [type] is "file" or "audio";
     * the URL is stored as the message body and opened on tap.
     */
    @JvmOverloads
    fun sendMediaMessage(
        chatroomId: String,
        url: String,
        type: String,
        receiverId: String?,
        replyTo: ChatMessageEntity? = null,
        onComplete: (Boolean) -> Unit
    ) {
        val preview = if (type == "audio") "\uD83C\uDFB5 Audio" else "\uD83D\uDCC4 Document"
        writeMessage(chatroomId, url, type, preview, receiverId, replyTo, onComplete)
    }

    /**
     * Deletes a message as a tombstone: the body is cleared on Firestore and
     * locally, but the document survives so quoted replies pointing at it still
     * render and the other participant sees "This message was deleted" rather
     * than the message silently vanishing.
     */
    fun deleteMessage(
        chatroomId: String,
        message: ChatMessageEntity,
        onComplete: (Boolean) -> Unit
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || message.senderId != uid) return onComplete(false)

        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("chatRooms").document(chatroomId)
            .collection("messages").document(message.messageId)

        // Optimistic local tombstone; reverted if Firestore rejects the write
        // (typically missing `allow update` on messages in security rules).
        scope.launch { dao.markDeleted(message.messageId) }

        val payload = hashMapOf<String, Any>(
            "deleted" to true,
            "message" to "",
            "type" to "text"
        )
        ref.update(payload)
            .addOnSuccessListener {
                refreshRoomPreviewAfterDelete(chatroomId, message.messageId)
                onComplete(true)
            }
            .addOnFailureListener { updateErr ->
                // Fallback: merge-set in case update is blocked but write isn't.
                ref.set(payload, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        refreshRoomPreviewAfterDelete(chatroomId, message.messageId)
                        onComplete(true)
                    }
                    .addOnFailureListener { setErr ->
                        android.util.Log.e(
                            "ChatRepository",
                            "delete failed update=${updateErr.message} set=${setErr.message}"
                        )
                        scope.launch { dao.insert(message) } // restore
                        onComplete(false)
                    }
            }
    }

    /**
     * If the deleted message was the room's most recent one, recompute the
     * preview from the newest surviving (non-deleted) message.
     */
    private fun refreshRoomPreviewAfterDelete(chatroomId: String, deletedId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("chatRooms").document(chatroomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(8)
            .get()
            .addOnSuccessListener { snap ->
                val newestAlive = snap.documents.firstOrNull { doc ->
                    doc.getBoolean("deleted") != true
                }
                val preview = when {
                    newestAlive == null -> DELETED_PLACEHOLDER
                    else -> {
                        val type = newestAlive.getString("type") ?: "text"
                        val body = newestAlive.getString("message").orEmpty()
                        when (type) {
                            "image" -> "\uD83D\uDCF7 Photo"
                            "audio" -> "\uD83C\uDFB5 Audio"
                            "file" -> "\uD83D\uDCC4 Document"
                            else -> body.ifBlank { DELETED_PLACEHOLDER }
                        }
                    }
                }
                // Only rewrite the room preview when the deleted msg was (or is) top.
                val top = snap.documents.firstOrNull()
                if (top == null || top.id == deletedId || newestAlive == null) {
                    db.collection("chatRooms").document(chatroomId)
                        .update("lastMessage", preview)
                }
            }
    }

    /** One-or-two-line quote text for [message], avoiding raw URLs for attachments. */
    private fun quoteSnippet(message: ChatMessageEntity): String = when {
        message.deleted -> DELETED_PLACEHOLDER
        message.type == "image" -> "\uD83D\uDCF7 Photo"
        message.type == "audio" -> "\uD83C\uDFB5 Audio"
        message.type == "file" -> "\uD83D\uDCC4 Document"
        else -> message.message.take(SNIPPET_MAX_CHARS)
    }

    /** Resets the current user's unread badge for a room (called when it's opened). */
    fun markRoomRead(chatroomId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("chatRooms").document(chatroomId)
            .update("unreadCount.$uid", 0)
    }

    private fun writeMessage(
        chatroomId: String,
        content: String,
        type: String,
        lastPreview: String,
        receiverId: String?,
        replyTo: ChatMessageEntity?,
        onComplete: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val user = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)

        val messageId = db.collection("chatRooms").document(chatroomId)
            .collection("messages").document().id

        val replyId = replyTo?.messageId.orEmpty()
        val replySender = replyTo?.senderName.orEmpty()
        val replySnippet = replyTo?.let { quoteSnippet(it) }.orEmpty()

        val entity = ChatMessageEntity(
            messageId = messageId,
            chatroomId = chatroomId,
            senderId = user.uid,
            senderName = user.displayName ?: "User",
            message = content,
            timestamp = System.currentTimeMillis(),
            type = type,
            status = "sending",
            replyToId = replyId,
            replyToSender = replySender,
            replyToSnippet = replySnippet
        )

        scope.launch { dao.insert(entity) }

        val chatMessage = hashMapOf(
            "messageId" to messageId,
            "senderId" to user.uid,
            "senderName" to (user.displayName ?: "User"),
            "message" to content,
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to type,
            "replyToId" to replyId,
            "replyToSender" to replySender,
            "replyToSnippet" to replySnippet,
            "deleted" to false
        )

        db.collection("chatRooms").document(chatroomId)
            .collection("messages").document(messageId)
            .set(chatMessage)
            .addOnSuccessListener {
                scope.launch { dao.updateStatus(messageId, "sent") }
                val roomUpdates = hashMapOf<String, Any>(
                    "lastMessage" to lastPreview,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "lastMessageSenderId" to user.uid
                )
                if (!receiverId.isNullOrEmpty()) {
                    roomUpdates["unreadCount.$receiverId"] = FieldValue.increment(1)
                }
                db.collection("chatRooms").document(chatroomId).update(roomUpdates)
                if (chatroomId.startsWith("ai_assistant_") && type == "text") {
                    sendToGemini(chatroomId, content)
                }
                onComplete(true)
            }
            .addOnFailureListener {
                scope.launch { dao.updateStatus(messageId, "failed") }
                onComplete(false)
            }
    }

    private fun sendToGemini(chatroomId: String, userMessage: String) {
        val db = FirebaseFirestore.getInstance()
        val typingEntity = ChatMessageEntity(
            messageId = "typing_indicator",
            chatroomId = chatroomId,
            senderId = "ai_assistant",
            senderName = "AI Assistant",
            message = "...",
            timestamp = System.currentTimeMillis(),
            status = "typing"
        )
        scope.launch { dao.insert(typingEntity) }

        scope.launch {
            try {
                val model = Firebase.ai.generativeModel("gemini-2.5-flash-lite")

                val reply = model.generateContent(userMessage).text ?: "No response"

                dao.deleteById("typing_indicator")
                val messageId = db.collection("chatRooms").document(chatroomId)
                    .collection("messages").document().id

                // Show the reply locally first so it's never lost if the network/write fails.
                dao.insert(ChatMessageEntity(
                    messageId = messageId,
                    chatroomId = chatroomId,
                    senderId = "ai_assistant",
                    senderName = "AI Assistant",
                    message = reply,
                    timestamp = System.currentTimeMillis(),
                    status = "sending"
                ))

                val aiMessage = hashMapOf(
                    "messageId" to messageId,
                    "senderId" to "ai_assistant",
                    "senderName" to "AI Assistant",
                    "message" to reply,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "type" to "text"
                )

                db.collection("chatRooms").document(chatroomId)
                    .collection("messages").document(messageId)
                    .set(aiMessage)
                    .addOnSuccessListener {
                        scope.launch { dao.updateStatus(messageId, "sent") }
                        db.collection("chatRooms").document(chatroomId).update(
                            "lastMessage", reply,
                            "lastMessageTimestamp", FieldValue.serverTimestamp()
                        )
                    }
                    .addOnFailureListener { e ->
                        scope.launch { dao.updateStatus(messageId, "failed") }
                        Log.e("Gemini", "Failed to save AI reply to Firestore", e)
                    }
            } catch (e: Exception) {
                dao.deleteById("typing_indicator")  // remove typing bubble
                dao.insert(ChatMessageEntity(
                    messageId = "err_${System.currentTimeMillis()}",
                    chatroomId = chatroomId,
                    senderId = "ai_assistant",
                    senderName = "AI Assistant",
                    message = "Failed to get response. Try again.",
                    timestamp = System.currentTimeMillis(),
                    status = "failed"
                ))
                Log.e("Gemini", "Error: ${e.message}")
            }
        }
    }

    // Sync Firestore messages → Room on open
    private var listenerRegistration: ListenerRegistration? = null

    fun clearSyncListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun syncMessages(chatroomId: String) {

        listenerRegistration?.remove()

        listenerRegistration = FirebaseFirestore.getInstance()
            .collection("chatRooms").document(chatroomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                val messages = snapshot?.toObjects(ChatMessage::class.java) ?: return@addSnapshotListener

                scope.launch {
                    messages.forEach { msg ->
                        dao.insert(ChatMessageEntity(
                            messageId = msg.messageId,
                            chatroomId = chatroomId,
                            senderId = msg.senderId,
                            senderName = msg.senderName,
                            message = msg.message,
                            timestamp = msg.timestamp?.toDate()?.time ?: System.currentTimeMillis(),
                            type = msg.type,
                            status = "sent",
                            replyToId = msg.replyToId,
                            replyToSender = msg.replyToSender,
                            replyToSnippet = msg.replyToSnippet,
                            deleted = msg.deleted
                        ))
                    }
                }
            }
    }

    companion object {
        const val DELETED_PLACEHOLDER = "This message was deleted"
        private const val SNIPPET_MAX_CHARS = 120
    }
}