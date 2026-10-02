package com.example.capstone.presentation

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import org.osmdroid.config.Configuration
import android.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.capstone.DisasterDetailActivity
import com.example.capstone.MainActivity
import com.example.capstone.LoginActivity
import com.example.capstone.R
import com.example.capstone.presentation.fragment.EmergencyFragment
import com.google.android.material.button.MaterialButton
import com.example.capstone.presentation.viewmodel.MeshViewModel
import com.example.capstone.presentation.viewmodel.RiskViewModel
import com.example.capstone.data.MeshMessage
import com.example.capstone.data.MeshMessageType

import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.example.capstone.location.LocationHelper
import com.example.capstone.data.MedReadyScanResult
import com.example.capstone.data.SafeReadyPreferences
import com.example.capstone.data.remote.groq.GroqVisionDataSource
import com.example.capstone.data.repository.MedReadyRepository
import com.example.capstone.presentation.fragment.LiveDisasterDetailFragment
import com.example.capstone.presentation.fragment.LiveDisasterMapFragment
import com.example.capstone.presentation.viewmodel.MedReadyViewModel
import com.example.capstone.presentation.viewmodel.MedReadyViewModelFactory
import com.example.capstone.presentation.viewmodel.LiveDisasterViewModel
import com.example.capstone.presentation.viewmodel.LoadState
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {
    private lateinit var viewModel: HomeViewModel
    private lateinit var meshViewModel: MeshViewModel
    private lateinit var liveDisasterViewModel: LiveDisasterViewModel

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                viewModel.resolveLocation()
                refreshLiveStatus()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                viewModel.resolveLocation()
                refreshLiveStatus()
            }
            else -> {
                showLiveStatusUnavailable()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home_modern, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[HomeViewModel::class.java]
        meshViewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[MeshViewModel::class.java]
        liveDisasterViewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[LiveDisasterViewModel::class.java]

        if (!LocationHelper.hasLocationPermission(requireContext())) {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            viewModel.resolveLocation()
        }

        val greeting = view.findViewById<TextView>(R.id.homeGreeting)
        val name = view.findViewById<TextView>(R.id.homeUserName)
        val region = view.findViewById<TextView>(R.id.homeLocation)
        val progressPercent = view.findViewById<TextView>(R.id.homeProgressPercent)
        val statusBadge = view.findViewById<TextView>(R.id.homeStatusBadge)
        val progressBar = view.findViewById<ProgressBar>(R.id.homeProgressBar)
        val emergencyBadge = view.findViewById<TextView>(R.id.homeEmergencyBadge)
        val recommendationBody = view.findViewById<TextView>(R.id.homeRecommendationBody)
        val recommendationCard = view.findViewById<CardView>(R.id.preparednessCard)
        val gamificationText = view.findViewById<TextView>(R.id.homeGamificationText)
        val weatherChip = view.findViewById<com.google.android.material.chip.Chip>(R.id.weatherChip)
        val medReadyBadge = view.findViewById<TextView>(R.id.homeMedReadyBadge)

        view.findViewById<CardView>(R.id.homeStartTrainingButton)?.setOnClickListener {
            (activity as? MainActivity)?.selectTab(R.id.nav_lab)
        }

        val openLiveMap = {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, LiveDisasterMapFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.disasterMapCard)?.setOnClickListener { openLiveMap() }
        view.findViewById<View>(R.id.homeMapNavigateButton)?.setOnClickListener { openLiveMap() }
        view.findViewById<View>(R.id.expandMapButton)?.setOnClickListener { openLiveMap() }
        view.findViewById<View>(R.id.homeLiveMapButton)?.setOnClickListener { openLiveMap() }

        view.findViewById<CardView>(R.id.assistantLaunchCard)?.setOnClickListener {
            (activity as? MainActivity)?.openAssistant()
        }
        view.findViewById<CardView>(R.id.homeSendSosButton)?.setOnClickListener {
            (activity as? MainActivity)?.selectTab(R.id.nav_home)
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, EmergencyFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.riskViewButton)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, RiskAssessmentFragment())
                .addToBackStack(null)
                .commit()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            greeting.text = greetingText()
            name.text = state.user.name.ifBlank { getString(R.string.home_user_default) }
            region.text = state.regionLabel
            progressPercent.text = getString(R.string.percentage_format, state.overallProgress)
            progressBar.progress = state.overallProgress
            val recommendation = state.recommendation ?: state.recommendedModule?.let {
                com.example.capstone.data.RecommendationCard(
                    title = it.title,
                    reason = it.summary,
                    ctaLabel = getString(R.string.quiz_next),
                    disasterKey = it.key,
                    chapterIndex = 0,
                )
            }
            recommendationBody.text = if (recommendation == null) {
                getString(R.string.home_recommendation_default)
            } else {
                "${recommendation.title}: ${recommendation.reason}\n${recommendation.ctaLabel}"
            }
            gamificationText.text = state.gamification.progressText
            statusBadge.text = when {
                state.overallProgress >= 80 -> getString(R.string.home_status_ready_to_review)
                state.overallProgress >= 1 -> getString(R.string.home_status_keep_going)
                else -> getString(R.string.home_status_start_here)
            }
            emergencyBadge.text = if (state.isEmergencyModeEnabled) {
                getString(R.string.home_emergency_mode_on)
            } else {
                getString(R.string.home_emergency_mode_off)
            }
            weatherChip.text = state.weather
            
            // Log for debugging
            android.util.Log.d("HomeFragment", "Risk Level: ${state.riskLevel}, Desc: ${state.riskDescription}")

            medReadyBadge?.visibility = if (state.medReadyReadiness >= 0) View.VISIBLE else View.GONE
            medReadyBadge?.text = if (state.medReadyReadiness >= 0) {
                getString(R.string.home_medready_ready, state.medReadyReadiness)
            } else {
                getString(R.string.home_medready_not_scanned)
            }

            val riskHeading = view.findViewById<TextView>(R.id.homeRiskHeading)
            val riskBody = view.findViewById<TextView>(R.id.homeRiskBody)
            riskHeading?.text = state.riskLevel
            riskBody?.text = state.riskDescription

            recommendationCard.setOnClickListener {
                val disasterKey = recommendation?.disasterKey ?: state.recommendedModule?.key
                if (!disasterKey.isNullOrBlank()) {
                    startActivity(DisasterDetailActivity.newIntent(requireActivity() as AppCompatActivity, disasterKey))
                } else {
                    (activity as? MainActivity)?.selectTab(R.id.nav_lab)
                }
            }

            recommendationBody.setOnClickListener { recommendationCard.performClick() }
        }

        val liveBadge = view.findViewById<TextView>(R.id.homeLiveStatusBadge)
        val liveBody = view.findViewById<TextView>(R.id.homeLiveStatusBody)
        val liveMeta = view.findViewById<TextView>(R.id.homeLiveStatusMeta)
        liveDisasterViewModel.nearbyAlertSummary.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoadState.Loading -> {
                    liveBadge.text = getString(R.string.home_live_status_monitoring)
                    liveBody.text = getString(R.string.live_map_loading)
                    liveMeta.text = ""
                }
                is LoadState.Success -> {
                    val summary = state.data
                    liveBadge.text = summary.status.toFriendlyStatus()
                    liveBody.text = summary.detail
                    liveMeta.text = summary.locationLabel ?: summary.nearbyEvents.firstOrNull()?.location.orEmpty()
                    view.findViewById<View>(R.id.homeLiveMapButton)?.isEnabled = true
                }
                is LoadState.Empty -> {
                    liveBadge.text = getString(R.string.home_live_status_safe)
                    liveBody.text = state.message
                    liveMeta.text = ""
                }
                is LoadState.Error -> {
                    liveBadge.text = getString(R.string.home_live_status_monitoring)
                    liveBody.text = state.message
                    liveMeta.text = ""
                    view.findViewById<View>(R.id.homeLiveMapButton)?.isEnabled = true
                }
                else -> {
                    liveBadge.text = getString(R.string.home_live_status_monitoring)
                    liveBody.text = getString(R.string.home_live_map_hint)
                    liveMeta.text = ""
                }
            }
        }
        refreshLiveStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refresh()
        }
        if (::liveDisasterViewModel.isInitialized) {
            refreshLiveStatus()
        }
    }

    private fun refreshLiveStatus() {
        if (!LocationHelper.hasLocationPermission(requireContext())) {
            showLiveStatusUnavailable()
            return
        }
        LocationHelper.fetchLastKnownLocation(requireContext()) { location ->
            if (location == null) {
                activity?.runOnUiThread { showLiveStatusUnavailable() }
            } else {
                activity?.runOnUiThread {
                    liveDisasterViewModel.loadNearby(location.latitude, location.longitude)
                }
            }
        }
    }

    private fun showLiveStatusUnavailable() {
        view?.post {
            view?.findViewById<TextView>(R.id.homeLiveStatusBadge)?.text = getString(R.string.home_live_status_monitoring)
            view?.findViewById<TextView>(R.id.homeLiveStatusBody)?.text = getString(R.string.home_live_status_unavailable)
            view?.findViewById<TextView>(R.id.homeLiveStatusMeta)?.text = getString(R.string.home_live_map_hint)
        }
    }

    private fun greetingText(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> getString(R.string.home_greeting_morning)
            in 12..16 -> getString(R.string.home_greeting_afternoon)
            in 17..20 -> getString(R.string.home_greeting_evening)
            else -> getString(R.string.home_greeting_night)
        }
    }
}

private fun String.toFriendlyStatus(): String {
    return when (uppercase(Locale.US)) {
        "SAFE" -> "SAFE"
        "MONITORING", "WARNING_MONITORING", "NO_ACTIVE_EVENT" -> "MONITORING"
        "AT_RISK" -> "AT RISK"
        "AFFECTED" -> "AFFECTED"
        "EMERGENCY" -> "EMERGENCY"
        else -> "MONITORING"
    }
}

class TrainingFragment : Fragment() {
    private lateinit var viewModel: ProgressViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_lab_modern, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[ProgressViewModel::class.java]

        val survivalLevel = view.findViewById<TextView>(R.id.labSurvivalLevel)
        val quizzesDone = view.findViewById<TextView>(R.id.labQuizzesDone)
        val simulationsDone = view.findViewById<TextView>(R.id.labSimulationsDone)
        val survivalScore = view.findViewById<TextView>(R.id.labSurvivalScore)
        val dailyChallengeCard = view.findViewById<View>(R.id.dailyChallengeCard)
        val learningProgress = view.findViewById<ProgressBar>(R.id.videoLearningProgress)

        viewModel.state.observe(viewLifecycleOwner) { state ->
            val snapshot = state.snapshot
            survivalLevel.text = getString(R.string.lab_level_format, state.gamification.level)
            quizzesDone.text = state.gamification.quizzesCompleted.toString()
            simulationsDone.text = state.gamification.simulationsCompleted.toString()
            survivalScore.text = state.gamification.points.toString()
            learningProgress.progress = snapshot.overallPercent
        }

        dailyChallengeCard.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.lab_daily_challenge_started), Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.cardEarthquake)?.setOnClickListener {
            startDisaster("earthquake")
        }
        view.findViewById<View>(R.id.cardFloods)?.setOnClickListener {
            startDisaster("floods")
        }
        view.findViewById<View>(R.id.cardCyclone)?.setOnClickListener {
            startDisaster("cyclone")
        }
        view.findViewById<View>(R.id.cardLandslides)?.setOnClickListener {
            startDisaster("landslides")
        }

        view.findViewById<View>(R.id.situationalGameCard)?.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.lab_starting_simulation), Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.startQuizButton)?.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.lab_generating_quiz), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDisaster(disasterKey: String) {
        val activity = requireActivity() as AppCompatActivity
        startActivity(DisasterDetailActivity.newIntent(activity, disasterKey))
    }
}

class ProgressFragment : Fragment() {
    private lateinit var viewModel: ProgressViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[ProgressViewModel::class.java]

        val overallPercent = view.findViewById<TextView>(R.id.overallProgressPercent)
        val overallBar = view.findViewById<ProgressBar>(R.id.overallProgressBar)
        val summary = view.findViewById<TextView>(R.id.quizSummaryText)
        val container = view.findViewById<LinearLayout>(R.id.progressListContainer)

        view.findViewById<MaterialButton>(R.id.clearProgressButton).setOnClickListener {
            viewModel.clearProgress()
            Toast.makeText(requireContext(), getString(R.string.progress_cleared), Toast.LENGTH_SHORT).show()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            val snapshot = state.snapshot
            overallPercent.text = getString(R.string.progress_overall_percent_format, snapshot.overallPercent)
            overallBar.progress = snapshot.overallPercent
            summary.text = state.gamification.progressText
            
            // Lab Progress Mapping
            view.findViewById<TextView>(R.id.labLessonsValue)?.text = getString(R.string.lab_lessons_format, state.gamification.lessonsCompleted, snapshot.disasterProgress.sumOf { it.totalChapters })

            container.removeAllViews()
            snapshot.disasterProgress.forEach { progress ->
                container.addView(progressRow(progress))
            }
        }
    }

    private fun progressRow(progress: com.example.capstone.data.DisasterProgress): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_white_card)
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }

        val title = TextView(requireContext()).apply {
            text = progress.title
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.dashboard_name))
        }

        val body = TextView(requireContext()).apply {
            text = getString(R.string.progress_chapters_completed_format, progress.completedChapters, progress.totalChapters)
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.dashboard_greeting))
            setPadding(0, 6, 0, 8)
        }

        val percent = TextView(requireContext()).apply {
            text = getString(R.string.progress_single_percent_format, progress.percent)
            textSize = 18f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.dashboard_name))
        }

        val bar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = progress.percent
        }

        row.addView(title)
        row.addView(body)
        row.addView(percent)
        row.addView(bar)
        return row
    }
}

class AssistantFragment : Fragment() {
    private lateinit var viewModel: AssistantViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_assistant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[AssistantViewModel::class.java]

        val scroll = view.findViewById<ScrollView>(R.id.assistantScroll)
        val chatContainer = view.findViewById<LinearLayout>(R.id.assistantChatContainer)
        val promptsContainer = view.findViewById<LinearLayout>(R.id.assistantPromptsContainer)
        val input = view.findViewById<EditText>(R.id.assistantInput)
        val send = view.findViewById<MaterialButton>(R.id.assistantSendButton)
        val suggestion = view.findViewById<TextView>(R.id.assistantSuggestion)
        val statusBadge = view.findViewById<TextView>(R.id.assistantStatusBadge)
        val loading = view.findViewById<ProgressBar>(R.id.assistantLoadingIndicator)

        fun submitMessage() {
            val text = input.text?.toString().orEmpty().trim()
            if (text.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.assistant_type_question), Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.sendMessage(text)
            input.text?.clear()
        }

        send.setOnClickListener { submitMessage() }

        input.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                submitMessage()
                true
            } else {
                false
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            chatContainer.removeAllViews()
            state.messages.forEach { message ->
                chatContainer.addView(chatBubble(message.text, message.isUser))
            }
            suggestion.text = state.suggestedTopic ?: getString(R.string.assistant_suggestion_default)
            statusBadge.text = state.backendLabel
            loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            send.isEnabled = !state.isLoading
            input.isEnabled = !state.isLoading

            promptsContainer.removeAllViews()
            state.prompts.forEach { prompt ->
                val chip = MaterialButton(requireContext()).apply {
                    text = prompt
                    isAllCaps = false
                    setOnClickListener {
                        input.setText(prompt)
                        viewModel.sendMessage(prompt)
                        input.text?.clear()
                    }
                }
                promptsContainer.addView(chip)
            }

            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun chatBubble(text: String, isUser: Boolean): View {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            setPadding(20, 16, 20, 16)
            textSize = 14f
            setTextColor(if (isUser) ContextCompat.getColor(context, R.color.text_inverse) else ContextCompat.getColor(context, R.color.text_primary))
            setBackgroundResource(if (isUser) R.drawable.bg_chip_active else R.drawable.bg_white_card)
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12
            marginStart = if (isUser) 64 else 0
            marginEnd = if (isUser) 0 else 64
        }
        bubble.layoutParams = params
        return bubble
    }
}

class ProfileFragment : Fragment() {
    private lateinit var viewModel: ProfileViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[ProfileViewModel::class.java]

        val name = view.findViewById<TextView>(R.id.profileName)
        val email = view.findViewById<TextView>(R.id.profileEmail)
        val institution = view.findViewById<TextView>(R.id.profileInstitution)
        val region = view.findViewById<TextView>(R.id.profileRegion)
        val summary = view.findViewById<TextView>(R.id.profileSummaryBody)
        val gamificationText = view.findViewById<TextView>(R.id.profileGamificationText)
        val initial = view.findViewById<TextView>(R.id.profileInitial)
        val levelValue = view.findViewById<TextView>(R.id.stat1Value)
        val streakValue = view.findViewById<TextView>(R.id.stat2Value)
        val pointsValue = view.findViewById<TextView>(R.id.stat3Value)
        val pointsLabel = view.findViewById<TextView>(R.id.stat3Label)
        val signOutItem = view.findViewById<View>(R.id.signOutItem)
        // val themeItem = view.findViewById<View>(R.id.themeItem)
        val currentThemeText = view.findViewById<TextView>(R.id.currentThemeText)
        val emergencyModeItem = view.findViewById<View>(R.id.emergencyModeItem)
        val currentEmergencyModeText = view.findViewById<TextView>(R.id.currentEmergencyModeText)
        val manageContacts = view.findViewById<View>(R.id.manageContactsText)
        val downloadMaps = view.findViewById<View>(R.id.downloadMapsText)
        val editProfileButton = view.findViewById<View>(R.id.editProfileButton)

        val simValue = view.findViewById<TextView>(R.id.simulationsValue)
        val simProgress = view.findViewById<View>(R.id.simulationsProgress)
        val lessonValue = view.findViewById<TextView>(R.id.lessonsValue)
        val lessonProgress = view.findViewById<View>(R.id.lessonsProgress)
        val drillValue = view.findViewById<TextView>(R.id.drillsValue)
        val drillProgress = view.findViewById<View>(R.id.drillsProgress)
        val contactsContainer = view.findViewById<LinearLayout>(R.id.emergencyContactsContainer)

        /* Theme selection removed - Light mode only
        themeItem?.setOnClickListener {
            val themes = arrayOf(
                getString(R.string.profile_theme_auto),
                getString(R.string.profile_theme_light),
                getString(R.string.profile_theme_dark)
            )
            val currentMode = viewModel.state.value?.themeMode ?: 0
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.profile_select_theme))
                .setSingleChoiceItems(themes, currentMode) { dialog, which ->
                    viewModel.setThemeMode(which)
                    updateTheme(which)
                    dialog.dismiss()
                }
                .show()
        }
        */

        emergencyModeItem?.setOnClickListener {
            val enabled = viewModel.toggleEmergencyMode()
            currentEmergencyModeText.text = if (enabled) getString(R.string.emergency_status_on) else getString(R.string.emergency_status_off_short)
            Toast.makeText(requireContext(), if (enabled) getString(R.string.profile_emergency_mode_enabled) else getString(R.string.profile_emergency_mode_disabled), Toast.LENGTH_SHORT).show()
        }

        manageContacts?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, EmergencyContactsFragment())
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        }

        editProfileButton?.setOnClickListener {
            showEditProfileDialog()
        }

        downloadMaps?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, LiveDisasterMapFragment())
                .addToBackStack(null)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit()
        }

        signOutItem?.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.signup_footer_action))
                .setMessage(getString(R.string.profile_sign_out_confirm))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.signup_footer_action)) { _, _ ->
                    viewModel.logOut()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finishAffinity()
                }
                .show()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            val profile = state.profile
            name.text = profile.name.ifBlank { getString(R.string.home_user_default) }
            initial.text = profile.name.take(1).uppercase()
            email.text = if (profile.email.isBlank()) getString(R.string.progress_email_not_saved) else profile.email
            institution.text = if (profile.institution.isBlank()) getString(R.string.progress_institution_not_saved) else profile.institution
            region.text = when {
                !profile.city.isNullOrBlank() && !profile.state.isNullOrBlank() -> getString(R.string.progress_region_city_state_format, profile.city, profile.state)
                !profile.state.isNullOrBlank() -> getString(R.string.progress_region_format, profile.state)
                !profile.city.isNullOrBlank() -> getString(R.string.progress_region_format, profile.city)
                else -> getString(R.string.progress_region_not_available)
            }
            summary.text = getString(R.string.progress_summary_format, state.completedDisasters.size)
            gamificationText.text = state.gamification.progressText
            levelValue.text = state.gamification.level.toString()
            streakValue.text = state.gamification.currentStreak.toString()
            pointsValue.text = state.gamification.points.toString()
            pointsLabel.text = getString(R.string.progress_points)

            // Update Journey Progress
            simValue?.text = "${state.simulationsCompleted}/${state.totalSimulations}"
            updateProgressWidth(simProgress, state.simulationsCompleted, state.totalSimulations)
            
            lessonValue?.text = "${state.lessonsCompleted}/${state.totalLessons}"
            updateProgressWidth(lessonProgress, state.lessonsCompleted, state.totalLessons)
            
            drillValue?.text = "${state.drillsCompleted}/${state.totalDrills}"
            updateProgressWidth(drillProgress, state.drillsCompleted, state.totalDrills)

            currentThemeText?.text = when(state.themeMode) {
                1 -> "Light"
                2 -> "Dark"
                else -> "Auto"
            }
            currentEmergencyModeText?.text = if (state.isEmergencyModeEnabled) "On" else "Off"

            // Update Emergency Contacts
            contactsContainer?.removeAllViews()
            if (state.emergencyContacts.isEmpty()) {
                val emptyText = TextView(requireContext()).apply {
                    text = getString(R.string.progress_no_contacts)
                    setPadding(16, 16, 16, 16)
                    setTextColor(ContextCompat.getColor(context, R.color.sr_text_secondary))
                }
                contactsContainer?.addView(emptyText)
            } else {
                state.emergencyContacts.take(3).forEach { contact ->
                    contactsContainer?.addView(createContactView(contact))
                }
            }
            }
        }

        override fun onResume() {
            super.onResume()
            if (::viewModel.isInitialized) {
                viewModel.refresh()
        }
        }

        private fun showEditProfileDialog() {
            val state = viewModel.state.value ?: return
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
            val nameInput = dialogView.findViewById<EditText>(R.id.profileNameInput)
            val emailInput = dialogView.findViewById<EditText>(R.id.profileEmailInput)
            val institutionInput = dialogView.findViewById<EditText>(R.id.profileInstitutionInput)
            val cityInput = dialogView.findViewById<EditText>(R.id.profileCityInput)
            val stateInput = dialogView.findViewById<EditText>(R.id.profileStateInput)

            nameInput.setText(state.profile.name)
            emailInput.setText(state.profile.email)
            institutionInput.setText(state.profile.institution)
            cityInput.setText(state.profile.city.orEmpty())
            stateInput.setText(state.profile.state.orEmpty())

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Profile")
                .setView(dialogView)
                .setPositiveButton("Save") { _, _ ->
                    viewModel.updateProfile(
                        name = nameInput.text?.toString().orEmpty().trim().ifBlank { "User" },
                        email = emailInput.text?.toString().orEmpty().trim(),
                        institution = institutionInput.text?.toString().orEmpty().trim(),
                        city = cityInput.text?.toString().orEmpty().trim().ifBlank { null },
                        state = stateInput.text?.toString().orEmpty().trim().ifBlank { null },
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

    private fun updateProgressWidth(view: View?, completed: Int, total: Int) {
        if (view == null || total == 0) return
        val params = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.matchConstraintPercentWidth = (completed.toFloat() / total).coerceIn(0f, 1f)
        view.layoutParams = params
    }

    private fun updateTheme(mode: Int) {
        // Light mode only
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun createContactView(contact: com.example.capstone.data.EmergencyContact): View {
        val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.sr_bg_card))
            radius = 12f * resources.displayMetrics.density
            cardElevation = 2f * resources.displayMetrics.density
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
        }

        val icon = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * resources.displayMetrics.density).toInt(),
                (40 * resources.displayMetrics.density).toInt()
            ).apply { marginEnd = (12 * resources.displayMetrics.density).toInt() }
            setBackgroundColor(ContextCompat.getColor(context, R.color.color_gray_200))
        }

        val info = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val name = TextView(requireContext()).apply {
            text = contact.name
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.sr_text_primary))
            textSize = 14f
        }

        val phone = TextView(requireContext()).apply {
            text = contact.phone
            setTextColor(ContextCompat.getColor(context, R.color.sr_text_secondary))
            textSize = 12f
        }

        info.addView(name)
        info.addView(phone)
        layout.addView(icon)
        layout.addView(info)
        card.addView(layout)
        return card
    }
}

class MedReadyFragment : Fragment() {
    private lateinit var viewModel: MedReadyViewModel

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let { processImage(it) }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                processImage(bitmap)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_medready, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val repository = MedReadyRepository(GroqVisionDataSource(), SafeReadyPreferences(requireContext()))
        viewModel = ViewModelProvider(this, MedReadyViewModelFactory(repository))[MedReadyViewModel::class.java]

        val title = view.findViewById<TextView>(R.id.medreadyTitle)
        title?.text = getString(R.string.medready_title)

        view.findViewById<View>(R.id.medreadyTakePhotoButton)?.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePhotoLauncher.launch(intent)
        }

        view.findViewById<View>(R.id.medreadyUploadButton)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        val historyContainer = view.findViewById<LinearLayout>(R.id.medreadyHistoryContainer)
        val progressBar = view.findViewById<ProgressBar>(R.id.medreadyProgressBar)

        viewModel.scanHistory.observe(viewLifecycleOwner) { history ->
            historyContainer?.let { updateHistoryUI(it, history) }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                showScanResultDialog(it)
                viewModel.clearResult()
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val stream = ByteArrayOutputStream()
        // Compress and resize image to prevent payload errors
        val scaledBitmap = if (bitmap.width > 1024 || bitmap.height > 1024) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth = if (ratio > 1) 1024 else (1024 * ratio).toInt()
            val newHeight = if (ratio > 1) (1024 / ratio).toInt() else 1024
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        viewModel.analyzeKit(stream.toByteArray())
    }

    private fun updateHistoryUI(container: LinearLayout, history: List<MedReadyScanResult>) {
        container.removeAllViews()
        if (history.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = getString(R.string.medready_no_scans)
                setPadding(0, 32, 0, 32)
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.sr_text_secondary))
            }
            container.addView(emptyView)
            return
        }

        history.forEach { result ->
            val card = layoutInflater.inflate(R.layout.item_medready_history, container, false)
            val dateText = card.findViewById<TextView>(R.id.historyDate)
            val countText = card.findViewById<TextView>(R.id.historyItemCount)
            val scoreText = card.findViewById<TextView>(R.id.historyScore)
            val warningStrip = card.findViewById<View>(R.id.historyWarningStrip)
            val warningText = card.findViewById<TextView>(R.id.historyWarningText)

            val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            dateText.text = sdf.format(Date(result.timestamp))
            countText.text = "${result.itemCount} items scanned"
            scoreText.text = "${result.readinessScore}%"
            
            if (result.warnings > 0) {
                warningStrip.visibility = View.VISIBLE
                warningText.text = "${result.warnings} warnings found"
            } else {
                warningStrip.visibility = View.GONE
            }

            card.setOnClickListener { showScanResultDialog(result) }
            container.addView(card)
        }
    }

    private fun showScanResultDialog(result: MedReadyScanResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_medready_result, null)
        val scoreText = dialogView.findViewById<TextView>(R.id.dialogScore)
        val summaryText = dialogView.findViewById<TextView>(R.id.dialogSummary)
        val itemsContainer = dialogView.findViewById<LinearLayout>(R.id.dialogItemsContainer)

        scoreText.text = "${result.readinessScore}%"
        summaryText.text = result.summary

        result.items.forEach { item ->
            val itemView = layoutInflater.inflate(R.layout.item_medready_detail, itemsContainer, false)
            val nameText = itemView.findViewById<TextView>(R.id.itemName)
            val statusText = itemView.findViewById<TextView>(R.id.itemStatus)
            val statusIcon = itemView.findViewById<ImageView>(R.id.itemStatusIcon)

            nameText.text = item.name
            statusText.text = item.status
            
            when (item.status) {
                "Detected" -> {
                    statusIcon.setImageResource(R.drawable.ic_medready_detection)
                    statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_success))
                    statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_success))
                }
                "Missing" -> {
                    statusIcon.setImageResource(R.drawable.ic_medready_missing)
                    statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_danger))
                    statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_danger))
                }
                "Expired" -> {
                    statusIcon.setImageResource(R.drawable.ic_medready_expiry)
                    statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_warning))
                    statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning))
                }
            }
            itemsContainer.addView(itemView)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Dismiss", null)
            .show()
    }
}

class RiskAssessmentFragment : Fragment() {
    private lateinit var viewModel: RiskViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_risk_assessment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[RiskViewModel::class.java]

        val stateSelector = view.findViewById<AutoCompleteTextView>(R.id.stateSelector)
        val districtSelector = view.findViewById<AutoCompleteTextView>(R.id.districtSelector)
        val monthSelector = view.findViewById<AutoCompleteTextView>(R.id.monthSelector)
        val disasterChipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.disasterChipGroup)
        val checkButton = view.findViewById<MaterialButton>(R.id.checkRiskButton)
        val loading = view.findViewById<ProgressBar>(R.id.riskLoading)
        val resultSection = view.findViewById<LinearLayout>(R.id.resultSection)
        val inputCard = view.findViewById<CardView>(R.id.inputCard)

        // Month setup
        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, months)
        monthSelector.setAdapter(monthAdapter)

        viewModel.states.observe(viewLifecycleOwner) { states ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, states)
            stateSelector.setAdapter(adapter)
        }

        viewModel.districts.observe(viewLifecycleOwner) { districts ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, districts)
            districtSelector.setAdapter(adapter)
        }

        districtSelector.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrBlank()) {
                    viewModel.searchDistricts(s.toString())
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        checkButton.setOnClickListener {
            val state = stateSelector.text.toString()
            val district = districtSelector.text.toString()
            val monthIdx = months.indexOf(monthSelector.text.toString()) + 1
            val selectedChipId = disasterChipGroup.checkedChipId
            val disasterType = when (selectedChipId) {
                R.id.chipFlood -> "Flood"
                R.id.chipEarthquake -> "Earthquake"
                R.id.chipLandslide -> "Landslide"
                R.id.chipCyclone -> "Cyclone"
                else -> ""
            }

            if (state.isEmpty() || disasterType.isEmpty() || monthIdx == 0) {
                Toast.makeText(requireContext(), "Please select State, Disaster, and Month", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.checkRisk(state, district.ifBlank { null }, disasterType, monthIdx)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loading.visibility = if (isLoading) View.VISIBLE else View.GONE
            checkButton.isEnabled = !isLoading
        }

        viewModel.prediction.observe(viewLifecycleOwner) { prediction ->
            if (prediction != null) {
                resultSection.visibility = View.VISIBLE
                inputCard.visibility = View.GONE
                
                view.findViewById<TextView>(R.id.riskLevelText).text = prediction.prediction.riskLevel
                view.findViewById<TextView>(R.id.riskEmoji).text = prediction.prediction.emoji
                
                // Dampen the probability visually (p^2.5) to avoid alarmism
                // 0.35 raw prob -> ~0.073 (7.3%) visual likelihood
                val visualProbability = Math.pow(prediction.prediction.probability, 2.5)
                
                view.findViewById<TextView>(R.id.probabilityText).text = "Risk Likelihood: ${String.format(Locale.US, "%.1f", visualProbability * 100)}%"

                val probBar = view.findViewById<ProgressBar>(R.id.probabilityBar)
                probBar.progress = (visualProbability * 100).toInt()
                
                val textColor = when(prediction.prediction.riskLevel.uppercase()) {
                    "HIGH" -> ContextCompat.getColor(requireContext(), R.color.status_danger)
                    "MODERATE" -> ContextCompat.getColor(requireContext(), R.color.status_warning_text)
                    else -> ContextCompat.getColor(requireContext(), R.color.status_success)
                }
                val barColor = when(prediction.prediction.riskLevel.uppercase()) {
                    "HIGH" -> ContextCompat.getColor(requireContext(), R.color.status_danger)
                    "MODERATE" -> ContextCompat.getColor(requireContext(), R.color.status_warning)
                    else -> ContextCompat.getColor(requireContext(), R.color.status_success)
                }
                probBar.progressTintList = android.content.res.ColorStateList.valueOf(barColor)
                view.findViewById<TextView>(R.id.riskLevelText).setTextColor(textColor)

                view.findViewById<TextView>(R.id.districtEventsCount).text = prediction.breakdown.districtEvents.toString()
                view.findViewById<TextView>(R.id.stateEventsCount).text = prediction.breakdown.stateEvents.toString()
                view.findViewById<TextView>(R.id.adviceText).text = "Historical data indicates ${prediction.prediction.riskLevel} risk of disaster here."
            }
        }

        viewModel.explanation.observe(viewLifecycleOwner) { explanation ->
            view.findViewById<TextView>(R.id.aiExplanationText).text = explanation
        }

        view.findViewById<MaterialButton>(R.id.resetButton).setOnClickListener {
            resultSection.visibility = View.GONE
            inputCard.visibility = View.VISIBLE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
        }
    }
}
