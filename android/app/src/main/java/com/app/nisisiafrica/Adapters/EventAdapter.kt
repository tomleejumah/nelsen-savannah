package com.app.nisisiafrica.Adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.Event
import com.github.vipulasri.timelineview.TimelineView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter : RecyclerView.Adapter<EventAdapter.ViewHolder>() {
    private val events = mutableListOf<Event>()

    fun interface OnEventClick {
        fun onClick(event: Event)
    }

    var onEventClick: OnEventClick? = null
    var onReserveClick: OnEventClick? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_timeline, parent, false)
        return ViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(events[position], position)
    }

    override fun getItemCount() = events.size

    override fun getItemViewType(position: Int): Int {
        return TimelineView.getTimeLineViewType(position, itemCount)
    }

    fun submitList(newEvents: List<Event>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val tvEventDate: TextView = itemView.findViewById(R.id.tvEventDate)
        private val tvEventTitle: TextView = itemView.findViewById(R.id.tvEventTitle)
        private val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        private val tvEventPlace: TextView = itemView.findViewById(R.id.tvEventPlace)
        private val tvEventDesc: TextView = itemView.findViewById(R.id.tvEventDesc)
        private val tvProgramTag: TextView = itemView.findViewById(R.id.tvProgramTag)
        private val tvFormatTag: TextView = itemView.findViewById(R.id.tvFormatTag)
        private val tvEventPrice: TextView = itemView.findViewById(R.id.tvEventPrice)
        private val seatsBlock: View = itemView.findViewById(R.id.seatsBlock)
        private val tvSeatsLeft: TextView = itemView.findViewById(R.id.tvSeatsLeft)
        private val tvSeatsPct: TextView = itemView.findViewById(R.id.tvSeatsPct)
        private val seatsProgress: ProgressBar = itemView.findViewById(R.id.seatsProgress)
        private val btnReserve: MaterialButton = itemView.findViewById(R.id.btnReserve)
        private val eventCard: MaterialCardView = itemView.findViewById(R.id.eventCard)
        private val timelineView: TimelineView = itemView.findViewById(R.id.itemDottedLine)

        init {
            timelineView.initLine(viewType)
        }

        @SuppressLint("SetTextI18n")
        fun bind(event: Event, position: Int) {
            val ctx = itemView.context
            val active = ContextCompat.getColor(ctx, R.color.maroon_600)
            val inactive = ContextCompat.getColor(ctx, R.color.line)
            timelineView.setMarker(ContextCompat.getDrawable(ctx, R.drawable.marker_active))
            timelineView.setStartLineColor(if (position == 0) active else inactive, position)
            timelineView.setEndLineColor(
                if (position == events.lastIndex) inactive else active,
                position,
            )
            timelineView.lineStyle = TimelineView.LineStyle.DASHED

            val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            tvEventDate.text = dateFormat.format(Date(event.date))

            tvEventTitle.text = if (event.title.isNotBlank()) event.title
            else "Session with " + listOf(event.mentorName, event.menteeName)
                .firstOrNull { it.isNotBlank() }.orEmpty()

            val desc = event.description
            if (!desc.isNullOrBlank()) {
                tvEventDesc.visibility = View.VISIBLE
                tvEventDesc.text = desc
            } else {
                tvEventDesc.visibility = View.GONE
            }

            if (event.program.isNotBlank()) {
                tvProgramTag.visibility = View.VISIBLE
                tvProgramTag.text = event.program
            } else {
                tvProgramTag.visibility = View.GONE
            }

            val online = "online".equals(event.mode, ignoreCase = true)
            tvFormatTag.text = if (online) "Online" else "In person"

            val time = when {
                event.startTime.isNotBlank() && event.endTime.isNotBlank() ->
                    "${event.startTime} – ${event.endTime}"
                event.startTime.isNotBlank() -> event.startTime
                else -> ""
            }
            tvEventTime.text = time
            tvEventTime.visibility = if (time.isBlank()) View.GONE else View.VISIBLE

            val place = if (online) {
                if (event.meetingLink.isNotBlank()) "Online meeting" else "Online"
            } else event.location
            if (place.isNotBlank()) {
                tvEventPlace.visibility = View.VISIBLE
                tvEventPlace.text = place
            } else {
                tvEventPlace.visibility = View.GONE
            }

            if (event.price.isNotBlank()) {
                tvEventPrice.visibility = View.VISIBLE
                tvEventPrice.text = event.price
            } else {
                tvEventPrice.visibility = View.GONE
            }

            if (event.seats > 0) {
                seatsBlock.visibility = View.VISIBLE
                val left = (event.seats - event.seatsTaken).coerceAtLeast(0)
                val pct = ((event.seatsTaken.toFloat() / event.seats) * 100f).toInt().coerceIn(0, 100)
                tvSeatsLeft.text = "$left seats left"
                tvSeatsPct.text = "$pct%"
                seatsProgress.progress = pct
                if (event.eventType != "announcement") {
                    btnReserve.isEnabled = left > 0
                    btnReserve.text = if (left > 0) "Reserve a seat ($left left)" else "Fully booked"
                } else {
                    btnReserve.isEnabled = true
                    btnReserve.text = "Reserve a seat"
                }
            } else {
                seatsBlock.visibility = View.GONE
                btnReserve.isEnabled = true
                btnReserve.text = "Reserve a seat"
            }

            val open = { onEventClick?.onClick(event) }
            eventCard.setOnClickListener { open() }
            btnReserve.setOnClickListener {
                if (event.seats > 0 && event.eventType != "announcement") {
                    onReserveClick?.onClick(event) ?: open()
                } else {
                    open()
                }
            }
        }
    }
}
