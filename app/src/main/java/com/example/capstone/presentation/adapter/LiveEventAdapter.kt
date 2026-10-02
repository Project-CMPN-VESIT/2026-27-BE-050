package com.example.capstone.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.R
import com.example.capstone.data.disaster.AlertLevel
import com.example.capstone.data.disaster.DisasterEvent
import java.text.DateFormat
import java.util.Date

class LiveEventAdapter(
    private val onClick: (DisasterEvent) -> Unit
) : ListAdapter<DisasterEvent, LiveEventAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_live_event, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (DisasterEvent) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.eventTitle)
        private val location: TextView = itemView.findViewById(R.id.eventLocation)
        private val status: TextView = itemView.findViewById(R.id.eventStatus)
        private val severity: TextView = itemView.findViewById(R.id.eventSeverity)
        private val time: TextView = itemView.findViewById(R.id.eventTime)
        private val confidence: TextView = itemView.findViewById(R.id.eventConfidence)

        private var current: DisasterEvent? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(item: DisasterEvent) {
            current = item
            title.text = item.displayTitle()
            location.text = item.location
            status.text = item.alertLevel.name.replace('_', ' ')
            severity.text = item.severity
            time.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.lastUpdated))
            confidence.text = itemView.context.getString(R.string.event_confidence_format, (item.evidenceConfidence * 100).toInt())

            val color = when (item.alertLevel) {
                AlertLevel.AFFECTED -> R.color.status_danger
                AlertLevel.AT_RISK -> R.color.status_warning
                AlertLevel.WARNING_MONITORING -> R.color.status_warning
                else -> R.color.status_success
            }
            status.setTextColor(itemView.context.getColor(color))
        }
    }

    private object Diff : DiffUtil.ItemCallback<DisasterEvent>() {
        override fun areItemsTheSame(oldItem: DisasterEvent, newItem: DisasterEvent): Boolean = oldItem.eventId == newItem.eventId
        override fun areContentsTheSame(oldItem: DisasterEvent, newItem: DisasterEvent): Boolean = oldItem == newItem
    }
}
