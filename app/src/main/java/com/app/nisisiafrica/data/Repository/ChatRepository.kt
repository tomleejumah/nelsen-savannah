package com.app.nisisiafrica.data.Repository

import android.util.Log
import com.app.nisisiafrica.BuildConfig
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

    fun sendMessage(chatroomId: String, message: String, onComplete: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val user = FirebaseAuth.getInstance().currentUser ?: return onComplete(false)

        val messageId = db.collection("chatRooms").document(chatroomId)
            .collection("messages").document().id
        val time : Long = System.currentTimeMillis()

        val entity = ChatMessageEntity(
            messageId = messageId,
            chatroomId = chatroomId,
            senderId = user.uid,
            senderName = user.displayName ?: "User",
            message = message,
            timestamp = System.currentTimeMillis(),
            status = "sending"
        )

        scope.launch { dao.insert(entity) }

        val chatMessage = hashMapOf(
            "messageId" to messageId,
            "senderId" to user.uid,
            "senderName" to (user.displayName ?: "User"),
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to "text"
        )

        db.collection("chatRooms").document(chatroomId)
            .collection("messages").document(messageId)
            .set(chatMessage)
            .addOnSuccessListener {
                scope.launch { dao.updateStatus(messageId, "sent") }
                db.collection("chatRooms").document(chatroomId).update(
                    "lastMessage", message,
                    "lastMessageTimestamp", FieldValue.serverTimestamp()
                )
                if (chatroomId.startsWith("ai_assistant_")) sendToGemini(chatroomId, message)
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
                        scope.launch {
                            dao.insert(ChatMessageEntity(
                                messageId = messageId,
                                chatroomId = chatroomId,
                                senderId = "ai_assistant",
                                senderName = "AI Assistant",
                                message = reply,
                                timestamp = System.currentTimeMillis(),
                                status = "sent"
                            ))
                        }
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
                            status = "sent"
                        ))
                    }
                }
            }
    }
//    fun syncMessages(chatroomId: String) {
//        FirebaseFirestore.getInstance()
//            .collection("chatRooms").document(chatroomId)
//            .collection("messages")
//            .orderBy("timestamp", Query.Direction.ASCENDING)
//            .limit(50)
//            .addSnapshotListener { snapshot, e ->
//                if (e != null) return@addSnapshotListener
//                val messages = snapshot?.toObjects(ChatMessage::class.java) ?: return@addSnapshotListener
//                scope.launch {
//                    messages.forEach { msg ->
//                        dao.insert(ChatMessageEntity(
//                            messageId = msg.messageId,
//                            chatroomId = chatroomId,
//                            senderId = msg.senderId,
//                            senderName = msg.senderName,
//                            message = msg.message,
//                            timestamp = msg.timestamp?.toDate()?.time ?: System.currentTimeMillis(),
//                            status = "sent"
//                        ))
//                    }
//                }
//            }
//    }
}