package com.example.capstone

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.example.capstone.presentation.HomeFragment
import com.example.capstone.presentation.LabFragment
import com.example.capstone.presentation.MedReadyFragment
import com.example.capstone.presentation.ProfileFragment
import com.example.capstone.presentation.QuizFragment
import com.example.capstone.presentation.fragment.AssistantBottomSheetFragment
import com.example.capstone.presentation.fragment.EmergencyFragment
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction

class MainActivity : AppCompatActivity() {

    private lateinit var navHome: FrameLayout
    private lateinit var navLab: FrameLayout
    private lateinit var navMedReady: FrameLayout
    private lateinit var navProfile: FrameLayout
    private lateinit var sosButton: View
    private lateinit var aiOrb: View
    private var currentTabId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt(KEY_CURRENT_TAB, R.id.nav_home)
        }

        navHome = findViewById(R.id.navHome)
        navLab = findViewById(R.id.navLab)
        navMedReady = findViewById(R.id.navMedReady)
        navProfile = findViewById(R.id.navProfile)
        sosButton = findViewById(R.id.sosEmergencyButton)
        aiOrb = findViewById(R.id.aiAssistantOrb)

        bindNavClicks()

        sosButton.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, EmergencyFragment())
                .addToBackStack(null)
                .commit()
        }

        aiOrb.setOnClickListener {
            openAssistant()
        }
        
        try {
            sosButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.sos_pulse))
        } catch (e: Exception) {
            // Pulse animation might be missing, ignore
        }

        updateSelectedTabUi(currentTabId)
        if (savedInstanceState == null) {
            showTab(currentTabId)
            handleIntent(intent)
        }
    }

    fun openAssistant() {
        AssistantBottomSheetFragment().show(supportFragmentManager, "AssistantBottomSheet")
    }

    fun selectTab(itemId: Int) {
        currentTabId = itemId
        updateSelectedTabUi(itemId)
        showTab(itemId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_CURRENT_TAB, currentTabId)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
        if (navigateTo == "QUIZ") {
            val disasterKey = intent.getStringExtra("QUIZ_DISASTER_KEY")
            val topic = intent.getStringExtra("QUIZ_TOPIC")
            
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, QuizFragment.newInstance(topic, disasterKey))
                .addToBackStack(null)
                .commit()
        } else if (navigateTo == "EMERGENCY") {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, EmergencyFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun showTab(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_lab -> LabFragment()
            R.id.nav_medready -> MedReadyFragment()
            R.id.nav_profile -> ProfileFragment()
            else -> HomeFragment()
        }

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.mainFragmentContainer, fragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    private fun bindNavClicks() {
        navHome.setOnClickListener { selectTab(R.id.nav_home) }
        navLab.setOnClickListener { selectTab(R.id.nav_lab) }
        navMedReady.setOnClickListener { selectTab(R.id.nav_medready) }
        navProfile.setOnClickListener { selectTab(R.id.nav_profile) }
    }

    private fun updateSelectedTabUi(selected: Int) {
        updateTab(navHome, selected == R.id.nav_home)
        updateTab(navLab, selected == R.id.nav_lab)
        updateTab(navMedReady, selected == R.id.nav_medready)
        updateTab(navProfile, selected == R.id.nav_profile)
    }

    private fun updateTab(container: FrameLayout, selected: Boolean) {
        val (iconId, labelId) = when (container.id) {
            R.id.navHome -> R.id.navHomeIcon to R.id.navHomeLabel
            R.id.navLab -> R.id.navLabIcon to R.id.navLabLabel
            R.id.navMedReady -> R.id.navMedReadyIcon to R.id.navMedReadyLabel
            R.id.navProfile -> R.id.navProfileIcon to R.id.navProfileLabel
            else -> return
        }
        val icon = container.findViewById<ImageView>(iconId)
        val label = container.findViewById<TextView>(labelId)
        
        val tint = if (selected) R.color.nav_active else R.color.nav_inactive
        // Standardize highlight to v2.0 design system
        container.setBackgroundResource(if (selected) R.drawable.bg_nav_selected_pill else android.R.color.transparent)
        
        icon?.setColorFilter(ContextCompat.getColor(this, tint))
        label?.setTextColor(ContextCompat.getColor(this, tint))
        
        // Typography for v2.0 Plus Jakarta Sans is applied via labels in XML, 
        // but we ensure the selection is visually distinct
        label?.typeface = android.graphics.Typeface.create("sans-serif", if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    companion object {
        // Legacy extras kept only for compatibility if needed elsewhere, 
        // but no longer used in onCreate.
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_INSTITUTION = "extra_institution"
        private const val KEY_CURRENT_TAB = "key_current_tab"
    }
}
