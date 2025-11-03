package com.app.nisisiafrica.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.Chatroom
import com.google.firebase.auth.FirebaseAuth

class ChatAdapter : PagingDataAdapter<Chatroom, ChatAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvLastMsg: TextView = itemView.findViewById(R.id.tvLastMsg)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvUnread)

        fun bind(chatroom: Chatroom) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

            // Get other user's name (you'll need to fetch from users collection)
            tvName.text = getOtherUserName(chatroom, currentUserId)

            // Last message
            tvLastMsg.text = chatroom.lastMessage ?: "No messages yet"

            // Unread count
            val unreadCount = chatroom.unreadCount[currentUserId] ?: 0
            if (unreadCount > 0) {
                tvUnread.visibility = View.VISIBLE
                tvUnread.text = unreadCount.toString()
            } else {
                tvUnread.visibility = View.GONE
            }

            // Avatar placeholder
            tvAvatar.text = tvName.text.firstOrNull()?.toString() ?: "?"
        }

        private fun getOtherUserName(chatroom: Chatroom, currentUserId: String): String {
            // TODO: Fetch other user's name from Firestore users collection
            // For now, return placeholder
            return "User"
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Chatroom>() {
            override fun areItemsTheSame(old: Chatroom, new: Chatroom) =
                old.chatroomId == new.chatroomId
            override fun areContentsTheSame(old: Chatroom, new: Chatroom) = old == new
        }
    }
}