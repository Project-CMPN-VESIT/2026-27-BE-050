package com.example.capstone.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.capstone.BuildConfig
import kotlinx.coroutines.launch
import com.example.capstone.data.AssistantRepository
import com.example.capstone.data.AssistantReply
import com.example.capstone.data.ChatMessage
import com.example.capstone.data.GamificationRepository
import com.example.capstone.data.DisasterModule
import com.example.capstone.data.DisasterProgress
import com.example.capstone.data.EmergencyRepository
import com.example.capstone.data.LessonRepository
import com.example.capstone.data.RecommendationCard
import com.example.capstone.data.RecommendationRepository
import com.example.capstone.data.LocationRepository
import com.example.capstone.data.WeatherRepository
import com.example.capstone.data.ProgressRepository
import com.example.capstone.data.GamificationSummary
import com.example.capstone.data.ProgressSnapshot
import com.example.capstone.data.QuizQuestion
import com.example.capstone.data.QuizRepository
import com.example.capstone.data.QuizResult
import com.example.capstone.data.SafeReadyPreferences
import com.example.capstone.data.UserProfile
import com.example.capstone.data.UserRepository
import com.example.capstone.data.repository.MeshRepository
import com.example.capstone.data.remote.firebase.FirebaseAuthDataSource
import com.example.capstone.data.remote.firebase.FirebaseUserDataSource
import com.example.capstone.data.repository.AuthRepository
import com.example.capstone.data.repository.RiskRepository
import com.example.capstone.data.remote.risk.RiskSummary
import com.example.capstone.location.LocationHelper


data class HomeState(
    val user: UserProfile = UserProfile(),
    val regionLabel: String = "📍 Location unavailable",
    val overallProgress: Int = 0,
    val recommendedModule: DisasterModule? = null,
    val recommendation: RecommendationCard? = null,
    val gamification: GamificationSummary = GamificationSummary(0, 1, 0, 0, emptyList(), "0 lessons completed"),
    val progressSnapshot: ProgressSnapshot = ProgressSnapshot(0, emptyList()),
    val isEmergencyModeEnabled: Boolean = false,
    val weather: String = "--°C",
    val riskLevel: String = "Historical ML Risk",
    val riskDescription: String = "Historical baseline risk only",
    val medReadyReadiness: Int = -1,
    val locationProfile: Map<String, RiskSummary> = emptyMap(),
    val liveStatus: String = "MONITORING",
    val liveStatusDescription: String = "Grant location permission to check nearby live disasters.",
    val liveEventId: String? = null,
    val liveEventTitle: String? = null
)

data class TrainingState(
    val modules: List<DisasterModule> = emptyList(),
)

data class LabState(
    val overallProgress: Int = 0,
    val completedLessons: Int = 0,
    val totalLessons: Int = 0,
)

data class ProgressState(
    val snapshot: ProgressSnapshot = ProgressSnapshot(0, emptyList()),
    val gamification: GamificationSummary = GamificationSummary(0, 1, 0, 0, emptyList(), "0 lessons completed"),
)

data class AssistantState(
    val messages: List<ChatMessage> = emptyList(),
    val prompts: List<String> = emptyList(),
    val suggestedTopic: String? = null,
    val followUpPrompts: List<String> = emptyList(),
    val backendLabel: String = "Offline disaster assistant",
    val isLoading: Boolean = false,
)

data class ProfileState(
    val profile: UserProfile = UserProfile(),
    val completedDisasters: List<DisasterProgress> = emptyList(),
    val gamification: GamificationSummary = GamificationSummary(0, 1, 0, 0, emptyList(), "0 lessons completed"),
    val lessonsCompleted: Int = 0,
    val totalLessons: Int = 0,
    val simulationsCompleted: Int = 0,
    val totalSimulations: Int = 0,
    val drillsCompleted: Int = 0,
    val totalDrills: Int = 0,
    val emergencyContacts: List<com.example.capstone.data.EmergencyContact> = emptyList(),
    val themeMode: Int = 0,
    val isEmergencyModeEnabled: Boolean = false,
)

data class QuizState(
    val question: QuizQuestion? = null,
    val result: QuizResult? = null,
    val selectedIndex: Int? = null,
    val currentQuestionIndex: Int = 0,
    val totalQuestions: Int = 0,
    val score: Int = 0,
    val currentLevel: Int = 1,
    val isLevelComplete: Boolean = false,
    val isFinished: Boolean = false,
    val isLoading: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = SafeReadyPreferences(application)
    private val meshRepository = MeshRepository(application.applicationContext)
    private val emergencyRepository = EmergencyRepository(application.applicationContext, prefs, meshRepository)
    private val userRepository = UserRepository(prefs)
    private val lessonRepository = LessonRepository()
    private val progressRepository = ProgressRepository(prefs, lessonRepository)
    private val gamificationRepository = GamificationRepository(prefs, progressRepository)
    private val recommendationRepository = RecommendationRepository(lessonRepository, progressRepository)
    private val locationRepository = LocationRepository(application.applicationContext)
    private val weatherRepository = WeatherRepository()
    private val riskRepository = RiskRepository()
    private val medReadyRepository = com.example.capstone.data.repository.MedReadyRepository(
        com.example.capstone.data.remote.groq.GroqVisionDataSource(),
        prefs
    )

    val state = MutableLiveData<HomeState>()

    init {
        refresh()
        resolveLocation()
    }

    fun refresh() {
        val user = userRepository.getProfile()
        val snapshot = progressRepository.getAllProgress()
        val overall = progressRepository.getOverallProgress()
        val recommended = progressRepository.getRecommendedModule(user.state ?: user.city, progressRepository.getCompletionHint())
        val gamification = gamificationRepository.getSummary()
        val recommendation = recommendationRepository.buildRecommendation(user, gamification)
        val emergencyMode = emergencyRepository.isEmergencyModeEnabled()
        
        val latestScan = medReadyRepository.getScanHistory().firstOrNull()
        val medReadyScore = latestScan?.readinessScore ?: -1
        
        state.postValue(HomeState(
            user = user,
            regionLabel = formatRegion(user.city, user.state),
            overallProgress = overall,
            recommendedModule = recommended,
            recommendation = recommendation,
            gamification = gamification,
            progressSnapshot = ProgressSnapshot(overall, snapshot),
            isEmergencyModeEnabled = emergencyMode,
            weather = state.value?.weather ?: "--°C",
            riskLevel = state.value?.riskLevel ?: "Historical ML Risk",
            riskDescription = state.value?.riskDescription ?: "Historical baseline risk only",
            medReadyReadiness = medReadyScore,
            locationProfile = state.value?.locationProfile ?: emptyMap(),
            liveStatus = state.value?.liveStatus ?: "MONITORING",
            liveStatusDescription = state.value?.liveStatusDescription
                ?: "Grant location permission to check nearby live disasters.",
            liveEventId = state.value?.liveEventId,
            liveEventTitle = state.value?.liveEventTitle
        ))
    }

    fun resolveLocation() {
        locationRepository.resolveState { stateName ->
            val profile = userRepository.getProfile()
            val targetState = if (!stateName.isNullOrBlank()) stateName else profile.state
            
            if (!targetState.isNullOrBlank()) {
                if (stateName != null) userRepository.saveRegion(profile.city, stateName)
                refresh()
                fetchWeather(targetState)
                fetchLocationProfile(targetState)
            } else {
                // If no state available anywhere, try to refresh with defaults
                refresh()
            }
        }
        locationRepository.resolveCity { cityName ->
            val profile = userRepository.getProfile()
            val targetCity = if (!cityName.isNullOrBlank()) cityName else profile.city
            
            if (!targetCity.isNullOrBlank()) {
                if (cityName != null) userRepository.saveRegion(cityName, profile.state)
                refresh()
                // Only fetch if we haven't successfully fetched using state yet, 
                // or if we want city-specific data
                fetchWeather(targetCity)
            }
        }
        LocationHelper.fetchLastKnownLocation(getApplication()) { location ->
            if (location != null) {
                fetchLiveStatus(location.latitude, location.longitude)
            }
        }
    }

    private fun fetchLiveStatus(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                val nearby = com.example.capstone.data.repository.LiveDisasterRepository().fetchNearbyEvents(latitude, longitude)
                state.value?.let { current ->
                    val top = nearby.firstOrNull()
                    val liveStatus = when {
                        emergencyRepository.isEmergencyModeEnabled() && top?.alertLevel == com.example.capstone.data.disaster.AlertLevel.AFFECTED -> "EMERGENCY"
                        top?.alertLevel == com.example.capstone.data.disaster.AlertLevel.AFFECTED -> "AFFECTED"
                        top?.alertLevel == com.example.capstone.data.disaster.AlertLevel.AT_RISK -> "AT RISK"
                        top?.alertLevel == com.example.capstone.data.disaster.AlertLevel.WARNING_MONITORING -> "MONITORING"
                        nearby.isEmpty() -> "SAFE"
                        else -> "MONITORING"
                    }
                    val liveDescription = when {
                        top != null -> "${top.displayTitle()} • ${top.location} • ${top.evidenceConfidence} evidence confidence"
                        else -> "No nearby live disasters detected."
                    }
                    state.postValue(current.copy(
                        liveStatus = liveStatus,
                        liveStatusDescription = liveDescription,
                        liveEventId = top?.eventId,
                        liveEventTitle = top?.displayTitle()
                    ))
                }
            } catch (e: Exception) {
                state.value?.let { current ->
                    state.postValue(current.copy(
                        liveStatus = "MONITORING",
                        liveStatusDescription = "Live disaster backend unavailable."
                    ))
                }
            }
        }
    }

    private fun fetchLocationProfile(stateName: String) {
        viewModelScope.launch {
            try {
                val calendar = java.util.Calendar.getInstance()
                val month = calendar.get(java.util.Calendar.MONTH) + 1
                val profile = riskRepository.getLocationProfile(stateName, month)
                state.value?.let { current ->
                    val topRisk = profile.maxByOrNull { it.value.probability }
                    val visualProb = topRisk?.let { Math.pow(it.value.probability, 2.5) } ?: 0.0
                    val newRiskLevel = topRisk?.let { "${it.key} Risk: ${it.value.riskLevel}" } ?: current.riskLevel
                    val newRiskDesc = topRisk?.let { 
                        "Risk Likelihood: ${String.format(java.util.Locale.US, "%.1f", visualProb * 100)}% for this month." 
                    } ?: current.riskDescription
                    
                    state.postValue(current.copy(
                        locationProfile = profile,
                        riskLevel = newRiskLevel,
                        riskDescription = newRiskDesc
                    ))
                }
            } catch (e: Exception) {
                // Silently fail for background enrichment
            }
        }
    }

    private fun fetchWeather(location: String) {
        viewModelScope.launch {
            try {
                val weather = weatherRepository.fetchWeather(location)
                state.value?.let { current ->
                    state.postValue(current.copy(weather = weather))
                }
            } catch (e: Exception) {
                // Keep default
            }
        }
    }

    private fun formatRegion(city: String?, state: String?): String {
        return when {
            !city.isNullOrBlank() && !state.isNullOrBlank() -> "📍 $city, $state"
            !state.isNullOrBlank() -> "📍 $state"
            !city.isNullOrBlank() -> "📍 $city"
            else -> "📍 Location unavailable"
        }
    }
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val lessonRepository = LessonRepository()
    val state = MutableLiveData(TrainingState(lessonRepository.getModules()))

    fun refresh() {
        state.postValue(TrainingState(lessonRepository.getModules()))
    }
}

class LabViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = SafeReadyPreferences(application)
    private val lessonRepository = LessonRepository()
    private val progressRepository = ProgressRepository(prefs, lessonRepository)
    private val gamificationRepository = GamificationRepository(prefs, progressRepository)

    val state = MutableLiveData<LabState>()
    val gamificationSummary = MutableLiveData<GamificationSummary>()

    init {
        refresh()
    }

    fun refresh() {
        val overall = progressRepository.getOverallProgress()
        val completed = progressRepository.getTotalCompletedChapters()
        val total = lessonRepository.getModules().sumOf { it.chapters.size }
        val summary = gamificationRepository.getSummary()
        
        state.postValue(LabState(
            overallProgress = overall,
            completedLessons = completed,
            totalLessons = total
        ))
        gamificationSummary.postValue(summary)
    }
}

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = SafeReadyPreferences(application)
    private val lessonRepository = LessonRepository()
    private val progressRepository = ProgressRepository(prefs, lessonRepository)
    private val gamificationRepository = GamificationRepository(prefs, progressRepository)

    val state = MutableLiveData<ProgressState>()

    init {
        refresh()
    }

    fun refresh() {
        state.postValue(ProgressState(
            snapshot = ProgressSnapshot(
                overallPercent = progressRepository.getOverallProgress(),
                disasterProgress = progressRepository.getAllProgress(),
            ),
            gamification = gamificationRepository.getSummary(),
        ))
    }

    fun markChapterCompleted(disasterKey: String, chapterIndex: Int) {
        if (progressRepository.markChapterCompleted(disasterKey, chapterIndex)) {
            gamificationRepository.recordLessonCompletion(disasterKey, chapterIndex)
        }
        refresh()
    }

    fun saveQuizResult(disasterKey: String, chapterIndex: Int, result: QuizResult) {
        progressRepository.saveQuizScore(disasterKey, chapterIndex, result.score, result.total)
        gamificationRepository.recordQuizAttempt(result.passed)
        refresh()
    }

    fun clearProgress() {
        progressRepository.clearProgress()
        gamificationRepository.reset()
        refresh()
    }
}

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val db = com.example.capstone.data.local.database.AppDatabase.getDatabase(application)
    private val assistantRepository = AssistantRepository(
        chatDao = db.chatDao()
    )
    
    val state = MutableLiveData(
        AssistantState(
            prompts = assistantRepository.quickPrompts(),
            backendLabel = assistantRepository.backendLabel(),
        )
    )

    init {
        viewModelScope.launch {
            assistantRepository.chatHistory.collect { messages ->
                val current = state.value ?: AssistantState()
                val displayMessages = if (messages.isEmpty()) {
                    listOf(
                        ChatMessage(
                            text = "Hi! Ask me about earthquakes, floods, cyclones, landslides, evacuation, or emergency kits.",
                            isUser = false,
                        )
                    )
                } else {
                    messages
                }
                state.postValue(current.copy(
                    messages = displayMessages,
                    backendLabel = assistantRepository.backendLabel()
                ))
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || state.value?.isLoading == true) return

        val previous = state.value ?: AssistantState()
        state.value = previous.copy(
            isLoading = true,
            backendLabel = assistantRepository.backendLabel(),
        )

        viewModelScope.launch {
            // Save user message to persistent storage
            assistantRepository.saveMessage(trimmed, true)
            
            // Fix: Pass previous.messages (history) without the current trimmed text, 
            // as respondWithContext/complete adds the current input separately.
            val reply: AssistantReply = assistantRepository.respondWithContext(trimmed, previous.messages)
            
            // Save assistant message to persistent storage
            assistantRepository.saveMessage(reply.answer, false)
            
            state.value = (state.value ?: previous).copy(
                prompts = reply.followUpPrompts.ifEmpty { assistantRepository.quickPrompts() },
                suggestedTopic = reply.suggestedTopic,
                followUpPrompts = reply.followUpPrompts,
                backendLabel = assistantRepository.backendLabel(),
                isLoading = false,
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            assistantRepository.clearHistory()
        }
    }
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = SafeReadyPreferences(application)
    private val meshRepository = MeshRepository(application.applicationContext)
    private val emergencyRepository = EmergencyRepository(application.applicationContext, prefs, meshRepository)
    private val userRepository = UserRepository(prefs)
    private val lessonRepository = LessonRepository()
    private val progressRepository = ProgressRepository(prefs, lessonRepository)
    private val gamificationRepository = GamificationRepository(prefs, progressRepository)
    private val authRepository = AuthRepository(
        FirebaseAuthDataSource(),
        FirebaseUserDataSource(),
        userRepository
    )

    val state = MutableLiveData<ProfileState>()

    init {
        refresh()
    }

    fun refresh() {
        val progress = progressRepository.getAllProgress()
        val contacts = prefs.getEmergencyContacts()
        val totalChapters = lessonRepository.getModules().sumOf { it.chapters.size }
        val completedChapters = progressRepository.getTotalCompletedChapters()
        
        // For now, let's map:
        // Lessons = Chapters
        // Simulations = Quizzes passed (estimated from chapter completion for now)
        // Drills = Half of chapters (as an example of 'practiced')
        
        state.postValue(ProfileState(
            profile = userRepository.getProfile(),
            completedDisasters = progress,
            gamification = gamificationRepository.getSummary(),
            lessonsCompleted = completedChapters,
            totalLessons = totalChapters,
            simulationsCompleted = completedChapters, // Mapping to lessons for now
            totalSimulations = totalChapters,
            drillsCompleted = completedChapters / 2,
            totalDrills = totalChapters,
            emergencyContacts = contacts,
            themeMode = prefs.getThemeMode(),
            isEmergencyModeEnabled = prefs.getEmergencyModeEnabled()
        ))
    }

    fun setThemeMode(mode: Int) {
        prefs.setThemeMode(mode)
        refresh()
    }

    fun updateProfile(name: String, email: String, institution: String, city: String?, state: String?) {
        val current = userRepository.getProfile()
        userRepository.saveUserProfile(
            current.copy(
                name = name,
                email = email,
                institution = institution,
                city = city,
                state = state,
            )
        )
        refresh()
    }

    fun setEmergencyModeEnabled(enabled: Boolean) {
        emergencyRepository.setEmergencyModeEnabled(enabled)
        refresh()
    }

    fun toggleEmergencyMode(): Boolean {
        val updated = emergencyRepository.toggleEmergencyMode()
        refresh()
        return updated
    }

    fun logOut() {
        authRepository.logOut()
    }
}

class QuizViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = SafeReadyPreferences(application)
    private val lessonRepository = LessonRepository()
    private val progressRepository = ProgressRepository(prefs, lessonRepository)
    private val gamificationRepository = GamificationRepository(prefs, progressRepository)
    private val quizRepository = QuizRepository(lessonRepository, prefs, com.example.capstone.data.remote.groq.GroqQuizDataSource(BuildConfig.GROQ_API_KEY))

    val state = MutableLiveData(QuizState())
    private var questionsList = listOf<QuizQuestion>()
    private var isDynamicQuiz = false
    private var currentTopic: String = ""

    fun loadQuestion(disasterKey: String, chapterIndex: Int) {
        state.value = state.value?.copy(
            isLoading = true,
            isFinished = false,
            isLevelComplete = false,
            result = null,
            selectedIndex = null,
            question = null,
            score = 0,
        )
        val questions = quizRepository.getQuizForChapter(disasterKey, chapterIndex)
        questionsList = questions
        isDynamicQuiz = false
        
        if (questions.isNotEmpty()) {
            state.postValue(QuizState(
                question = questions[0],
                totalQuestions = questions.size,
                currentQuestionIndex = 0,
                isLoading = false
            ))
        } else {
            state.postValue(state.value?.copy(isLoading = false))
        }
    }

    fun generateDynamicQuiz(topic: String, level: Int = 1) {
        currentTopic = topic
        state.value = state.value?.copy(
            isLoading = true,
            isFinished = false,
            isLevelComplete = false,
            currentLevel = level,
            result = null,
            selectedIndex = null,
            question = null,
            score = 0,
        )
        viewModelScope.launch {
            // Increased to 10 questions per level for better engagement
            val questions = quizRepository.generateDynamicQuiz(topic, 10, level)
            isDynamicQuiz = true
            if (questions.isNotEmpty()) {
                questionsList = questions.mapIndexed { index, q ->
                    QuizQuestion(
                        disasterKey = topic,
                        chapterIndex = index,
                        question = q.question,
                        options = q.options,
                        correctIndex = when(q.correctAnswer) {
                            "A" -> 0; "B" -> 1; "C" -> 2; "D" -> 3; else -> 0
                        },
                        explanation = q.explanation
                    )
                }
                
                state.postValue(state.value?.copy(
                    question = questionsList[0],
                    totalQuestions = questionsList.size,
                    currentQuestionIndex = 0,
                    score = 0,
                    isLoading = false
                ))
            } else {
                state.postValue(state.value?.copy(isLoading = false))
            }
        }
    }

    fun startNextLevel() {
        val nextLevel = (state.value?.currentLevel ?: 1) + 1
        generateDynamicQuiz(currentTopic, nextLevel)
    }

    fun submitAnswer(selectedIndex: Int) {
        val currentState = state.value ?: return
        val question = currentState.question ?: return
        
        // Don't allow changing answer after it's submitted (showing result)
        if (currentState.result != null) return

        val result = quizRepository.evaluate(question, selectedIndex)
        val newScore = if (result.passed) currentState.score + 1 else currentState.score
        
        state.value = currentState.copy(
            result = result,
            selectedIndex = selectedIndex,
            score = newScore
        )
    }

    fun nextQuestion() {
        val currentState = state.value ?: return
        val nextIndex = currentState.currentQuestionIndex + 1
        
        if (nextIndex < questionsList.size) {
            val uiQuestion = questionsList[nextIndex]
            state.value = currentState.copy(
                question = uiQuestion,
                currentQuestionIndex = nextIndex,
                result = null,
                selectedIndex = null
            )
        } else {
            val total = currentState.totalQuestions
            val score = currentState.score
            val passed = (score.toFloat() / total) >= 0.6f
            
            if (isDynamicQuiz) {
                state.value = currentState.copy(isLevelComplete = passed, isFinished = true)
                saveDynamicQuizResult(score, total)
            } else {
                state.value = currentState.copy(isFinished = true)
                val currentQuestion = currentState.question
                if (currentQuestion != null && passed) {
                    recordCompletion(currentQuestion.disasterKey, currentQuestion.chapterIndex)
                }
                // Save specific quiz score regardless of passing
                progressRepository.saveQuizScore(
                    currentQuestion?.disasterKey ?: "",
                    currentQuestion?.chapterIndex ?: 0,
                    score,
                    total
                )
                gamificationRepository.recordQuizAttempt(passed)
            }
        }
    }

    fun recordCompletion(disasterKey: String, chapterIndex: Int) {
        if (progressRepository.markChapterCompleted(disasterKey, chapterIndex)) {
            gamificationRepository.recordLessonCompletion(disasterKey, chapterIndex)
        }
    }

    fun saveDynamicQuizResult(score: Int, total: Int) {
        val passed = (score.toFloat() / total) >= 0.6f
        gamificationRepository.recordQuizAttempt(passed)
        if (passed) {
            val level = state.value?.currentLevel ?: 1
            gamificationRepository.recordLessonCompletion("dynamic_quiz_level_$level", 0)
        }
    }
}
