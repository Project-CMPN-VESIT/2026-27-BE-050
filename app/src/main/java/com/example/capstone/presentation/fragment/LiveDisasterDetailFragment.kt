package com.example.capstone.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.capstone.R
import com.example.capstone.data.disaster.AlertLevel
import com.example.capstone.data.disaster.DisasterEvent
import com.example.capstone.presentation.viewmodel.LiveDisasterViewModel
import com.example.capstone.presentation.viewmodel.LoadState
import java.text.DateFormat
import java.util.Date

class LiveDisasterDetailFragment : Fragment() {
    private lateinit var viewModel: LiveDisasterViewModel
    private lateinit var loadingView: TextView
    private lateinit var errorView: TextView
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var locationView: TextView
    private lateinit var severityView: TextView
    private lateinit var timeView: TextView
    private lateinit var coordinatesView: TextView
    private lateinit var affectedAreaView: TextView
    private lateinit var sourceView: TextView
    private lateinit var confidenceView: TextView
    private lateinit var whyView: TextView
    private lateinit var evidenceButton: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_live_disaster_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[LiveDisasterViewModel::class.java]

        val eventId = requireArguments().getString(ARG_EVENT_ID).orEmpty()
        loadingView = view.findViewById(R.id.detailLoading)
        errorView = view.findViewById(R.id.detailError)
        titleView = view.findViewById(R.id.detailScreenTitle)
        statusView = view.findViewById(R.id.detailStatus)
        locationView = view.findViewById(R.id.detailLocation)
        severityView = view.findViewById(R.id.detailSeverity)
        timeView = view.findViewById(R.id.detailTime)
        coordinatesView = view.findViewById(R.id.detailCoordinates)
        affectedAreaView = view.findViewById(R.id.detailAffectedArea)
        sourceView = view.findViewById(R.id.detailSource)
        confidenceView = view.findViewById(R.id.detailConfidence)
        whyView = view.findViewById(R.id.detailWhy)
        evidenceButton = view.findViewById(R.id.detailViewEvidence)

        view.findViewById<View>(R.id.detailBackButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        evidenceButton.setOnClickListener {
            if (eventId.isNotBlank()) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.mainFragmentContainer, LiveDisasterEvidenceFragment.newInstance(eventId))
                    .addToBackStack(null)
                    .commit()
            }
        }

        if (eventId.isBlank()) {
            showError(getString(R.string.evidence_empty))
            return
        }

        viewModel.event.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoadState.Loading -> {
                    loadingView.visibility = View.VISIBLE
                    errorView.visibility = View.GONE
                    evidenceButton.isEnabled = false
                }
                is LoadState.Success -> {
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.GONE
                    evidenceButton.isEnabled = true
                    bindEvent(state.data)
                }
                is LoadState.Empty -> {
                    loadingView.visibility = View.GONE
                    evidenceButton.isEnabled = false
                    showError(state.message)
                }
                is LoadState.Error -> {
                    loadingView.visibility = View.GONE
                    evidenceButton.isEnabled = false
                    showError(state.message)
                }
                else -> Unit
            }
        }

        titleView.text = getString(R.string.live_detail_title)
        viewModel.loadEvent(eventId)
    }

    private fun bindEvent(event: DisasterEvent) {
        titleView.text = event.displayTitle()
        statusView.text = event.alertLevel.name.replace('_', ' ')
        locationView.text = buildLocationText(event)
        severityView.text = getString(R.string.live_detail_severity_format, event.severity)
        timeView.text = getString(R.string.live_detail_time_format, formatTime(event.lastUpdated))
        coordinatesView.text = if (event.latitude != null && event.longitude != null) {
            getString(R.string.live_detail_coordinates_format, event.latitude, event.longitude)
        } else {
            getString(R.string.live_detail_coordinates_missing)
        }
        affectedAreaView.text = buildAffectedAreaText(event)
        sourceView.text = getString(R.string.live_detail_source_format, event.sourceSummary)
        confidenceView.text = getString(R.string.live_detail_confidence_format, (event.evidenceConfidence * 100).toInt())
        whyView.text = buildWhyText(event)

        statusView.setTextColor(
            requireContext().getColor(
                when (event.alertLevel) {
                    AlertLevel.AFFECTED -> R.color.status_danger
                    AlertLevel.AT_RISK -> R.color.status_warning
                    AlertLevel.WARNING_MONITORING -> R.color.status_warning
                    else -> R.color.status_success
                }
            )
        )
    }

    private fun buildLocationText(event: DisasterEvent): String {
        val parts = listOfNotNull(event.city, event.district, event.state, event.country)
        return if (parts.isEmpty()) {
            getString(R.string.live_detail_location_missing)
        } else {
            getString(R.string.live_detail_location_format, parts.joinToString(", "))
        }
    }

    private fun buildAffectedAreaText(event: DisasterEvent): String {
        val parts = listOfNotNull(event.district, event.state, event.country)
        return if (parts.isEmpty()) {
            getString(R.string.live_detail_affected_area_missing)
        } else {
            getString(R.string.live_detail_affected_area_format, parts.joinToString(", "))
        }
    }

    private fun buildWhyText(event: DisasterEvent): String {
        val status = event.alertLevel.name.replace('_', ' ')
        val confidence = (event.evidenceConfidence * 100).toInt()
        val sources = event.sourceSummary.ifBlank { getString(R.string.live_detail_source_missing) }
        return "Status: $status\nEvidence confidence: $confidence%\nSources: $sources\n\nThis alert is shown because the backend matched real live evidence for this event."
    }

    private fun formatTime(time: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
    }

    private fun showError(message: String) {
        errorView.visibility = View.VISIBLE
        errorView.text = message
    }

    companion object {
        private const val ARG_EVENT_ID = "arg_event_id"

        fun newInstance(eventId: String): LiveDisasterDetailFragment {
            return LiveDisasterDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EVENT_ID, eventId)
                }
            }
        }
    }
}
