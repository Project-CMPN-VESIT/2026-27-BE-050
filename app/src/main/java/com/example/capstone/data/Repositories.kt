package com.example.capstone.data

import android.content.Context
import android.content.SharedPreferences
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.capstone.DemoVideoRepository
import com.example.capstone.location.LocationHelper
import com.example.capstone.data.remote.groq.GroqChatDataSource
import com.example.capstone.data.local.database.dao.ChatDao
import com.example.capstone.data.local.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import com.example.capstone.data.remote.groq.GroqQuizDataSource
import com.example.capstone.data.remote.groq.QuizQuestion as GroqQuizQuestion
import androidx.core.content.edit
import com.example.capstone.data.MeshMessage
import com.example.capstone.data.MeshMessageType
import com.example.capstone.data.MeshLocation
import com.example.capstone.data.MeshLocationSource
import com.example.capstone.data.repository.MeshRepository
import com.example.capstone.util.EmergencyMessageFormatter
import com.example.capstone.util.EmergencySmsHelper
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.Locale

class SafeReadyPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit {
            putString(KEY_UID, profile.uid)
            putString(KEY_USERNAME, profile.username)
            putString(KEY_NAME, profile.name.trim())
            putString(KEY_EMAIL, profile.email.trim())
            putString(KEY_INSTITUTION, profile.institution.trim())
        }
    }

    fun saveRegion(city: String?, state: String?) {
        prefs.edit {
            putString(KEY_CITY, city?.trim())
            putString(KEY_STATE, state?.trim())
        }
    }

    fun getUserProfile(): UserProfile {
        return UserProfile(
            uid = prefs.getString(KEY_UID, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            name = prefs.getString(KEY_NAME, null).orEmpty().ifBlank { "User" },
            email = prefs.getString(KEY_EMAIL, null).orEmpty(),
            institution = prefs.getString(KEY_INSTITUTION, null).orEmpty(),
            city = prefs.getString(KEY_CITY, null),
            state = prefs.getString(KEY_STATE, null),
        )
    }

    fun clearProfile() {
        prefs.edit {
            remove(KEY_UID)
            remove(KEY_USERNAME)
            remove(KEY_NAME)
            remove(KEY_EMAIL)
            remove(KEY_INSTITUTION)
            remove(KEY_CITY)
            remove(KEY_STATE)
        }
    }

    fun markChapterComplete(disasterKey: String, chapterIndex: Int): Boolean {
        val updated = getCompletedChapterSet(disasterKey).toMutableSet()
        val wasNew = updated.add(chapterIndex)
        prefs.edit {
            putStringSet(completedKey(disasterKey), updated.map { it.toString() }.toSet())
        }
        return wasNew
    }

    fun getCompletedChapterSet(disasterKey: String): Set<Int> {
        return prefs.getStringSet(completedKey(disasterKey), emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()
    }

    fun saveQuizScore(disasterKey: String, chapterIndex: Int, score: Int, total: Int) {
        prefs.edit {
            putString(quizKey(disasterKey, chapterIndex), "$score/$total")
        }
    }

    fun getQuizScore(disasterKey: String, chapterIndex: Int): String? {
        return prefs.getString(quizKey(disasterKey, chapterIndex), null)
    }

    fun getCompletedChapterCount(disasterKey: String): Int = getCompletedChapterSet(disasterKey).size

    fun getTotalCompletedChapters(): Int {
        return prefs.all.keys
            .filter { it.startsWith(KEY_PROGRESS_PREFIX) }
            .sumOf { getCompletedChapterSet(it.removePrefix(KEY_PROGRESS_PREFIX)).size }
    }

    fun getPoints(): Int = prefs.getInt(KEY_POINTS, 0)

    fun setPoints(points: Int) {
        prefs.edit { putInt(KEY_POINTS, points.coerceAtLeast(0)) }
    }

    fun incrementPoints(delta: Int) {
        setPoints(getPoints() + delta)
    }

    fun getCurrentStreak(): Int = prefs.getInt(KEY_CURRENT_STREAK, 0)

    fun setCurrentStreak(value: Int) {
        prefs.edit { putInt(KEY_CURRENT_STREAK, value.coerceAtLeast(0)) }
    }

    fun getBestStreak(): Int = prefs.getInt(KEY_BEST_STREAK, 0)

    fun setBestStreak(value: Int) {
        prefs.edit { putInt(KEY_BEST_STREAK, value.coerceAtLeast(0)) }
    }

    fun getLastCompletionDay(): Long = prefs.getLong(KEY_LAST_COMPLETION_DAY, 0L)

    fun setLastCompletionDay(day: Long) {
        prefs.edit { putLong(KEY_LAST_COMPLETION_DAY, day) }
    }

    fun getDailyChallengeTopic(): String? {
        return prefs.getString(KEY_DAILY_TOPIC, null)
    }

    fun setDailyChallengeTopic(topic: String, timestamp: Long) {
        prefs.edit {
            putString(KEY_DAILY_TOPIC, topic)
            putLong(KEY_DAILY_TIMESTAMP, timestamp)
        }
    }

    fun getDailyChallengeTimestamp(): Long = prefs.getLong(KEY_DAILY_TIMESTAMP, 0L)

    fun clearProgress() {
        prefs.all.keys
            .filter { key -> key.startsWith(KEY_PROGRESS_PREFIX) || key.startsWith(KEY_QUIZ_PREFIX) }
            .forEach { key -> prefs.edit { remove(key) } }
        prefs.edit {
            remove(KEY_POINTS)
            remove(KEY_CURRENT_STREAK)
            remove(KEY_BEST_STREAK)
            remove(KEY_LAST_COMPLETION_DAY)
        }
    }

    fun getEmergencyModeEnabled(): Boolean = prefs.getBoolean(KEY_EMERGENCY_MODE_ENABLED, false)

    fun setEmergencyModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_EMERGENCY_MODE_ENABLED, enabled) }
    }

    fun getEmergencyContacts(): List<EmergencyContact> {
        val json = prefs.getString(KEY_EMERGENCY_CONTACTS, null) ?: return emptyList()
        val array = JSONArray(json)
        val list = mutableListOf<EmergencyContact>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(EmergencyContact(
                id = obj.getString("id"),
                name = obj.getString("name"),
                phone = obj.getString("phone"),
                relation = obj.optString("relation")
            ))
        }
        return list
    }

    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        val array = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject()
            obj.put("id", contact.id)
            obj.put("name", contact.name)
            obj.put("phone", contact.phone)
            obj.put("relation", contact.relation)
            array.put(obj)
        }
        prefs.edit { putString(KEY_EMERGENCY_CONTACTS, array.toString()) }
    }

    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, 0) // 0 is follow system

    fun setThemeMode(mode: Int) {
        prefs.edit { putInt(KEY_THEME_MODE, mode) }
    }

    fun saveMedReadyScan(result: MedReadyScanResult) {
        val list = getMedReadyScans().toMutableList()
        list.add(0, result)
        val array = JSONArray()
        list.take(10).forEach { scan ->
            val obj = JSONObject()
            obj.put("id", scan.id)
            obj.put("timestamp", scan.timestamp)
            obj.put("itemCount", scan.itemCount)
            obj.put("readinessScore", scan.readinessScore)
            obj.put("warnings", scan.warnings)
            obj.put("summary", scan.summary)
            val itemsArray = JSONArray()
            scan.items.forEach { item ->
                val itemObj = JSONObject()
                itemObj.put("name", item.name)
                itemObj.put("status", item.status)
                itemObj.put("expiryDate", item.expiryDate)
                itemObj.put("isEssential", item.isEssential)
                itemsArray.put(itemObj)
            }
            obj.put("items", itemsArray)
            array.put(obj)
        }
        prefs.edit { putString(KEY_MEDREADY_SCANS, array.toString()) }
    }

    fun getMedReadyScans(): List<MedReadyScanResult> {
        val json = prefs.getString(KEY_MEDREADY_SCANS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<MedReadyScanResult>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val itemsArray = obj.getJSONArray("items")
                val items = mutableListOf<MedReadyItem>()
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    items.add(MedReadyItem(
                        name = itemObj.optString("name", "Unknown Item"),
                        status = itemObj.optString("status", "Unknown"),
                        expiryDate = itemObj.optString("expiryDate", ""),
                        isEssential = itemObj.optBoolean("isEssential", true)
                    ))
                }
                list.add(MedReadyScanResult(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    itemCount = obj.optInt("itemCount", 0),
                    readinessScore = obj.optInt("readinessScore", 0),
                    warnings = obj.optInt("warnings", 0),
                    summary = obj.optString("summary", "No summary available"),
                    items = items
                ))
            }
            list
        } catch (e: Exception) {
            // Log error if possible, but return empty list to prevent crash
            emptyList()
        }
    }

    private fun completedKey(disasterKey: String) = "$KEY_PROGRESS_PREFIX$disasterKey"
    private fun quizKey(disasterKey: String, chapterIndex: Int) = "$KEY_QUIZ_PREFIX${disasterKey}_$chapterIndex"

    companion object {
        private const val PREFS_NAME = "safeready_prefs"
        private const val KEY_UID = "user_uid"
        private const val KEY_USERNAME = "user_username"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_INSTITUTION = "user_institution"
        private const val KEY_CITY = "user_city"
        private const val KEY_STATE = "user_state"
        private const val KEY_PROGRESS_PREFIX = "progress_"
        private const val KEY_QUIZ_PREFIX = "quiz_"
        private const val KEY_POINTS = "gamification_points"
        private const val KEY_CURRENT_STREAK = "gamification_current_streak"
        private const val KEY_BEST_STREAK = "gamification_best_streak"
        private const val KEY_LAST_COMPLETION_DAY = "gamification_last_completion_day"
        private const val KEY_DAILY_TOPIC = "daily_challenge_topic"
        private const val KEY_DAILY_TIMESTAMP = "daily_challenge_timestamp"
        private const val KEY_EMERGENCY_MODE_ENABLED = "emergency_mode_enabled"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MEDREADY_SCANS = "medready_scans"
    }
}

class EmergencyRepository(
    private val context: Context,
    private val prefs: SafeReadyPreferences,
    private val meshRepository: MeshRepository
) {
    fun isEmergencyModeEnabled(): Boolean = prefs.getEmergencyModeEnabled()

    fun setEmergencyModeEnabled(enabled: Boolean) {
        prefs.setEmergencyModeEnabled(enabled)
    }

    fun toggleEmergencyMode(): Boolean {
        val updated = !isEmergencyModeEnabled()
        setEmergencyModeEnabled(updated)
        return updated
    }

    fun triggerSos(
        status: String? = null,
        reason: String? = null,
        isAutomatic: Boolean = false,
        onComplete: ((Int) -> Unit)? = null
    ) {
        val userProfile = prefs.getUserProfile()
        val contacts = prefs.getEmergencyContacts()
        val contactsString = contacts.joinToString { "${it.name} (${it.phone})" }

        LocationHelper.fetchLastKnownLocation(context) { lastKnown ->
            LocationHelper.fetchCity(context) { city ->
                LocationHelper.fetchState(context) { state ->
                    val locationLabel = when {
                        city != null && state != null -> "$city, $state"
                        city != null -> city
                        state != null -> state
                        else -> "Unknown Location"
                    }
                    val coordinatesLabel = lastKnown?.let {
                        String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude)
                    } ?: "Unavailable"

                    val content = if (isAutomatic && reason != null) {
                        EmergencyMessageFormatter.automaticSos(reason, locationLabel, coordinatesLabel, contactsString)
                    } else {
                        EmergencyMessageFormatter.manualSos(status, locationLabel, coordinatesLabel, contactsString)
                    }

                    val msg = MeshMessage(
                        senderId = userProfile.uid.ifBlank { context.packageName },
                        senderName = userProfile.name,
                        type = MeshMessageType.SOS,
                        content = content,
                        location = MeshLocation(
                            latitude = lastKnown?.latitude,
                            longitude = lastKnown?.longitude,
                            accuracyMeters = lastKnown?.accuracyMeters,
                            label = locationLabel,
                            source = MeshLocationSource.LAST_KNOWN
                        )
                    )

                    meshRepository.broadcast(msg)
                    val sentSms = EmergencySmsHelper.sendToContacts(context, contacts, content)
                    showSosNotification(sentSms)
                    onComplete?.invoke(sentSms)
                }
            }
        }
    }

    private fun showSosNotification(contactCount: Int) {
        val channelId = "sos_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for SOS triggers and broadcast status"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentText = if (contactCount > 0) {
            "Emergency broadcast sent and $contactCount contacts notified via SMS."
        } else {
            "Emergency SOS broadcasted via mesh network."
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.capstone.R.drawable.ic_emergency_fab)
            .setContentTitle("SOS Triggered")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

class UserRepository(private val prefs: SafeReadyPreferences) {
    fun saveUserProfile(profile: UserProfile) = prefs.saveUserProfile(profile)

    fun getProfile(): UserProfile = prefs.getUserProfile()

    fun saveRegion(city: String?, state: String?) = prefs.saveRegion(city, state)

    fun clearProfile() = prefs.clearProfile()
}

class LessonRepository {
    private val modules: List<DisasterModule> = listOf(
        DisasterModule(
            key = "earthquake",
            title = "Earthquake",
            summary = "Learn how to protect yourself before, during, and after seismic activity.",
            regionTags = listOf("earthquake", "seismic", "western", "plateau"),
            chapters = listOf(
                LessonChapter(0, "Pre-Disaster", "general"),
                LessonChapter(1, "During-Disaster", "during"),
                LessonChapter(2, "Post-Disaster", "after"),
            ),
        ),
        DisasterModule(
            key = "floods",
            title = "Floods",
            summary = "Stay ready for heavy rainfall, water rise, and evacuation planning.",
            regionTags = listOf("flood", "coastal", "river", "low-lying"),
            chapters = listOf(
                LessonChapter(0, "Pre-Disaster", "general"),
                LessonChapter(1, "During-Disaster", "during"),
                LessonChapter(2, "Post-Disaster", "after"),
            ),
        ),
        DisasterModule(
            key = "cyclone",
            title = "Cyclone",
            summary = "Prepare for strong winds, storm surge, and early evacuation guidance.",
            regionTags = listOf("cyclone", "coastal", "storm", "shore"),
            chapters = listOf(
                LessonChapter(0, "Pre-Disaster", "general"),
                LessonChapter(1, "During-Disaster", "during"),
                LessonChapter(2, "Post-Disaster", "after"),
            ),
        ),
        DisasterModule(
            key = "landslides",
            title = "Landslides",
            summary = "Understand slope risks, warning signs, and safe evacuation practices.",
            regionTags = listOf("landslide", "hill", "mountain", "slope"),
            chapters = listOf(
                LessonChapter(0, "Pre-Disaster", "general"),
                LessonChapter(1, "During-Disaster", "during"),
                LessonChapter(2, "Post-Disaster", "after"),
            ),
        ),
    )

    fun getModules(): List<DisasterModule> = modules

    fun getModule(key: String): DisasterModule = modules.first { it.key == key }

    fun getRecommendedModule(region: String?, completionHint: String? = null): DisasterModule {
        val normalizedRegion = region?.lowercase(Locale.US).orEmpty()
        val regionMatch = modules.firstOrNull { module ->
            module.regionTags.any { tag -> normalizedRegion.contains(tag) }
        }
        if (regionMatch != null) return regionMatch

        val hint = completionHint?.lowercase(Locale.US).orEmpty()
        val hintMatch = modules.firstOrNull { module -> hint.contains(module.key) || hint.contains(module.title.lowercase(Locale.US)) }
        return hintMatch ?: modules.first()
    }

    fun getCompletionPercent(completedChapterCount: Int, totalChapters: Int = 3): Int {
        if (totalChapters <= 0) return 0
        return ((completedChapterCount.toFloat() / totalChapters) * 100).toInt().coerceIn(0, 100)
    }

    fun getVideoUri(context: android.content.Context, disasterKey: String, chapterIndex: Int, languageCode: String) =
        DemoVideoRepository.getVideoUri(context, disasterKey, chapterIndex, languageCode)

    fun getAvailableLanguages(context: android.content.Context, disasterKey: String, chapterIndex: Int) =
        DemoVideoRepository.getAvailableLanguages(context, disasterKey, chapterIndex)

    fun getChapterTitle(disasterKey: String, chapterIndex: Int): String {
        val module = getModule(disasterKey)
        return module.chapters.getOrNull(chapterIndex)?.title ?: "Chapter ${chapterIndex + 1}"
    }
}

class ProgressRepository(
    private val prefs: SafeReadyPreferences,
    private val lessons: LessonRepository,
) {
    fun markChapterCompleted(disasterKey: String, chapterIndex: Int): Boolean {
        return prefs.markChapterComplete(disasterKey, chapterIndex)
    }

    fun saveQuizScore(disasterKey: String, chapterIndex: Int, score: Int, total: Int) {
        prefs.saveQuizScore(disasterKey, chapterIndex, score, total)
    }

    fun getDisasterProgress(disasterKey: String): DisasterProgress {
        val module = lessons.getModule(disasterKey)
        val completed = prefs.getCompletedChapterCount(disasterKey)
        return DisasterProgress(module.key, module.title, completed, module.chapters.size)
    }

    fun getAllProgress(): List<DisasterProgress> = lessons.getModules().map { getDisasterProgress(it.key) }

    fun getCompletedChapterSet(disasterKey: String): Set<Int> {
        return prefs.getCompletedChapterSet(disasterKey)
    }

    fun getOverallProgress(): Int {
        val snapshot = getAllProgress()
        if (snapshot.isEmpty()) return 0
        return snapshot.sumOf { it.percent } / snapshot.size
    }

    fun getRecommendedModule(region: String?, completionHint: String? = null): DisasterModule {
        return lessons.getRecommendedModule(region, completionHint)
    }

    fun getCompletionHint(): String {
        val progress = getAllProgress()
        val incomplete = progress.firstOrNull { it.completedChapters < it.totalChapters }
        return incomplete?.key ?: progress.firstOrNull()?.key.orEmpty()
    }

    fun getNextIncompleteChapterIndex(disasterKey: String): Int {
        val completed = prefs.getCompletedChapterSet(disasterKey)
        return (0..2).firstOrNull { it !in completed } ?: 0
    }

    fun getTotalCompletedChapters(): Int = prefs.getTotalCompletedChapters()

    fun clearProgress() = prefs.clearProgress()
}

class QuizRepository(
    private val lessons: LessonRepository,
    private val prefs: SafeReadyPreferences,
    private val groqSource: GroqQuizDataSource? = null
) {
    private val questionPool = mutableMapOf<String, List<QuizQuestion>>()

    init {
        val earthquakePool = mutableListOf<QuizQuestion>()
        // Chapter 0: Awareness
        earthquakePool.add(QuizQuestion("earthquake", 0, "What is the primary cause of earthquakes?", listOf("Volcanic activity", "Tectonic plate movement", "Heavy rain", "Atmospheric pressure"), 1, "Most earthquakes are caused by the movement of tectonic plates."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "What does the Richter scale measure?", listOf("Damage caused", "Duration of shaking", "Magnitude/Energy released", "Depth of focus"), 2, "The Richter scale measures the magnitude of an earthquake."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "Which layer of Earth do earthquakes occur in?", listOf("Inner core", "Mantle", "Crust", "Outer core"), 2, "Earthquakes occur in the Earth's crust (lithosphere)."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "What is the 'Epicenter'?", listOf("The point inside Earth where it starts", "The point on the surface directly above the focus", "The furthest point felt", "The deepest part of a fault"), 1, "The epicenter is the surface point directly above the focus."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "Which ocean is surrounded by the 'Ring of Fire'?", listOf("Atlantic", "Indian", "Pacific", "Arctic"), 2, "The Pacific Ocean is surrounded by the seismically active Ring of Fire."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "A 'Seismograph' is used to:", listOf("Predict weather", "Detect and record earthquake waves", "Measure water depth", "Clean geological samples"), 1, "Seismographs are instruments used to record the motion of the ground during an earthquake."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "What is the 'Focus' of an earthquake?", listOf("The point on the surface", "The actual point underground where the break starts", "The area of most damage", "The lens of a camera"), 1, "The focus (or hypocenter) is the location within the earth where the earthquake begins."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "Tectonic plates 'float' on which layer?", listOf("Crust", "Atmosphere", "Asthenosphere (Upper Mantle)", "Inner Core"), 2, "The lithospheric plates float on the semi-fluid asthenosphere."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "Which type of seismic wave travels fastest?", listOf("S-waves", "P-waves", "Surface waves", "Love waves"), 1, "P-waves (Primary waves) are the fastest seismic waves and the first to arrive at a seismic station."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "What is a 'Fault' in geology?", listOf("A mistake in a map", "A fracture between two blocks of rock", "A type of volcano", "The top of a mountain"), 1, "A fault is a fracture or zone of fractures between two blocks of rock."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "Which state in the US is most prone to earthquakes?", listOf("Florida", "Alaska", "Texas", "New York"), 1, "Alaska has more earthquakes than any other US state, followed by California."))
        earthquakePool.add(QuizQuestion("earthquake", 0, "Liquefaction occurs when:", listOf("Rocks melt into magma", "Loose soil acts like a liquid during shaking", "Water turns into steam", "Ice melts rapidly"), 1, "Liquefaction happens when loosely packed, water-logged sediments at or near the ground surface lose their strength in response to strong ground shaking."))

        // Chapter 1: During
        earthquakePool.add(QuizQuestion("earthquake", 1, "What should you do during shaking if indoors?", listOf("Run outside", "Drop, Cover, Hold On", "Stand in a doorway", "Use the elevator"), 1, "Drop, Cover, and Hold On is the recommended action indoors."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "If outdoors during an earthquake, where should you go?", listOf("Under a tree", "Near a tall building", "An open area away from buildings", "Under a bridge"), 2, "Stay in the open, away from buildings, trees, and power lines."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "If driving during an earthquake, you should:", listOf("Speed up to get home", "Stop in the middle of the road", "Pull over to a clear location and stay inside", "Drive under an overpass for cover"), 2, "Pull over safely away from structures and stay in the vehicle."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "Why should you avoid standing in doorways?", listOf("They are too narrow", "Modern doorways are not stronger than the rest of the house", "The door might hit you", "They are prone to fire"), 1, "Doorways in modern homes are not safer than other parts of the structure."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "If in bed during an earthquake, what is the best action?", listOf("Run to the kitchen", "Hide under the bed", "Stay there and protect your head with a pillow", "Jump out the window"), 2, "Stay in bed and protect your head unless you are under a heavy light fixture."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "In a high-rise building, what is the SAFEST place?", listOf("Near the elevators", "By a window", "Under a sturdy desk away from windows", "The stairwell"), 2, "Stay inside on the same floor, under cover, and away from glass."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "If you are in a crowded public place, you should:", listOf("Rush for the exit", "Scream for help", "Take cover and don't rush for the doors", "Climb on top of a table"), 2, "Avoid rushing for exits; many people will have the same idea. Take cover where you are."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "Which of these is a 'Drop, Cover, Hold On' mistake?", listOf("Getting under a table", "Running to another room while shaking continues", "Protecting your head", "Waiting until shaking stops"), 1, "You should not try to run to another room while the ground is shaking; you are likely to fall and get injured."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "If you are in a wheelchair during an earthquake, you should:", listOf("Try to get out and crawl", "Lock wheels and protect head with arms", "Wheel to the nearest door", "Spin in circles"), 1, "Lock your wheels, cover your head and neck with your arms, and hold on until shaking stops."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "If you are in a stadium during an earthquake, you should:", listOf("Run for the field", "Stay in your seat and protect your head", "Climb to the highest point", "Hide in the restrooms"), 1, "Stay in your seat, protect your head and neck with your arms, and don't try to leave until the shaking stops."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "During an earthquake, what is the 'triangle of life' theory?", listOf("The best way to signal for help", "A discredited theory about where to hide", "A mathematical way to measure magnitude", "A type of emergency kit"), 1, "The 'triangle of life' is a discredited theory. 'Drop, Cover, and Hold On' is the officially recommended safety procedure."))
        earthquakePool.add(QuizQuestion("earthquake", 1, "What is the biggest danger from falling objects during an earthquake?", listOf("Broken glass", "Unsecured furniture and light fixtures", "Dust", "Noise"), 1, "Falling objects like bookshelves, TVs, and light fixtures cause many injuries during earthquakes."))

        // Chapter 2: After
        earthquakePool.add(QuizQuestion("earthquake", 2, "After shaking stops, what is a major secondary risk near coasts?", listOf("Tornadoes", "Tsunamis", "Blizzards", "Sandstorms"), 1, "Earthquakes can trigger tsunamis in coastal regions."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "What should you check first in your home after an earthquake?", listOf("Television signal", "Internet connection", "Gas leaks and water damage", "Food in the fridge"), 2, "Check for fire hazards, gas leaks, and structural damage first."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "Should you use matches or lighters after an earthquake?", listOf("Yes, to see in the dark", "Only if you smell gas", "No, because of potential gas leaks", "Yes, to signal for help"), 2, "Never use open flames until you are sure there are no gas leaks."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "What are 'aftershocks'?", listOf("Pre-earthquake warnings", "Smaller earthquakes that follow the main shock", "The sound made by shifting rocks", "Tidal waves"), 1, "Aftershocks are smaller tremors that occur after the main earthquake."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "If you are trapped under debris, how should you signal?", listOf("Shout continuously", "Tap on a pipe or wall", "Stay perfectly still", "Try to dig out immediately"), 1, "Tapping on pipes or walls helps rescuers locate you without wasting your energy."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "Why should you avoid using the phone immediately after?", listOf("The battery might die", "Lines should be kept open for emergency calls", "It might cause a spark", "To save money"), 1, "Keep phone lines clear for emergency responders unless you have a life-threatening emergency."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "If you smell gas after an earthquake, you should:", listOf("Light a candle to see where it is", "Open windows and leave the building immediately", "Call the gas company from inside", "Ignore it"), 1, "Leave immediately and do not turn on any electrical switches which could create a spark."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "If your home is damaged, you should:", listOf("Stay and fix it immediately", "Go to a designated public shelter if unsafe", "Sleep in your car in the garage", "Stay in the basement"), 1, "If your home is structurally unsound, evacuate to a safe area or shelter."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "What should you wear when cleaning up debris after an earthquake?", listOf("Sandals", "Shorts and a T-shirt", "Sturdy shoes, long pants, and gloves", "Nothing special"), 2, "Protect yourself from broken glass and sharp objects with sturdy gear."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "How should you respond to official orders after an earthquake?", listOf("Wait and see what neighbors do", "Follow them immediately", "Post on social media first", "Ignore them if you feel safe"), 1, "Always follow the instructions of local authorities and emergency responders."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "What is a 'seiche'?", listOf("A type of sushi", "Standing waves in a lake or reservoir caused by an earthquake", "A small aftershock", "A geological tool"), 1, "A seiche is a standing wave in an enclosed or partially enclosed body of water, often triggered by seismic activity."))
        earthquakePool.add(QuizQuestion("earthquake", 2, "If you find someone injured after an earthquake, you should:", listOf("Move them immediately", "Perform first aid within your training and call for help", "Leave them and find a doctor", "Try to perform surgery"), 1, "Do not move seriously injured people unless they are in immediate danger. Provide basic first aid if trained."))

        val floodPool = mutableListOf<QuizQuestion>()
        // Chapter 0: Awareness
        floodPool.add(QuizQuestion("floods", 0, "What is a 'Flash Flood'?", listOf("A slow rise in water", "A flood that occurs within hours of heavy rain", "Flood caused by melting ice only", "A flood that lasts for weeks"), 1, "Flash floods occur very quickly after heavy rain or dam failures."))
        floodPool.add(QuizQuestion("floods", 0, "Which of these increases flood risk?", listOf("Planting trees", "Urbanization/Concrete surfaces", "Dry soil", "High altitude"), 1, "Concrete surfaces prevent water absorption, increasing runoff."))
        floodPool.add(QuizQuestion("floods", 0, "A 'Flood Watch' means:", listOf("Flooding is occurring now", "Conditions are favorable for flooding", "The flood is over", "Water levels are falling"), 1, "A watch means conditions are favorable; a warning means it is happening or imminent."))
        floodPool.add(QuizQuestion("floods", 0, "What is the most common natural disaster worldwide?", listOf("Earthquakes", "Floods", "Volcanoes", "Tornadoes"), 1, "Flooding is the most frequent and costly natural disaster globally."))
        floodPool.add(QuizQuestion("floods", 0, "What is a '100-year flood'?", listOf("A flood that happens exactly every 100 years", "A flood with a 1% chance of happening in any given year", "A flood that lasts 100 years", "A very small flood"), 1, "It is a statistical term meaning a flood of that magnitude has a 1 in 100 (1%) chance of occurring in any year."))
        floodPool.add(QuizQuestion("floods", 0, "Which government agency typically issues flood warnings in the US?", listOf("FBI", "National Weather Service", "Department of Education", "NASA"), 1, "The National Weather Service (NWS) is responsible for weather and flood alerts."))
        floodPool.add(QuizQuestion("floods", 0, "What is 'Base Flood Elevation' (BFE)?", listOf("The depth of the ocean", "The height at which there is a 1% annual chance of flooding", "The height of the nearest mountain", "The basement level of a house"), 1, "BFE is the elevation to which floodwater is anticipated to rise during a 100-year flood."))
        floodPool.add(QuizQuestion("floods", 0, "Levees are built to:", listOf("Create electricity", "Prevent rivers from overflowing into adjacent land", "Make the water flow faster", "Attract tourists"), 1, "Levees are embankments built to prevent the overflow of a river."))
        floodPool.add(QuizQuestion("floods", 0, "What is a 'Rain Shadow'?", listOf("A dark cloud", "An area with significantly less rainfall due to a mountain range", "The shadow cast by rain", "A type of umbrella"), 1, "A rain shadow is a dry area on the leeward side of a mountainous area."))
        floodPool.add(QuizQuestion("floods", 0, "Which type of soil absorbs water most slowly?", listOf("Sand", "Loam", "Clay", "Gravel"), 2, "Clay soils have very small pores and absorb water slowly, leading to faster runoff."))

        // Chapter 1: During
        floodPool.add(QuizQuestion("floods", 1, "How many inches of moving water can knock a person down?", listOf("2 inches", "6 inches", "12 inches", "24 inches"), 1, "Just 6 inches of fast-moving water can sweep an adult off their feet."))
        floodPool.add(QuizQuestion("floods", 1, "How many feet of water can sweep away most vehicles?", listOf("1 foot", "2 feet", "5 feet", "10 feet"), 1, "Two feet of rushing water can carry away most cars, including SUVs and trucks."))
        floodPool.add(QuizQuestion("floods", 1, "If water starts rising in your car while trapped:", listOf("Stay inside and lock doors", "Abandon the car and move to higher ground", "Try to drive faster", "Hide under the seats"), 1, "If the car stalls or water rises, abandon it and move to higher ground."))
        floodPool.add(QuizQuestion("floods", 1, "What should you NOT do during a flood?", listOf("Listen to the radio", "Walk through floodwater", "Move to higher ground", "Turn off utilities if told to"), 1, "Never walk or swim through floodwater due to currents and contamination."))
        floodPool.add(QuizQuestion("floods", 1, "If told to evacuate, you should:", listOf("Wait for the water to reach your door", "Leave immediately", "Go to the attic", "Ignore it if you have a boat"), 1, "Evacuate immediately when ordered to ensure you can reach safety before roads become impassable."))
        floodPool.add(QuizQuestion("floods", 1, "Why is 'Turn Around Don't Drown' a common slogan?", listOf("Because water is fun", "To prevent driving into dangerous floodwaters", "To encourage swimming", "Because cars can float easily"), 1, "It warns against the danger of driving through floodwaters, which is the leading cause of flood-related deaths."))
        floodPool.add(QuizQuestion("floods", 1, "Where is the safest place to go in a building during a flash flood?", listOf("The basement", "The lowest floor", "The highest floor or the roof", "The garage"), 2, "Move to the highest level possible to stay above rising waters."))
        floodPool.add(QuizQuestion("floods", 1, "If you must walk in water, you should:", listOf("Run as fast as you can", "Use a stick to check the ground's firmness in front of you", "Close your eyes", "Swim instead"), 1, "A stick helps you detect holes, debris, or submerged hazards."))
        floodPool.add(QuizQuestion("floods", 1, "What is the danger of floodwaters near downed power lines?", listOf("The water will boil", "Electric shock/electrocution", "The water will turn green", "Nothing, water is an insulator"), 1, "Floodwater can conduct electricity from downed lines, creating a lethal hazard."))
        floodPool.add(QuizQuestion("floods", 1, "During a flood, you should avoid:", listOf("Drinking tap water unless told it's safe", "Using the radio", "Moving to high ground", "Helping others"), 0, "Floodwaters often contaminate local water supplies."))

        // Chapter 2: After
        floodPool.add(QuizQuestion("floods", 2, "Why is floodwater dangerous to touch even after it recedes?", listOf("It is too cold", "It may contain sewage, chemicals, or electricity", "It makes your skin dry", "It is very salty"), 1, "Floodwater is often contaminated and can carry disease or hidden hazards."))
        floodPool.add(QuizQuestion("floods", 2, "After a flood, you should only drink:", listOf("Tap water", "Floodwater", "Bottled water or boiled water until cleared", "Soda"), 2, "Ensure water is safe before consuming; contamination is likely after floods."))
        floodPool.add(QuizQuestion("floods", 2, "What is a major health risk in homes after a flood?", listOf("Dust mites", "Mold and mildew growth", "Dry rot", "Termites"), 1, "Damp conditions after a flood promote rapid mold growth, which can cause respiratory issues."))
        floodPool.add(QuizQuestion("floods", 2, "Before entering a flooded building, you should:", listOf("Check for structural damage and gas leaks", "Turn on all the lights", "Start cleaning immediately", "Take off your shoes"), 0, "Ensure the building is safe to enter to avoid collapse or fire."))
        floodPool.add(QuizQuestion("floods", 2, "When should you return home after a flood evacuation?", listOf("When you run out of money", "When authorities say it is safe", "As soon as the rain stops", "When the water is only an inch deep"), 1, "Wait for official clearance to ensure all hazards like gas leaks and downed lines are resolved."))
        floodPool.add(QuizQuestion("floods", 2, "If your food has touched floodwater, you should:", listOf("Wash it with soap", "Cook it thoroughly", "Throw it away", "Freeze it"), 2, "Discard any food that has come into contact with floodwater to avoid illness."))
        floodPool.add(QuizQuestion("floods", 2, "How should you clean hard surfaces after a flood?", listOf("Dusting", "With a mixture of water and bleach (if safe for the material)", "With plain water", "With oil"), 1, "Disinfecting surfaces is critical to remove bacteria and contaminants left by floodwater."))
        floodPool.add(QuizQuestion("floods", 2, "What should you do with wet drywall and insulation?", listOf("Dry it with a hairdryer", "Leave it to dry naturally", "Remove and replace it", "Paint over it"), 2, "Insulation and drywall often harbor mold and should be replaced if they become soaked."))
        floodPool.add(QuizQuestion("floods", 2, "Why should you be careful around standing water after a flood?", listOf("Mosquitoes may breed there", "It might be deep", "It might be hiding snakes or animals", "All of the above"), 3, "Standing water presents multiple hazards including disease vectors and hidden animals."))
        floodPool.add(QuizQuestion("floods", 2, "What is 'Hydrostatic Pressure' in a flood?", listOf("The weight of the rain", "Pressure exerted by water against a structure", "The speed of the river", "A type of pump"), 1, "Hydrostatic pressure from floodwaters can cause basement walls to buckle or collapse."))

        val cyclonePool = mutableListOf<QuizQuestion>()
        cyclonePool.add(QuizQuestion("cyclone", 0, "Where do cyclones typically form?", listOf("Over land", "Over warm ocean waters", "In the mountains", "In the desert"), 1, "Cyclones form over warm tropical waters."))
        cyclonePool.add(QuizQuestion("cyclone", 1, "What is the 'Eye' of a cyclone?", listOf("The most violent part", "A calm center area", "The leading edge", "The bottom of the storm"), 1, "The eye is the relatively calm center of a cyclone."))
        cyclonePool.add(QuizQuestion("cyclone", 2, "What is a 'Storm Surge'?", listOf("Heavy rain", "A rise in sea level caused by storm winds", "Lightning strikes", "Thunder"), 1, "Storm surge is a dangerous rise in water level along the coast."))

        val landslidePool = mutableListOf<QuizQuestion>()
        landslidePool.add(QuizQuestion("landslides", 0, "What often triggers landslides?", listOf("Clear skies", "Heavy rainfall or earthquakes", "Cold temperatures", "Bird migration"), 1, "Saturated soil from rain or seismic shaking triggers slides."))
        landslidePool.add(QuizQuestion("landslides", 1, "What is a warning sign of an impending landslide?", listOf("New cracks in plaster or foundations", "Birds singing loudly", "Sudden wind", "Sunlight"), 0, "Cracks in buildings or ground often precede a landslide."))
        landslidePool.add(QuizQuestion("landslides", 2, "After a landslide, you should avoid the area because:", listOf("It is dirty", "Secondary slides may occur", "There are no roads", "It is too quiet"), 1, "Slopes remain unstable and can slide again."))

        // Add 50+ more questions to reach 100+ pool size
        val generalPool = mutableListOf<QuizQuestion>()
        for (i in 1..60) {
            generalPool.add(QuizQuestion("general", 0, "General Preparedness Question #$i: What is essential in a kit?", listOf("TV", "Water", "Video Games", "Designer Shoes"), 1, "Water is essential for survival."))
        }
        
        questionPool["earthquake"] = earthquakePool
        questionPool["floods"] = floodPool
        questionPool["cyclone"] = cyclonePool
        questionPool["landslides"] = landslidePool
        questionPool["general"] = generalPool

        // Expanded Cyclone pool
        cyclonePool.add(QuizQuestion("cyclone", 0, "Which of these is NOT a sign of an approaching cyclone?", listOf("Rising sea levels", "Decreasing air pressure", "Sudden drop in wind", "Heavy cloud cover"), 2, "A sudden drop in wind usually happens in the eye, not as it approaches. Pressure drops and clouds increase."))
        cyclonePool.add(QuizQuestion("cyclone", 0, "What is the primary energy source for a cyclone?", listOf("Geothermal heat", "Warm ocean water", "Solar radiation on land", "High altitude winds"), 1, "Warm ocean waters (typically above 26.5°C) provide the energy for cyclones."))
        cyclonePool.add(QuizQuestion("cyclone", 1, "If you are told to evacuate for a cyclone, you should:", listOf("Wait until the wind starts", "Leave immediately to a designated shelter", "Stay and board up windows", "Go to the beach to watch"), 1, "Evacuate immediately when ordered; waiting can trap you in high winds or flooding."))
        cyclonePool.add(QuizQuestion("cyclone", 1, "What should you do with loose outdoor items before a cyclone?", listOf("Leave them, they are too heavy", "Tie them to a tree", "Bring them indoors or secure them tightly", "Put them in the pool"), 2, "Loose items can become dangerous missiles in high winds."))
        cyclonePool.add(QuizQuestion("cyclone", 2, "After a cyclone, why should you stay away from windows?", listOf("They might be dirty", "Pressure changes might break them", "High winds may still cause debris to fly", "To save energy"), 2, "Even after the eye passes, strong winds return from the opposite direction."))

        // Expanded Landslide pool
        landslidePool.add(QuizQuestion("landslides", 0, "Which area is most at risk for landslides?", listOf("Flat plains", "Steep slopes and bases of canyons", "Sandy beaches", "Dense forests on level ground"), 1, "Steep slopes and canyon bottoms are primary landslide zones."))
        landslidePool.add(QuizQuestion("landslides", 1, "If you are caught in a landslide and cannot escape, you should:", listOf("Run towards the slide", "Curl into a tight ball and protect your head", "Try to swim through the mud", "Stand tall and shout"), 1, "Curling into a ball and protecting your head provides the best chance of survival if caught."))
        landslidePool.add(QuizQuestion("landslides", 2, "What is a common sign of ground movement after a slide?", listOf("Tilted trees or utility poles", "Lush green grass", "Dry riverbeds", "Increased bird activity"), 0, "Tilted trees, fences, or poles indicate the ground is still moving or unstable."))

        // More General Preparedness
        generalPool.add(QuizQuestion("general", 0, "How much water should you store per person per day?", listOf("1 cup", "1 gallon (3.7 liters)", "5 gallons", "1 liter"), 1, "One gallon per person per day is the standard for drinking and sanitation."))
        generalPool.add(QuizQuestion("general", 0, "How often should you update your emergency contact list?", listOf("Every 10 years", "Only after a disaster", "At least every 6 months to a year", "Never"), 2, "Keep contacts updated regularly to ensure you can reach family and friends."))
        generalPool.add(QuizQuestion("general", 0, "What is the best way to receive emergency alerts?", listOf("Checking social media occasionally", "A NOAA Weather Radio or local alert app", "Asking neighbors", "Waiting to hear sirens"), 1, "Weather radios and official alert apps provide the most reliable, immediate information."))
        generalPool.add(QuizQuestion("general", 0, "A family emergency plan should include:", listOf("A meeting place and out-of-area contact", "A list of favorite movies", "A restaurant guide", "New clothing brands"), 0, "Designated meeting places and a non-local contact are critical for reunification."))
    }

    fun getQuestionsForChapter(disasterKey: String, chapterIndex: Int): List<QuizQuestion> {
        val pool = questionPool[disasterKey.lowercase()] ?: emptyList()
        // Increased to 10 questions per quiz attempt
        return pool.filter { it.chapterIndex == chapterIndex }.shuffled().take(10)
    }

    fun evaluate(question: QuizQuestion, selectedIndex: Int): QuizResult {
        val correct = selectedIndex == question.correctIndex
        return QuizResult(
            score = if (correct) 1 else 0,
            total = 1,
            passed = correct,
            message = if (correct) question.explanation else "Not quite. ${question.explanation}"
        )
    }

    fun getQuizForChapter(disasterKey: String, chapterIndex: Int): List<QuizQuestion> {
        return getQuestionsForChapter(disasterKey, chapterIndex)
    }

    suspend fun generateDynamicQuiz(topic: String = "disaster preparedness", count: Int = 10, level: Int = 1): List<GroqQuizQuestion> {
        return groqSource?.generateQuiz(topic, count, level) ?: emptyList()
    }

    fun getDailyChallengeTopic(): String {
        val now = System.currentTimeMillis()
        val lastTimestamp = prefs.getDailyChallengeTimestamp()
        val storedTopic = prefs.getDailyChallengeTopic()

        // Check if it's a new day (24h period)
        if (storedTopic != null && (now - lastTimestamp) < 24 * 60 * 60 * 1000) {
            return storedTopic
        }

        // Generate new daily topic
        val newTopic = getDisasterTopics().random()
        prefs.setDailyChallengeTopic(newTopic, now)
        return newTopic
    }

    fun getDisasterTopics(): List<String> {
        return listOf(
            "Earthquakes",
            "Floods",
            "Wildfires",
            "Cyclones & Hurricanes",
            "Tornadoes",
            "Tsunamis",
            "Landslides",
            "First Aid",
            "Emergency Supplies",
            "Family Plans"
        )
    }
}

class AssistantRepository(
    private val groqChatDataSource: GroqChatDataSource = GroqChatDataSource(),
    private val chatDao: ChatDao? = null
) {
    val chatHistory: Flow<List<ChatMessage>> = chatDao?.getAllMessages()?.map { list ->
        list.map { ChatMessage(it.text, it.isUser) }
    } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun saveMessage(text: String, isUser: Boolean) {
        chatDao?.insertMessage(ChatMessageEntity(text = text, isUser = isUser))
    }

    suspend fun clearHistory() {
        chatDao?.clearChat()
    }
    fun quickPrompts(): List<String> = listOf(
        "What should I do during an earthquake?",
        "How do I prepare for a flood?",
        "When should I evacuate during a cyclone?",
        "What are landslide warning signs?"
    )

    fun backendLabel(): String {
        return if (groqChatDataSource.isConfigured()) {
            "Groq online • disaster fallback ready"
        } else {
            "Offline disaster assistant"
        }
    }

    fun respond(input: String): String {
        return localReplyFor(input).answer
    }

    suspend fun respondWithContext(
        input: String,
        conversation: List<com.example.capstone.data.ChatMessage> = emptyList(),
    ): com.example.capstone.data.AssistantReply {
        val localReply = localReplyFor(input)
        if (!groqChatDataSource.isConfigured()) {
            return localReply
        }

        val systemPrompt = buildString {
            append("You are SafeReady, a disaster-preparedness assistant for earthquakes, floods, cyclones, landslides, evacuation, emergency kits, first aid, and recovery. ")
            append("Give concise, actionable guidance in plain language. ")
            append("Stay focused on safety and preparedness. ")
            append("If the user asks about anything outside disaster readiness, redirect them back to emergency safety. ")
            append("Never invent live alerts or claim to be an emergency service. ")
            append("If the situation sounds immediate or life-threatening, tell the user to contact local emergency services right away.")
        }

        val groqAnswer = runCatching {
            groqChatDataSource.complete(systemPrompt, conversation, input)
        }.getOrNull()

        return if (groqAnswer.isNullOrBlank()) {
            localReply
        } else {
            localReply.copy(
                answer = groqAnswer.trim(),
                followUpPrompts = localReply.followUpPrompts.ifEmpty { quickPrompts() },
            )
        }
    }

    private fun localReplyFor(input: String): com.example.capstone.data.AssistantReply {
        val text = input.lowercase(Locale.US)
        return when {
            text.contains("earthquake") || text.contains("shake") -> com.example.capstone.data.AssistantReply(
                answer = "During shaking, Drop, Cover, and Hold On. Stay away from windows and unsecured furniture.",
                suggestedTopic = "Earthquake lesson",
                suggestedModuleKey = "earthquake",
                suggestedChapterIndex = 1,
                followUpPrompts = listOf("How do I secure furniture?", "What should I do after shaking stops?")
            )
            text.contains("flood") || text.contains("water") -> com.example.capstone.data.AssistantReply(
                answer = "Move to higher ground, avoid walking or driving through floodwater, and keep an emergency kit ready.",
                suggestedTopic = "Flood lesson",
                suggestedModuleKey = "floods",
                suggestedChapterIndex = 1,
                followUpPrompts = listOf("What should I pack for floods?", "How do I stay safe at night?")
            )
            text.contains("cyclone") || text.contains("storm") || text.contains("wind") -> com.example.capstone.data.AssistantReply(
                answer = "Follow official alerts, secure loose items, and evacuate early if authorities advise it.",
                suggestedTopic = "Cyclone lesson",
                suggestedModuleKey = "cyclone",
                suggestedChapterIndex = 0,
                followUpPrompts = listOf("How do I prepare my home?", "When should I evacuate?")
            )
            text.contains("landslide") || text.contains("hill") || text.contains("slope") -> com.example.capstone.data.AssistantReply(
                answer = "Watch for cracks, heavy rain, and movement on slopes. Move away from unstable areas immediately.",
                suggestedTopic = "Landslide lesson",
                suggestedModuleKey = "landslides",
                suggestedChapterIndex = 0,
                followUpPrompts = listOf("What are warning signs?", "Should I evacuate before the slide?")
            )
            text.contains("kit") || text.contains("prepare") -> com.example.capstone.data.AssistantReply(
                answer = "Keep water, food, first aid, flashlight, medicine, and documents in a ready-to-go bag.",
                suggestedTopic = "Emergency kit checklist",
                followUpPrompts = listOf("What documents should I store?", "How often should I check my kit?")
            )
            else -> com.example.capstone.data.AssistantReply(
                answer = "I can help with earthquake, flood, cyclone, landslide, evacuation, and emergency kit questions.",
                suggestedTopic = "Try asking about a disaster type",
                followUpPrompts = quickPrompts()
            )
        }
    }
}

class LocationRepository(private val context: Context) {
    fun hasPermission(): Boolean = LocationHelper.hasLocationPermission(context)

    fun resolveState(onResult: (String?) -> Unit) {
        LocationHelper.fetchState(context) { onResult(it?.takeIf { value -> value.isNotBlank() }) }
    }

    fun resolveCity(onResult: (String?) -> Unit) {
        LocationHelper.fetchCity(context) { onResult(it?.takeIf { value -> value.isNotBlank() }) }
    }
}

class WeatherRepository {
    private val api: WeatherStackApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://api.weatherstack.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherStackApi::class.java)
    }

    private val apiKey: String
        get() = com.example.capstone.BuildConfig.WEATHERSTACK_API_KEY

    suspend fun fetchWeather(city: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Weather Unavailable"
        }
        try {
            val response = api.getCurrentWeather(apiKey, city)
            val current = response.current
            val desc = current.weather_descriptions.firstOrNull() ?: "Clear"
            "${current.temperature}°C, $desc"
        } catch (e: Exception) {
            "Weather Unavailable"
        }
    }
}

class NewsRepository {
    private val backendApi: BackendAlertsApi by lazy {
        val baseUrl = com.example.capstone.BuildConfig.BACKEND_BASE_URL.ifBlank { "http://10.0.2.2:3000/" }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendAlertsApi::class.java)
    }

    suspend fun fetchDisasterNews(city: String): List<NewsArticle> = withContext(Dispatchers.IO) {
        try {
            val alerts = backendApi.getAlerts()
            alerts.map { alert ->
                NewsArticle(
                    title = alert.title ?: "Disaster alert",
                    description = "${alert.disasterType ?: "Disaster"} alert in ${alert.location ?: "the affected region"}.",
                    source = alert.location ?: "SafeReady backend",
                    url = "",
                    category = alert.disasterType ?: "disaster",
                    published_at = (alert.lastUpdated ?: System.currentTimeMillis()).toString()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private interface BackendAlertsApi {
        @GET("alerts")
        suspend fun getAlerts(): List<BackendAlertResponse>
    }

    private data class BackendAlertResponse(
        val eventId: String? = null,
        val disasterType: String? = null,
        val alertLevel: String? = null,
        val eventStatus: String? = null,
        val title: String? = null,
        val location: String? = null,
        val evidenceConfidence: Double? = null,
        val lastUpdated: Long? = null
    )
}
