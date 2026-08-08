package com.app.nisisiafrica.Adapters

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.Utils.ThemeColors
import com.app.nisisiafrica.data.Model.ChatMessageEntity
import com.app.nisisiafrica.data.Repository.ChatRepository
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<Any>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    var onReply: ((ChatMessageEntity) -> Unit)? = null

    /** Long-press on a bubble. The boolean says whether the message is the current user's. */
    var onMessageLongClick: ((ChatMessageEntity, Boolean) -> Unit)? = null

    /** Tap on a quoted block; carries the id of the message being quoted. */
    var onQuotedClick: ((String) -> Unit)? = null

    /** Set while a jumped-to message should flash, then cleared. */
    private var highlightedId: String? = null

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

    fun getItemAt(position: Int): Any = items[position]

    /** Adapter position of [messageId], or -1 when it isn't in the loaded window. */
    fun positionOf(messageId: String): Int =
        items.indexOfFirst { it is ChatMessageEntity && it.messageId == messageId }

    /** Flashes [messageId] once to show where a quote jumped to. */
    fun flashMessage(messageId: String) {
        val position = positionOf(messageId)
        if (position < 0) return
        highlightedId = messageId
        notifyItemChanged(position)
    }

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
            is MessageViewHolder -> {
                val message = items[position] as ChatMessageEntity
                holder.bind(
                    message,
                    isMine = message.senderId == currentUserId,
                    onMessageLongClick = onMessageLongClick,
                    onQuotedClick = onQuotedClick
                )
                if (highlightedId == message.messageId) {
                    highlightedId = null
                    holder.flash()
                }
            }
        }
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> DateViewHolder(
                inflater.inflate(R.layout.item_date_header, parent, false)
            )
            VIEW_TYPE_SENDER -> MessageViewHolder(
                inflater.inflate(R.layout.message_sender, parent, false)
            )
            else -> MessageViewHolder(
                inflater.inflate(R.layout.message_receiver, parent, false)
            )
        }
    }

    class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        fun bind(label: String) { tvDate.text = label }
    }

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val tvSender: TextView? = view.findViewById(R.id.tvSender)
        private val llMessage: View? = view.findViewById(R.id.llMessage)
        private val ivImage: ImageView? = view.findViewById(R.id.ivImage)
        private val quotedReply: View? = view.findViewById(R.id.quotedReply)
        private val tvQuotedSender: TextView? = view.findViewById(R.id.tvQuotedSender)
        private val tvQuotedSnippet: TextView? = view.findViewById(R.id.tvQuotedSnippet)

        private val defaultTextColor = tvMessage.currentTextColor

        fun bind(
            message: ChatMessageEntity,
            isMine: Boolean,
            onMessageLongClick: ((ChatMessageEntity, Boolean) -> Unit)?,
            onQuotedClick: ((String) -> Unit)?
        ) {
            // Reset state carried over from a recycled row.
            tvMessage.paintFlags = tvMessage.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            tvMessage.setOnClickListener(null)
            tvMessage.setTypeface(null, Typeface.NORMAL)
            tvMessage.setTextColor(defaultTextColor)
            tvMessage.alpha = 1f
            itemView.setBackgroundColor(Color.TRANSPARENT)

            bindQuote(message, onQuotedClick)

            when {
                message.deleted -> {
                    ivImage?.visibility = View.GONE
                    tvMessage.visibility = View.VISIBLE
                    tvMessage.text = ChatRepository.DELETED_PLACEHOLDER
                    tvMessage.setTypeface(null, Typeface.ITALIC)
                    tvMessage.alpha = 0.7f
                }
                message.type == "image" && ivImage != null -> {
                    tvMessage.visibility = View.GONE
                    ivImage.visibility = View.VISIBLE
                    Glide.with(ivImage.context)
                        .load(message.message)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(ivImage)
                    ivImage.setOnClickListener { openUrl(it, message.message) }
                }
                message.type == "file" || message.type == "audio" -> {
                    ivImage?.visibility = View.GONE
                    tvMessage.visibility = View.VISIBLE
                    tvMessage.text = if (message.type == "audio") "\uD83C\uDFB5 Audio message"
                        else "\uD83D\uDCC4 Document — tap to open"
                    tvMessage.paintFlags = tvMessage.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    tvMessage.setOnClickListener { openUrl(it, message.message) }
                }
                else -> {
                    ivImage?.visibility = View.GONE
                    tvMessage.visibility = View.VISIBLE
                    tvMessage.text = message.message
                }
            }

            tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

            // Deleted messages have no actions worth offering.
            if (message.deleted || onMessageLongClick == null) {
                llMessage?.setOnLongClickListener(null)
                llMessage?.isLongClickable = false
            } else {
                llMessage?.setOnLongClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onMessageLongClick(message, isMine)
                    true
                }
            }

            // tvSender only exists on the received-message layout.
            if (tvSender != null) {
                val isAi = message.senderId == "ai_assistant"
                if (isAi) {
                    tvSender.visibility = View.VISIBLE
                    tvSender.text = "AI Assistant"
                    llMessage?.setBackgroundResource(R.drawable.message_ai_bg)
                } else {
                    tvSender.visibility = View.GONE
                    llMessage?.setBackgroundResource(R.drawable.message_bg)
                }
            }
        }

        private fun bindQuote(message: ChatMessageEntity, onQuotedClick: ((String) -> Unit)?) {
            val quote = quotedReply ?: return
            if (!message.isReply) {
                quote.visibility = View.GONE
                quote.setOnClickListener(null)
                return
            }
            quote.visibility = View.VISIBLE
            tvQuotedSender?.text = message.replyToSender.ifEmpty { "Message" }
            tvQuotedSnippet?.text =
                message.replyToSnippet.ifEmpty { ChatRepository.DELETED_PLACEHOLDER }
            quote.setOnClickListener { onQuotedClick?.invoke(message.replyToId) }
        }

        /** Brief accent wash over the row, so a jump target is obvious. */
        fun flash() {
            val accent = ThemeColors.accent(itemView.context)
            val from = ColorUtils.setAlphaComponent(accent, 60)
            ValueAnimator.ofObject(ArgbEvaluator(), from, Color.TRANSPARENT).apply {
                duration = 1200
                addUpdateListener { itemView.setBackgroundColor(it.animatedValue as Int) }
                start()
            }
        }

        private fun openUrl(v: View, url: String?) {
            if (url.isNullOrBlank()) return
            try {
                v.context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(v.context, "Can't open attachment", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
