package com.example.capstone.presentation.fragment

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.OfflineMapActivity
import com.example.capstone.EmergencyContactsHostActivity
import com.example.capstone.R
import com.example.capstone.data.EmergencyRepository
import com.example.capstone.data.SafeReadyPreferences
import com.example.capstone.data.repository.MeshRepository
import com.example.capstone.location.LocationHelper
import com.example.capstone.presentation.fragment.LiveDisasterDetailFragment
import com.example.capstone.presentation.fragment.LiveDisasterMapFragment
import com.example.capstone.presentation.adapter.MeshDeviceAdapter
import com.example.capstone.presentation.adapter.MeshMessageAdapter
import com.example.capstone.presentation.viewmodel.LiveDisasterViewModel
import com.example.capstone.presentation.viewmodel.LoadState
import com.example.capstone.presentation.viewmodel.MeshViewModel
import com.example.capstone.service.EmergencyService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EmergencyFragment : Fragment() {
    private lateinit var meshViewModel: MeshViewModel
    private lateinit var liveDisasterViewModel: LiveDisasterViewModel
    private lateinit var emergencyRepository: EmergencyRepository
    private lateinit var prefs: SafeReadyPreferences
    private lateinit var meshAdapter: MeshMessageAdapter
    private lateinit var deviceAdapter: MeshDeviceAdapter
    private var selectedStatus: String? = null
    private var nearbyEventId: String? = null
    private var connectivityManager: ConnectivityManager? = null
    private var cameraManager: CameraManager? = null
    private var torchEnabled = false
    private var torchCameraId: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshNetworkStatus()
        }

        override fun onLost(network: Network) {
            refreshNetworkStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshNetworkStatus()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(requireContext(), R.string.emergency_permissions_granted, Toast.LENGTH_SHORT).show()
            refreshNearbyAlert()
        } else {
            Toast.makeText(requireContext(), R.string.emergency_permissions_required, Toast.LENGTH_LONG).show()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                val batteryPct = (level / scale.toFloat() * 100).toInt()
                view?.findViewById<TextView>(R.id.batteryStatusValue)?.text = "$batteryPct%"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_emergency, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkAndRequestPermissions()

        meshViewModel = ViewModelProvider(requireActivity())[MeshViewModel::class.java]
        liveDisasterViewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[LiveDisasterViewModel::class.java]
        prefs = SafeReadyPreferences(requireContext())
        emergencyRepository = EmergencyRepository(requireContext(), prefs, MeshRepository(requireContext()))
        prefs.setEmergencyModeEnabled(true)
        connectivityManager = requireContext().getSystemService(ConnectivityManager::class.java)
        cameraManager = requireContext().getSystemService(CameraManager::class.java)

        setupUi(view)
        observeViewModel(view)
        
        refreshNetworkStatus()
        view.findViewById<TextView>(R.id.gpsStatusValue)?.text = if (LocationHelper.hasLocationPermission(requireContext())) getString(R.string.emergency_status_on) else getString(R.string.emergency_status_off_short)
        refreshNearbyAlert()

        updateEmergencyStatus()
        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        runCatching { connectivityManager?.registerDefaultNetworkCallback(networkCallback) }
    }

    private fun setupUi(view: View) {
        val lastAction = view.findViewById<TextView>(R.id.emergencyLastAction)
        val sosBtn = view.findViewById<FrameLayout>(R.id.btnSendSosEmergency)
        val exitBtn = view.findViewById<MaterialButton>(R.id.btnExitEmergency)
        
        val rvNearbyDevices = view.findViewById<RecyclerView>(R.id.rvNearbyDevices)
        val flashlightCard = view.findViewById<MaterialCardView>(R.id.emergencyFlashlightCard)
        val sheltersCard = view.findViewById<MaterialCardView>(R.id.emergencySheltersCard)
        val contactsCard = view.findViewById<MaterialCardView>(R.id.emergencyContactsCard)
        val checklistCard = view.findViewById<MaterialCardView>(R.id.emergencyChecklistCard)
        val liveAlertCard = view.findViewById<MaterialCardView>(R.id.emergencyLiveAlertCard)
        val liveAlertAction = view.findViewById<MaterialButton>(R.id.emergencyLiveAlertAction)
        val btnManualDiscover = view.findViewById<MaterialButton>(R.id.btnManualDiscover)
        val rvMessages = view.findViewById<RecyclerView>(R.id.rvEmergencyMessages)
        
        deviceAdapter = MeshDeviceAdapter()
        rvNearbyDevices.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = deviceAdapter
        }

        meshAdapter = MeshMessageAdapter()
        rvMessages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = meshAdapter
        }

        // Status cards
        val cardSafe = view.findViewById<MaterialCardView>(R.id.cardStatusSafe)
        val cardInjured = view.findViewById<MaterialCardView>(R.id.cardStatusInjured)
        val cardTrapped = view.findViewById<MaterialCardView>(R.id.cardStatusTrapped)
        val cardFood = view.findViewById<MaterialCardView>(R.id.cardStatusFood)
        val cardMedical = view.findViewById<MaterialCardView>(R.id.cardStatusMedical)
        val cardWater = view.findViewById<MaterialCardView>(R.id.cardStatusWater)

        val statusCards = listOf(cardSafe, cardInjured, cardTrapped, cardFood, cardMedical, cardWater)

        fun selectStatus(card: MaterialCardView, status: String) {
            statusCards.forEach { 
                it.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_card_emergency))
                it.strokeWidth = 1
            }
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_navy_600))
            card.strokeWidth = 2
            selectedStatus = status
        }

        cardSafe.setOnClickListener { selectStatus(cardSafe, getString(R.string.emergency_safe_status)) }
        cardInjured.setOnClickListener { selectStatus(cardInjured, getString(R.string.emergency_injured_status)) }
        cardTrapped.setOnClickListener { selectStatus(cardTrapped, getString(R.string.emergency_trapped_status)) }
        cardFood.setOnClickListener { selectStatus(cardFood, getString(R.string.emergency_need_food)) }
        cardMedical.setOnClickListener { selectStatus(cardMedical, getString(R.string.emergency_need_medical)) }
        cardWater.setOnClickListener { selectStatus(cardWater, getString(R.string.emergency_need_water)) }
        
        flashlightCard.setOnClickListener { toggleFlashlight() }
        sheltersCard.setOnClickListener {
            startActivity(Intent(requireContext(), OfflineMapActivity::class.java))
        }
        contactsCard.setOnClickListener {
            startActivity(Intent(requireContext(), EmergencyContactsHostActivity::class.java))
        }
        checklistCard.setOnClickListener { showChecklistDialog() }
        btnManualDiscover.setOnClickListener {
            // Ensure permissions and show the Discover screen
            checkAndRequestPermissions()
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, com.example.capstone.presentation.fragment.MeshDiscoverFragment())
                .addToBackStack(null)
                .commit()
            meshViewModel.restartMesh()
        }
        liveAlertCard.setOnClickListener { openNearbyLiveAlert() }
        liveAlertAction.setOnClickListener { openNearbyLiveAlert() }

        sosBtn.setOnClickListener {
            if (!prefs.getEmergencyModeEnabled()) {
                Toast.makeText(requireContext(), R.string.emergency_mode_enable_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val statusText = selectedStatus?.takeIf { it.isNotBlank() }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.emergency_sos_confirm_title)
                .setMessage(
                    if (statusText == null) {
                        getString(R.string.emergency_sos_confirm_message_generic)
                    } else {
                        getString(R.string.emergency_sos_confirm_message_status, statusText)
                    }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    emergencyRepository.triggerSos(
                        status = selectedStatus,
                        isAutomatic = false
                    ) { sentCount ->
                        if (sentCount > 0) {
                            lastAction.text = getString(R.string.emergency_sos_sent_count, sentCount)
                        }
                    }
                    Toast.makeText(requireContext(), R.string.emergency_sos_queued, Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        exitBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.emergency_exit_confirm_title)
                .setMessage(R.string.emergency_exit_confirm_message)
                .setNegativeButton(R.string.emergency_exit_confirm_no, null)
                .setPositiveButton(R.string.emergency_exit_confirm_yes) { _, _ ->
                    prefs.setEmergencyModeEnabled(false)
                    requireContext().stopService(Intent(requireContext(), EmergencyService::class.java))
                    // Navigate back or close fragment
                    parentFragmentManager.popBackStack()
                }
                .show()
        }

        val pulse = AnimationUtils.loadAnimation(requireContext(), R.anim.sos_pulse)
        sosBtn.startAnimation(pulse)
    }

    private fun observeViewModel(view: View) {
        val lastAction = view.findViewById<TextView>(R.id.emergencyLastAction)
        val meshStatusValue = view.findViewById<TextView>(R.id.meshStatusValue)
        val meshConnectedCountText = view.findViewById<TextView>(R.id.meshConnectedCountText)
        val liveBadge = view.findViewById<TextView>(R.id.emergencyLiveAlertBadge)
        val liveBody = view.findViewById<TextView>(R.id.emergencyLiveAlertBody)
        val liveMeta = view.findViewById<TextView>(R.id.emergencyLiveAlertMeta)

        viewLifecycleOwner.lifecycleScope.launch {
            meshViewModel.actionMessage.collectLatest { m ->
                if (!m.isNullOrBlank()) {
                    lastAction.text = m
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            meshViewModel.telemetry.collectLatest { t ->
                meshStatusValue.text = t.sent.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            meshViewModel.connectionState.collectLatest { state ->
                meshConnectedCountText.text = getString(R.string.emergency_mesh_connected_count, state.connectedDevices)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            meshViewModel.messages.collectLatest { messages ->
                meshAdapter.submitList(messages)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            meshViewModel.nearbyDevices.collectLatest { devices ->
                deviceAdapter.submitList(devices)
            }
        }

        liveDisasterViewModel.nearbyAlertSummary.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoadState.Loading -> {
                    liveBadge.text = getString(R.string.home_live_status_monitoring)
                    liveBody.text = getString(R.string.live_map_loading)
                    liveMeta.text = ""
                    nearbyEventId = null
                }
                is LoadState.Success -> {
                    val summary = state.data
                    nearbyEventId = summary.nearbyEvents.firstOrNull()?.eventId
                    liveBadge.text = summary.status.toFriendlyStatus()
                    liveBody.text = summary.detail
                    liveMeta.text = summary.locationLabel ?: summary.nearbyEvents.firstOrNull()?.location.orEmpty()
                }
                is LoadState.Empty -> {
                    nearbyEventId = null
                    liveBadge.text = getString(R.string.home_live_status_safe)
                    liveBody.text = state.message
                    liveMeta.text = ""
                }
                is LoadState.Error -> {
                    nearbyEventId = null
                    liveBadge.text = getString(R.string.home_live_status_monitoring)
                    liveBody.text = state.message
                    liveMeta.text = ""
                }
                else -> {
                    nearbyEventId = null
                    liveBadge.text = getString(R.string.home_live_status_monitoring)
                    liveBody.text = getString(R.string.home_live_map_hint)
                    liveMeta.text = ""
                }
            }
        }
    }

    private fun refreshNearbyAlert() {
        if (!LocationHelper.hasLocationPermission(requireContext())) {
            showNearbyAlertUnavailable()
            return
        }
        LocationHelper.fetchLastKnownLocation(requireContext()) { location ->
            if (location == null) {
                activity?.runOnUiThread { showNearbyAlertUnavailable() }
            } else {
                activity?.runOnUiThread {
                    liveDisasterViewModel.loadNearby(location.latitude, location.longitude)
                }
            }
        }
    }

    private fun showNearbyAlertUnavailable() {
        view?.post {
            view?.findViewById<TextView>(R.id.emergencyLiveAlertBadge)?.text = getString(R.string.home_live_status_monitoring)
            view?.findViewById<TextView>(R.id.emergencyLiveAlertBody)?.text = getString(R.string.home_live_status_unavailable)
            view?.findViewById<TextView>(R.id.emergencyLiveAlertMeta)?.text = getString(R.string.home_live_map_hint)
        }
        nearbyEventId = null
    }

    private fun openNearbyLiveAlert() {
        val eventId = nearbyEventId
        if (!eventId.isNullOrBlank()) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, LiveDisasterDetailFragment.newInstance(eventId))
                .addToBackStack(null)
                .commit()
        } else {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, LiveDisasterMapFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun updateEmergencyStatus() {
        val enabled = prefs.getEmergencyModeEnabled()
        view?.findViewById<FrameLayout>(R.id.btnSendSosEmergency)?.isEnabled = enabled
        
        if (enabled) {
            requireContext().startService(Intent(requireContext(), EmergencyService::class.java))
            meshViewModel.start(prefs.getUserProfile().name)
        } else {
            requireContext().stopService(Intent(requireContext(), EmergencyService::class.java))
            meshViewModel.stop()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun refreshNetworkStatus() {
        val statusView = view?.findViewById<TextView>(R.id.networkStatusValue) ?: return
        val cm = connectivityManager ?: return
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val label = when {
            network == null || capabilities == null -> getString(R.string.status_offline)
            else -> {
                val transport = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                    else -> getString(android.R.string.ok)
                }
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) transport else "$transport (no internet)"
            }
        }
        activity?.runOnUiThread {
            statusView.text = label
        }
    }

    private fun toggleFlashlight() {
        val cm = cameraManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(requireContext(), R.string.emergency_flashlight_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        val cameraId = cm.cameraIdList.firstOrNull { id ->
            val characteristics = cm.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        if (cameraId == null) {
            Toast.makeText(requireContext(), R.string.emergency_flashlight_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            torchEnabled = !torchEnabled
            torchCameraId = cameraId
            cm.setTorchMode(cameraId, torchEnabled)
            Toast.makeText(requireContext(), if (torchEnabled) getString(R.string.emergency_flashlight_on) else getString(R.string.emergency_flashlight_off), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            torchEnabled = false
            Log.e("EmergencyFragment", "Flashlight toggle failed", e)
            Toast.makeText(requireContext(), R.string.emergency_flashlight_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChecklistDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.emergency_checklist_title)
            .setMessage(R.string.emergency_checklist_content)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        runCatching { torchCameraId?.let { cameraManager?.setTorchMode(it, false) } }
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
    }
}

private fun String.toFriendlyStatus(): String {
    return when (uppercase()) {
        "SAFE" -> "SAFE"
        "MONITORING", "WARNING_MONITORING", "NO_ACTIVE_EVENT" -> "MONITORING"
        "AT_RISK" -> "AT RISK"
        "AFFECTED" -> "AFFECTED"
        "EMERGENCY" -> "EMERGENCY"
        else -> "MONITORING"
    }
}
