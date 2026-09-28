package com.app.nisisiafrica.Adapters

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.media.MediaPlayer
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
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
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<Any>()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val selectedIds = LinkedHashSet<String>()

    var onReply: ((ChatMessageEntity) -> Unit)? = null

    /** First long-press enters selection; subsequent taps toggle. */
    var onSelectionChanged: ((Int) -> Unit)? = null

    /** Tap on a quoted block; carries the id of the message being quoted. */
    var onQuotedClick: ((String) -> Unit)? = null

    /** Set while a jumped-to message should flash, then cleared. */
    private var highlightedId: String? = null

    val selectionCount: Int get() = selectedIds.size
    val inSelectionMode: Boolean get() = selectedIds.isNotEmpty()

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
        // Drop selections that no longer exist in the list.
        val liveIds = newMessages.map { it.messageId }.toSet()
        selectedIds.retainAll(liveIds)
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun getItemAt(position: Int): Any = items[position]

    fun positionOf(messageId: String): Int =
        items.indexOfFirst { it is ChatMessageEntity && it.messageId == messageId }

    fun flashMessage(messageId: String) {
        val position = positionOf(messageId)
        if (position < 0) return
        highlightedId = messageId
        notifyItemChanged(position)
    }

    fun toggleSelection(message: ChatMessageEntity) {
        if (message.deleted) return
        val wasSelecting = inSelectionMode
        if (!selectedIds.add(message.messageId)) selectedIds.remove(message.messageId)
        val nowSelecting = inSelectionMode
        // Entering/leaving selection must rebind ALL rows so single-tap selects
        // (otherwise only the long-pressed row has selectionMode=true).
        if (wasSelecting != nowSelecting) {
            notifyDataSetChanged()
        } else {
            val pos = positionOf(message.messageId)
            if (pos >= 0) notifyItemChanged(pos)
        }
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun clearSelection() {
        if (selectedIds.isEmpty()) return
        val old = selectedIds.toList()
        selectedIds.clear()
        old.forEach { id ->
            val pos = positionOf(id)
            if (pos >= 0) notifyItemChanged(pos)
        }
        onSelectionChanged?.invoke(0)
    }

    fun selectedMessages(): List<ChatMessageEntity> =
        items.filterIsInstance<ChatMessageEntity>().filter { selectedIds.contains(it.messageId) }

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
                    message = message,
                    isMine = message.senderId == currentUserId,
                    selected = selectedIds.contains(message.messageId),
                    selectionMode = inSelectionMode,
                    onToggle = { toggleSelection(it) },
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
        private val videoBubble: VideoView? = view.findViewById(R.id.videoBubble)
        private val quotedReply: View? = view.findViewById(R.id.quotedReply)
        private var audioPlayer: MediaPlayer? = null
        private val tvQuotedSender: TextView? = view.findViewById(R.id.tvQuotedSender)
        private val tvQuotedSnippet: TextView? = view.findViewById(R.id.tvQuotedSnippet)

        private val defaultTextColor = tvMessage.currentTextColor

        fun bind(
            message: ChatMessageEntity,
            isMine: Boolean,
            selected: Boolean,
            selectionMode: Boolean,
            onToggle: (ChatMessageEntity) -> Unit,
            onQuotedClick: ((String) -> Unit)?
        ) {
            tvMessage.paintFlags = tvMessage.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            tvMessage.setOnClickListener(null)
            tvMessage.setTypeface(null, Typeface.NORMAL)
            tvMessage.setTextColor(defaultTextColor)
            tvMessage.alpha = 1f
            stopAudio()
            ivImage?.setOnClickListener(null)
            ivImage?.setOnLongClickListener(null)
            videoBubble?.setOnClickListener(null)
            videoBubble?.visibility = View.GONE
            ivImage?.visibility = View.GONE

            val selectBg = if (selected) {
                ColorUtils.setAlphaComponent(ThemeColors.accent(itemView.context), 50)
            } else {
                Color.TRANSPARENT
            }
            itemView.setBackgroundColor(selectBg)

            bindQuote(message, onQuotedClick)
            applyQuoteMinWidth(message)

            when {
                message.deleted -> {
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
                    if (!selectionMode) {
                        ivImage.setOnClickListener { openUrl(it, message.message) }
                    }
                }
                message.type == "video" && videoBubble != null -> {
                    tvMessage.visibility = View.GONE
                    videoBubble.visibility = View.VISIBLE
                    if (!selectionMode) {
                        videoBubble.setOnClickListener {
                            videoBubble.setVideoURI(Uri.parse(message.message))
                            videoBubble.start()
                        }
                    }
                }
                message.type == "audio" -> {
                    tvMessage.visibility = View.VISIBLE
                    tvMessage.text = "Play audio"
                    if (!selectionMode) {
                        tvMessage.setOnClickListener { toggleAudio(message.message) }
                    }
                }
                message.type == "file" -> {
                    tvMessage.visibility = View.VISIBLE
                    val name = Uri.parse(message.message).lastPathSegment ?: "Document"
                    tvMessage.text = name
                    tvMessage.paintFlags = tvMessage.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    if (!selectionMode) {
                        tvMessage.setOnClickListener { openUrl(it, message.message) }
                    }
                }
                else -> {
                    tvMessage.visibility = View.VISIBLE
                    tvMessage.text = message.message
                }
            }

            // TODO(read-receipts): single tick = delivered, double tick = read (not implemented yet).
            tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

            val longClick = View.OnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onToggle(message)
                true
            }
            val click = View.OnClickListener {
                if (selectionMode) onToggle(message)
            }

            if (message.deleted) {
                llMessage?.setOnLongClickListener(null)
                llMessage?.setOnClickListener(null)
                itemView.setOnLongClickListener(null)
                itemView.setOnClickListener(null)
                tvMessage.setOnClickListener(null)
                ivImage?.setOnClickListener(null)
                llMessage?.isLongClickable = false
            } else {
                llMessage?.setOnLongClickListener(longClick)
                itemView.setOnLongClickListener(longClick)
                ivImage?.setOnLongClickListener(longClick)
                videoBubble?.setOnLongClickListener(longClick)
                tvMessage.setOnLongClickListener(longClick)
                if (selectionMode) {
                    llMessage?.setOnClickListener(click)
                    itemView.setOnClickListener(click)
                    tvMessage.setOnClickListener(click)
                    ivImage?.setOnClickListener(click)
                    videoBubble?.setOnClickListener(click)
                } else {
                    llMessage?.setOnClickListener(null)
                    itemView.setOnClickListener(null)
                    // Image/file open handlers already set above when !selectionMode.
                }
            }

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

        /**
         * When the reply body is short but the quoted snippet is long, grow the
         * bubble so the quote isn't clipped to a tiny wrap_content width.
         */
        private fun applyQuoteMinWidth(message: ChatMessageEntity) {
            val bubble = llMessage ?: return
            if (!message.isReply || message.deleted) {
                bubble.minimumWidth = 0
                return
            }
            val density = itemView.resources.displayMetrics.density
            val paint = TextPaint(tvQuotedSnippet?.paint ?: tvMessage.paint)
            val snippet = message.replyToSnippet.ifEmpty { ChatRepository.DELETED_PLACEHOLDER }
            val sender = message.replyToSender.ifEmpty { "Message" }
            val needed = max(paint.measureText(snippet.take(90)), paint.measureText(sender))
            val min = (160 * density).toInt()
            val max = (280 * density).toInt()
            bubble.minimumWidth = min(max, max(min, (needed + 36 * density).toInt()))
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

        fun flash() {
            val accent = ThemeColors.accent(itemView.context)
            val from = ColorUtils.setAlphaComponent(accent, 60)
            ValueAnimator.ofObject(ArgbEvaluator(), from, Color.TRANSPARENT).apply {
                duration = 1200
                addUpdateListener { itemView.setBackgroundColor(it.animatedValue as Int) }
                start()
            }
        }

        private fun toggleAudio(url: String?) {
            if (url.isNullOrBlank()) return
            if (audioPlayer?.isPlaying == true) {
                stopAudio()
                tvMessage.text = "Play audio"
                return
            }
            stopAudio()
            val player = MediaPlayer()
            audioPlayer = player
            try {
                player.setDataSource(url)
                player.setOnPreparedListener {
                    tvMessage.text = "Pause audio"
                    it.start()
                }
                player.setOnCompletionListener {
                    tvMessage.text = "Play audio"
                    stopAudio()
                }
                player.prepareAsync()
            } catch (e: Exception) {
                stopAudio()
                android.widget.Toast.makeText(itemView.context, "Can't play audio", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        private fun stopAudio() {
            audioPlayer?.release()
            audioPlayer = null
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
