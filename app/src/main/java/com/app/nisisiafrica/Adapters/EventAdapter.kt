package com.app.nisisiafrica.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.nisisiafrica.R
import com.app.nisisiafrica.data.Model.Event
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class EventAdapter : RecyclerView.Adapter<EventAdapter.ViewHolder>() {
    private val events = mutableListOf<Event>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_timeline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(events[position], position == events.lastIndex)
    }

    override fun getItemCount() = events.size

    fun submitList(newEvents: List<Event>) {
        events.clear()
        events.addAll(newEvents)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEventDate: TextView = itemView.findViewById(R.id.tvEventDate)
        private val tvEventTitle: TextView = itemView.findViewById(R.id.tvEventTitle)
        private val tvEventTime: TextView = itemView.findViewById(R.id.tvEventTime)
        private val tvEventBadge: TextView = itemView.findViewById(R.id.tvEventBadge)
        private val timelineDot: View = itemView.findViewById(R.id.timelineDot)
        private val dottedLine: View = itemView.findViewById(R.id.dottedLine)
        private val eventCard: MaterialCardView = itemView.findViewById(R.id.eventCard)

        fun bind(event: Event, isLast: Boolean) {
            // Format date
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            tvEventDate.text = dateFormat.format(event.date.toDate())

            tvEventTitle.text = event.title
            tvEventTime.text = "${event.startTime} - ${event.endTime}"

            // Hide line for last item
            dottedLine.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

            // Set colors based on status
            when (event.status) {
                0 -> { // Upcoming
//                    timelineDot.setBackgroundResource(R.drawable.timeline_dot_upcoming)
                    eventCard.strokeColor = 0xFFFF9800.toInt()
                    tvEventBadge.text = "Upcoming"
                    tvEventBadge.setBackgroundColor(0xFFFFF3E0.toInt())
                    tvEventBadge.setTextColor(0xFFF57C00.toInt())
                }
                1 -> { // Completed
//                    timelineDot.setBackgroundResource(R.drawable.timeline_dot_completed)
                    eventCard.strokeColor = 0xFF4CAF50.toInt()
                    eventCard.alpha = 0.7f
                    tvEventBadge.text = "Completed"
                    tvEventBadge.setBackgroundColor(0xFFE8F5E9.toInt())
                    tvEventBadge.setTextColor(0xFF2E7D32.toInt())
                }
                2 -> { // Today
                    timelineDot.setBackgroundResource(R.drawable.timeline_dot)
                    eventCard.strokeColor = 0xFF2196F3.toInt()
                    tvEventBadge.text = "Today"
                    tvEventBadge.setBackgroundColor(0xFFE3F2FD.toInt())
                    tvEventBadge.setTextColor(0xFF1565C0.toInt())
                }
            }
        }
    }
}