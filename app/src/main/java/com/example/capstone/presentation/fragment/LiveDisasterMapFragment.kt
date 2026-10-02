package com.example.capstone.presentation.fragment

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.R
import com.example.capstone.data.disaster.AlertLevel
import com.example.capstone.data.disaster.DisasterEvent
import com.example.capstone.data.disaster.DisasterSourceStatus
import com.example.capstone.presentation.adapter.LiveEventAdapter
import com.example.capstone.presentation.viewmodel.LiveDisasterViewModel
import com.example.capstone.presentation.viewmodel.LoadState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker

class LiveDisasterMapFragment : Fragment() {
    private lateinit var viewModel: LiveDisasterViewModel
    private lateinit var mapView: MapView
    private lateinit var eventAdapter: LiveEventAdapter
    private lateinit var eventsRecycler: RecyclerView
    private lateinit var loadingView: TextView
    private lateinit var errorView: TextView
    private lateinit var emptyView: TextView
    private lateinit var sourceStatusContainer: LinearLayout
    private val overlay = FolderOverlay()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_live_disaster_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[LiveDisasterViewModel::class.java]

        mapView = view.findViewById(R.id.liveDisasterMap)
        eventsRecycler = view.findViewById(R.id.liveEventsRecycler)
        loadingView = view.findViewById(R.id.liveMapLoading)
        errorView = view.findViewById(R.id.liveMapError)
        emptyView = view.findViewById(R.id.liveMapEmpty)
        sourceStatusContainer = view.findViewById(R.id.sourceStatusContainer)

        setupMap()
        setupList()
        view.findViewById<View>(R.id.liveMapBackButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        observeState()
        viewModel.loadGlobalEvents()
        viewModel.loadSourceStatus()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(2.0)
        mapView.controller.setCenter(GeoPoint(20.0, 0.0))
        mapView.overlays.add(overlay)
    }

    private fun setupList() {
        eventAdapter = LiveEventAdapter { event -> openEventDetail(event.eventId) }
        eventsRecycler.layoutManager = LinearLayoutManager(requireContext())
        eventsRecycler.adapter = eventAdapter
    }

    private fun observeState() {
        viewModel.globalEvents.observe(viewLifecycleOwner) { state ->
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
                    bindEvents(state.data)
                }
                is LoadState.Empty -> {
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = state.message
                    eventAdapter.submitList(emptyList<DisasterEvent>())
                    clearMarkers()
                }
                is LoadState.Error -> {
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                    errorView.text = state.message
                    emptyView.visibility = View.GONE
                    eventAdapter.submitList(emptyList<DisasterEvent>())
                    clearMarkers()
                }
                else -> Unit
            }
        }

        viewModel.sourceStatus.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoadState.Success -> renderSourceStatus(state.data)
                is LoadState.Empty -> renderSourceStatus(emptyList())
                is LoadState.Error -> renderSourceStatus(emptyList(), state.message)
                else -> Unit
            }
        }
    }

    private fun bindEvents(events: List<DisasterEvent>) {
        eventAdapter.submitList(events)
        clearMarkers()
        events.filter { it.latitude != null && it.longitude != null }.forEach { event ->
            addMarker(event)
        }
    }

    private fun clearMarkers() {
        overlay.items.clear()
        mapView.invalidate()
    }

    private fun addMarker(event: DisasterEvent) {
        val marker = Marker(mapView)
        val lat = event.latitude ?: return
        val lon = event.longitude ?: return
        marker.position = GeoPoint(lat, lon)
        marker.title = event.displayTitle()
        marker.snippet = event.location
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = coloredMarkerFor(event.alertLevel)
        marker.setOnMarkerClickListener { _, _ ->
            openEventDetail(event.eventId)
            true
        }
        overlay.add(marker)
    }

    private fun coloredMarkerFor(level: AlertLevel): Drawable? {
        val colorRes = when (level) {
            AlertLevel.AFFECTED -> R.color.status_danger
            AlertLevel.AT_RISK -> R.color.status_warning
            AlertLevel.WARNING_MONITORING -> R.color.status_warning
            else -> R.color.status_success
        }
        return ContextCompat.getDrawable(requireContext(), R.drawable.ic_alert_circle)?.mutate()?.apply {
            setTint(ContextCompat.getColor(requireContext(), colorRes))
        }
    }

    private fun renderSourceStatus(statuses: List<DisasterSourceStatus>, errorMessage: String? = null) {
        sourceStatusContainer.removeAllViews()
        if (statuses.isEmpty()) {
            val text = TextView(requireContext())
            text.text = errorMessage ?: getString(R.string.evidence_empty)
            text.setTextColor(ContextCompat.getColor(requireContext(), R.color.sr_text_secondary))
            sourceStatusContainer.addView(text)
            return
        }

        statuses.forEach { status ->
            val chip = TextView(requireContext())
            chip.text = "${status.source}: ${status.status}"
            chip.setPadding(32, 16, 32, 16)
            chip.setTextColor(ContextCompat.getColor(requireContext(), if (status.configured) R.color.sr_text_primary else R.color.status_danger))
            chip.setBackgroundResource(R.drawable.bg_risk_badge)
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginEnd = 16
            chip.layoutParams = params
            sourceStatusContainer.addView(chip)
        }
    }

    private fun openEventDetail(eventId: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, LiveDisasterDetailFragment.newInstance(eventId))
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
