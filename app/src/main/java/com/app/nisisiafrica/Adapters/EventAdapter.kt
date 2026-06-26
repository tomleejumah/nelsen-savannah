package com.app.nisisiafrica.Adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.Event
import com.github.vipulasri.timelineview.TimelineView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventAdapter : RecyclerView.Adapter<EventAdapter.ViewHolder>() {
    private val events = mutableListOf<Event>()

    fun interface OnEventClick {
        fun onClick(event: Event)
    }

    /** Invoked when an event card is tapped (host shows the actions sheet). */
    var onEventClick: OnEventClick? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_timeline, parent, false)
        return ViewHolder(view,viewType)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(events[position], position == events.lastIndex)
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

    inner class ViewHolder(itemView: View,viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val tvEventDate: TextView = itemView.findViewById(R.id.tvEventDate)
        private val tvEventTitle: TextView = itemView.findViewById(R.id.tvEventTitle)
        private val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        private val tvEventPlace: TextView = itemView.findViewById(R.id.tvEventPlace)
        private val tvEventBadge: TextView = itemView.findViewById(R.id.tvEventBadge)
        private val timelineDot: View = itemView.findViewById(R.id.timelineDot)
        private val dottedLine: View = itemView.findViewById(R.id.dottedLine)
        private val eventCard: MaterialCardView = itemView.findViewById(R.id.eventCard)
        private val timelineView: TimelineView = itemView.findViewById(R.id.itemDottedLine)

        init {
            timelineView.initLine(viewType)
        }


        @SuppressLint("SetTextI18n")
        fun bind(event: Event, isLast: Boolean) {
            // Format date
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
//            tvEventDate.text = dateFormat.format(event.date.toDate())
            tvEventDate.text = dateFormat.format(Date(event.date))

            tvEventTitle.text = if (event.title.isNotBlank()) event.title
                else "Session with " + listOf(event.mentorName, event.menteeName)
                    .firstOrNull { it.isNotBlank() }.orEmpty()
            tvEventTime.text = "${event.startTime} - ${event.endTime}"

            val online = "online".equals(event.mode, ignoreCase = true)
            val place = if (online) "Online" else event.location
            if (place.isNotBlank()) {
                tvEventPlace.visibility = View.VISIBLE
                tvEventPlace.text = place
                tvEventPlace.setCompoundDrawablesWithIntrinsicBounds(
                    if (online) R.drawable.ic_video else R.drawable.ic_location, 0, 0, 0
                )
            } else {
                tvEventPlace.visibility = View.GONE
            }

            eventCard.setOnClickListener { onEventClick?.onClick(event) }

            // Hide line for last item
//            timelineView.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
//            dottedLine.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

            // Set colors based on status
            when (event.status) {
                0 -> { //todo
//                    timelineDot.setBackgroundResource(R.drawable.timeline_dot_upcoming)
                    timelineView.setMarker(
                        ContextCompat.getDrawable(itemView.context, R.drawable.marker_completed)
                    )
//                    timelineView.setStartLineColor(
//                        ContextCompat.getColor(itemView.context, R.color.timeline_today),
//                        position
//                    )
//                    timelineView.setLineStyle(TimelineView.LineStyle.NORMAL)
                    eventCard.strokeColor = 0xFFFF9800.toInt()
                    tvEventBadge.text = "Upcoming"
                    tvEventBadge.setBackgroundColor(0xFFFFF3E0.toInt())
                    tvEventBadge.setTextColor(0xFFF57C00.toInt())
                }
                1 -> { //todo
//                    timelineDot.setBackgroundResource(R.drawable.timeline_dot_completed)

                    timelineView.setMarker(
                        ContextCompat.getDrawable(itemView.context, R.drawable.marker_completed)
                    )
//                    timelineView.setStartLineColor(
//                        ContextCompat.getColor(itemView.context, R.color.timeline_today),
//                               position
//                    )
//                    timelineView.setLineStyle(TimelineView.LineStyle.NORMAL)
                    eventCard.strokeColor = 0xFF4CAF50.toInt()
                    eventCard.alpha = 0.7f
                    tvEventBadge.text = "Completed"
                    tvEventBadge.setBackgroundColor(0xFFE8F5E9.toInt())
                    tvEventBadge.setTextColor(0xFF2E7D32.toInt())
                }
                2 -> { // Today
//                    timelineDot.setBackgroundResource(R.drawable.timeline_dot)
                    timelineView.setMarker(
                        ContextCompat.getDrawable(itemView.context, R.drawable.marker_completed)
                    )
//                    timelineView.setStartLineColor(
//                        ContextCompat.getColor(itemView.context, R.color.timeline_today),
//                        position
//                    )
//                    timelineView.setLineStyle(TimelineView.LineStyle.NORMAL)
                    eventCard.strokeColor = 0xFF2196F3.toInt()
                    tvEventBadge.text = "Today"
                    tvEventBadge.setBackgroundColor(0xFFE3F2FD.toInt())
                    tvEventBadge.setTextColor(0xFF1565C0.toInt())
                }
            }
        }
    }
}