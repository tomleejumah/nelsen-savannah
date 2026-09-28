package com.app.nisisiafrica.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.Chatroom
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView

class PinnedChatAdapter(
    private val onChatroomClick: (Chatroom) -> Unit
) : ListAdapter<Chatroom, PinnedChatAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: CircleImageView = itemView.findViewById(R.id.tvAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvLastMsg: TextView = itemView.findViewById(R.id.tvLastMsg)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvUnread)

        fun bind(chatroom: Chatroom) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

            // Pinned: Announcements only (AI assistant hidden until a new provider is wired).
            if (chatroom.chatroomId == "announcements" || chatroom.type == "system") {
                itemView.visibility = View.VISIBLE
                tvName.text = itemView.context.getString(R.string.chat_announcements)
                tvLastMsg.text = chatroom.lastMessage
                    ?: itemView.context.getString(R.string.chat_official_updates)
                Glide.with(tvAvatar.context).load(R.drawable.nelsen_icon).into(tvAvatar)

                val unreadCount = chatroom.unreadCount?.get(currentUserId) ?: 0
                tvUnread.apply {
                    visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                    text = unreadCount.toString()
                }
            } else {
                itemView.visibility = View.GONE
                return
            }

            itemView.setOnClickListener { onChatroomClick(chatroom) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Chatroom>() {
            override fun areItemsTheSame(old: Chatroom, new: Chatroom) = old.chatroomId == new.chatroomId
            override fun areContentsTheSame(old: Chatroom, new: Chatroom) = old == new
        }
    }
}
