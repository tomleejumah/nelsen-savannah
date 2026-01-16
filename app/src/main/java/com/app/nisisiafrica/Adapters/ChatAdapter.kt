package com.app.nisisiafrica.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale


class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    fun submitList(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int) =
        if (messages[position].senderId == currentUserId) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == 1) R.layout.message_sender else R.layout.message_receiver
        return MessageViewHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val tvSender: TextView? = view.findViewById(R.id.tvSender)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.message
            tvSender?.text = message.senderName
            message.timestamp?.let {
                tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate())
            }
        }
    }
}
