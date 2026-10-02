package com.example.capstone.presentation.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.R
import com.example.capstone.data.disaster.DisasterEvidence
import com.example.capstone.data.disaster.SourceType
import java.text.DateFormat
import java.util.Date

class LiveEvidenceAdapter : ListAdapter<DisasterEvidence, LiveEvidenceAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_live_evidence, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sourceType: TextView = itemView.findViewById(R.id.evidenceSourceType)
        private val sourceName: TextView = itemView.findViewById(R.id.evidenceSourceName)
        private val title: TextView = itemView.findViewById(R.id.evidenceTitle)
        private val body: TextView = itemView.findViewById(R.id.evidenceBody)
        private val timestamp: TextView = itemView.findViewById(R.id.evidenceTimestamp)
        private val location: TextView = itemView.findViewById(R.id.evidenceLocation)
        private val contribution: TextView = itemView.findViewById(R.id.evidenceContribution)
        private val urlButton: Button = itemView.findViewById(R.id.evidenceOpenUrl)

        fun bind(item: DisasterEvidence) {
            sourceType.text = item.sourceType.displayName()
            sourceName.text = item.source
            title.text = item.title
            body.text = item.text
            timestamp.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.timestamp))
            location.text = item.location
            contribution.text = if (item.usableAsEvidence) {
                itemView.context.getString(R.string.evidence_contributed)
            } else {
                itemView.context.getString(R.string.evidence_not_contributed)
            }
            urlButton.isEnabled = item.originalUrl.isNotBlank()
            urlButton.setOnClickListener {
                if (item.originalUrl.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.originalUrl))
                    itemView.context.startActivity(intent)
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DisasterEvidence>() {
        override fun areItemsTheSame(oldItem: DisasterEvidence, newItem: DisasterEvidence): Boolean =
            oldItem.originalUrl == newItem.originalUrl && oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: DisasterEvidence, newItem: DisasterEvidence): Boolean = oldItem == newItem
    }
}

private fun SourceType.displayName(): String = when (this) {
    SourceType.OFFICIAL -> "OFFICIAL"
    SourceType.NEWS -> "NEWS"
    SourceType.SOCIAL -> "SOCIAL"
    SourceType.VIDEO -> "VIDEO"
    SourceType.RELIEF_WEB -> "RELIEF WEB"
    SourceType.AI_ANALYSIS -> "AI ANALYSIS"
    SourceType.HISTORICAL_RISK -> "HISTORICAL RISK"
}
