package com.app.nisisiafrica.Adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.Chatroom
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView
class ChatRoomAdapter(
    private val onChatroomClick: (Chatroom) -> Unit
): PagingDataAdapter<Chatroom, ChatRoomAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var TAG = "ChatAdapter"
    private var announcementChatroom: Chatroom? = null

    override fun getItemCount(): Int {
        return super.getItemCount()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && announcementChatroom != null) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Check if this is announcements and pin it
        getItem(position)?.let { chatroom ->
            if (chatroom.chatroomId == "announcements") {
                if (announcementChatroom == null) {
                    announcementChatroom = chatroom
                    notifyDataSetChanged()
                }
                // Don't bind announcements in regular positions
                if (position != 0) return
            }

            // Bind announcement at position 0
            if (position == 0 && announcementChatroom != null) {
                holder.bind(announcementChatroom!!)
            } else {
                holder.bind(chatroom)
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: CircleImageView = itemView.findViewById(R.id.tvAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvLastMsg: TextView = itemView.findViewById(R.id.tvLastMsg)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvUnread)

        fun bind(chatroom: Chatroom) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

            if (chatroom.chatroomId == "announcements") {
                tvName.text = "Announcements"
                tvLastMsg.text = chatroom.lastMessage ?: "No announcements yet"
                tvUnread.visibility = View.GONE
                Glide.with(tvAvatar.context)
                    .load(R.drawable.nisisi_logo)
                    .into(tvAvatar)

                itemView.setOnClickListener {
                    onChatroomClick(chatroom)
                }
            } else {
                tvName.text = chatroom.getOtherUserName(currentUserId)
                tvLastMsg.text = chatroom.lastMessage ?: "No messages yet"

                val otherUserId: String? = chatroom.getOtherUserId(currentUserId)
                otherUserId?.let {
                    FirebaseRemoteDataSource.getMentorData(it, onSuccess = { mentorData ->
                        Glide.with(tvAvatar.context)
                            .load(mentorData?.mentorImageUrl)
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .into(tvAvatar)
                    }, onError = { exception ->
                        Log.d(TAG, "bind: ${exception.message}")
                    })

                    val unreadCount = chatroom.unreadCount?.get(currentUserId) ?: 0
                    if (unreadCount > 0) {
                        tvUnread.visibility = View.VISIBLE
                        tvUnread.text = unreadCount.toString()
                    } else {
                        tvUnread.visibility = View.GONE
                    }

                    itemView.setOnClickListener {
                        onChatroomClick(chatroom)
                    }
                }
            }
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
