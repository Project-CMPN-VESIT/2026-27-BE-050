package com.example.capstone.presentation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.capstone.DisasterDetailActivity
import com.example.capstone.R
import com.example.capstone.StartLearningActivity
import com.example.capstone.SituationalGameActivity

class LabFragment : Fragment() {
    private lateinit var viewModel: LabViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_lab_modern, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this, defaultViewModelProviderFactory)[LabViewModel::class.java]

        val progressBar = view.findViewById<ProgressBar>(R.id.videoLearningProgress)
        val levelText = view.findViewById<TextView>(R.id.labSurvivalLevel)
        val quizzesText = view.findViewById<TextView>(R.id.labQuizzesDone)
        val simulationsText = view.findViewById<TextView>(R.id.labSimulationsDone)
        val scoreText = view.findViewById<TextView>(R.id.labSurvivalScore)

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progressBar?.progress = state.overallProgress
        }

        viewModel.gamificationSummary.observe(viewLifecycleOwner) { summary ->
            levelText?.text = getString(R.string.lab_level_format, summary.level)
            quizzesText?.text = summary.quizzesCompleted.toString()
            simulationsText?.text = summary.simulationsCompleted.toString()
            scoreText?.text = summary.points.toString()
        }

        // Daily Challenge card
        view.findViewById<View>(R.id.dailyChallengeCard)?.setOnClickListener {
            // Choose today's daily challenge topic (rotates every 24h)
            val prefs = com.example.capstone.data.SafeReadyPreferences(requireContext())
            val lessons = com.example.capstone.data.LessonRepository()
            val progressRepo = com.example.capstone.data.ProgressRepository(prefs, lessons)
            val topic = progressRepo.getDailyChallengeTopic()
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, QuizFragment.newInstance(topic))
                .addToBackStack(null)
                .commit()
        }

        // Situational Game card
        view.findViewById<View>(R.id.situationalGameCard)?.setOnClickListener {
            startActivity(Intent(requireContext(), SituationalGameActivity::class.java))
        }

        // Role-Based Game card
        view.findViewById<View>(R.id.roleBasedGameCard)?.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.lab_role_game_toast), Toast.LENGTH_SHORT).show()
        }

        // Quiz stays behind the explicit button inside the quiz card.
        view.findViewById<View>(R.id.quizCard)?.isClickable = false
        view.findViewById<View>(R.id.quizCard)?.isFocusable = false
        view.findViewById<View>(R.id.startQuizButton)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, QuizFragment.newInstance("emergency response"))
                .addToBackStack(null)
                .commit()
        }

        // Video Learning card opens StartLearningActivity
        view.findViewById<View>(R.id.video_card_disasters)?.setOnClickListener {
            startActivity(Intent(requireContext(), StartLearningActivity::class.java))
        }

        // Disaster Training Cards
        view.findViewById<View>(R.id.cardEarthquake)?.setOnClickListener { startDisaster("earthquake") }
        view.findViewById<View>(R.id.cardFloods)?.setOnClickListener { startDisaster("floods") }
        view.findViewById<View>(R.id.cardCyclone)?.setOnClickListener { startDisaster("cyclone") }
        view.findViewById<View>(R.id.cardLandslides)?.setOnClickListener { startDisaster("landslides") }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refresh()
        }
    }

    private fun startDisaster(disasterKey: String) {
        val activity = requireActivity() as AppCompatActivity
        startActivity(DisasterDetailActivity.newIntent(activity, disasterKey))
    }
}
