package com.example.capstone.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.R
import com.example.capstone.data.disaster.DisasterEvidence
import com.example.capstone.data.disaster.DisasterEvent
import com.example.capstone.presentation.adapter.LiveEvidenceAdapter
import com.example.capstone.presentation.viewmodel.LiveDisasterViewModel
import com.example.capstone.presentation.viewmodel.LoadState

class LiveDisasterEvidenceFragment : Fragment() {
    private lateinit var viewModel: LiveDisasterViewModel
    private lateinit var adapter: LiveEvidenceAdapter
    private lateinit var loadingView: TextView
    private lateinit var errorView: TextView
    private lateinit var emptyView: TextView
    private lateinit var summaryView: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_live_disaster_evidence, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[LiveDisasterViewModel::class.java]

        val eventId = requireArguments().getString(ARG_EVENT_ID).orEmpty()
        loadingView = view.findViewById(R.id.evidenceLoading)
        errorView = view.findViewById(R.id.evidenceError)
        emptyView = view.findViewById(R.id.evidenceEmpty)
        summaryView = view.findViewById(R.id.evidenceEventSummary)
        recyclerView = view.findViewById(R.id.evidenceRecycler)

        view.findViewById<View>(R.id.evidenceBackButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        adapter = LiveEvidenceAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        if (eventId.isBlank()) {
            showError(getString(R.string.evidence_empty))
            return
        }

        viewModel.event.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoadState.Success -> summaryView.text = buildSummary(state.data)
                is LoadState.Empty -> summaryView.text = state.message
                is LoadState.Error -> summaryView.text = state.message
                else -> Unit
            }
        }

        viewModel.evidence.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoadState.Loading -> {
                    loadingView.visibility = View.VISIBLE
                    errorView.visibility = View.GONE
                    emptyView.visibility = View.GONE
                }
                is LoadState.Success -> {
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.GONE
                    emptyView.visibility = View.GONE
                    adapter.submitList(state.data)
                }
                is LoadState.Empty -> {
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = state.message
                    adapter.submitList(emptyList<DisasterEvidence>())
                }
                is LoadState.Error -> {
                    loadingView.visibility = View.GONE
                    emptyView.visibility = View.GONE
                    showError(state.message)
                    adapter.submitList(emptyList<DisasterEvidence>())
                }
                else -> Unit
            }
        }

        view.findViewById<TextView>(R.id.evidenceScreenTitle).text = getString(R.string.live_evidence_title)
        viewModel.loadEvent(eventId)
        viewModel.loadEvidence(eventId)
    }

    private fun buildSummary(event: DisasterEvent): String {
        val location = listOfNotNull(event.city, event.district, event.state, event.country).joinToString(", ")
        return if (location.isBlank()) {
            event.displayTitle()
        } else {
            "${event.displayTitle()} • $location"
        }
    }

    private fun showError(message: String) {
        errorView.visibility = View.VISIBLE
        errorView.text = message
    }

    companion object {
        private const val ARG_EVENT_ID = "arg_event_id"

        fun newInstance(eventId: String): LiveDisasterEvidenceFragment {
            return LiveDisasterEvidenceFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EVENT_ID, eventId)
                }
            }
        }
    }
}
