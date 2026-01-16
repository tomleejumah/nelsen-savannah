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
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView

class ChatAdapter (
    private val onChatroomClick: (Chatroom) -> Unit
): PagingDataAdapter<Chatroom, ChatAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: CircleImageView = itemView.findViewById(R.id.tvAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvLastMsg: TextView = itemView.findViewById(R.id.tvLastMsg)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvUnread)

        fun bind(chatroom: Chatroom) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

            tvName.text = chatroom.getOtherUserName(currentUserId)
//            getOtherUserName(chatroom, currentUserId)
            tvLastMsg.text = chatroom.lastMessage ?: "No messages yet"

            if (chatroom.chatroomId == "announcements") {
                tvName.text = "Announcements"
                Glide.with(tvAvatar.context)
                    .load(R.drawable.nisisi_logo)
                    .into(tvAvatar)

            } else {
                tvName.text = chatroom.getOtherUserName(currentUserId)
                val otherUserId: String? = chatroom.getOtherUserId(currentUserId)
                otherUserId?.let {
                    FirebaseRemoteDataSource.getMentorData(it, onSuccess = { mentorData ->
                        Glide.with(tvAvatar.context)
                            .load(mentorData?.mentorImageUrl)
                            .placeholder(R.drawable.ic_person)
                            .error(R.drawable.ic_person)
                            .into(tvAvatar)
                    }, onError = { exception ->
                        {
                            // Handle error
                        }
                    })

                    tvLastMsg.text = chatroom.lastMessage ?: "No messages yet"
                    val unreadCount = chatroom.unreadCount[currentUserId] ?: 0
                    if (unreadCount > 0) {
                        tvUnread.visibility = View.VISIBLE
                        tvUnread.text = unreadCount.toString()
                    } else {
                        tvUnread.visibility = View.GONE
                    }

                    itemView.setOnClickListener {
                        onChatroomClick(chatroom)
                    }

                    // Avatar placeholder
                    //            tvAvatar.text = tvName.text.firstOrNull()?.toString() ?: "?"
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