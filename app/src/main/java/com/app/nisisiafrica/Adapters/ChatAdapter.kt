package com.app.nisisiafrica.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.ChatMessageEntity
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<Any>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    companion object {
        private const val VIEW_TYPE_DATE = 0
        private const val VIEW_TYPE_SENDER = 1
        private const val VIEW_TYPE_RECEIVER = 2
    }

    fun submitList(newMessages: List<ChatMessageEntity>) {
        items.clear()
        newMessages.groupBy { msg ->
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(msg.timestamp))
        }.forEach { (_, msgs) ->
            items.add(getDateLabel(Date(msgs.first().timestamp)))
            items.addAll(msgs)
        }
        notifyDataSetChanged()
    }

//    fun submitList(newMessages: List<ChatMessageEntity>) {
//        items.clear()
//        newMessages.groupBy { msg ->
//            msg.timestamp?.toDate()?.let {
//                SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(it)
//            } ?: "unknown"
//        }.forEach { (_, msgs) ->
//            val date = msgs.first().timestamp?.toDate()
//            items.add(getDateLabel(date))
//            items.addAll(msgs)
//        }
//        notifyDataSetChanged()
//    }

    private fun getDateLabel(date: Date?): String {
        date ?: return "Unknown"
        val cal = Calendar.getInstance()
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
        val msgDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date)

        return when (msgDay) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
    }

    override fun getItemViewType(position: Int) = when {
        items[position] is String -> VIEW_TYPE_DATE
        (items[position] as ChatMessageEntity).senderId == currentUserId -> VIEW_TYPE_SENDER
        else -> VIEW_TYPE_RECEIVER
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DateViewHolder -> holder.bind(items[position] as String)
            is MessageViewHolder -> holder.bind(items[position] as ChatMessageEntity)
        }
    }

//    override fun getItemViewType(position: Int) = when {
//        items[position] is String -> VIEW_TYPE_DATE
//        (items[position] as ChatMessage).senderId == currentUserId -> VIEW_TYPE_SENDER
//        else -> VIEW_TYPE_RECEIVER
//    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> DateViewHolder(inflater.inflate(R.layout.item_date_header, parent, false))
            VIEW_TYPE_SENDER -> MessageViewHolder(inflater.inflate(R.layout.message_sender, parent, false))
            else -> MessageViewHolder(inflater.inflate(R.layout.message_receiver, parent, false))
        }
    }

//    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//        when (holder) {
//            is DateViewHolder -> holder.bind(items[position] as String)
//            is MessageViewHolder -> holder.bind(items[position] as ChatMessage)
//        }
//    }

    class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        fun bind(label: String) { tvDate.text = label }
    }

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val tvSender: TextView? = view.findViewById(R.id.tvSender)

        fun bind(message: ChatMessageEntity) {
            tvMessage.text = message.message
            tvSender?.text = message.senderName
            tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
//            message.timestamp?.let {
//                tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate())
//            }
        }
    }
}