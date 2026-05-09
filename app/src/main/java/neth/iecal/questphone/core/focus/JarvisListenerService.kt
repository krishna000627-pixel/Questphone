package neth.iecal.questphone.core.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.QuestRepositoryEntryPoint
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.repositories.UserRepositoryEntryPoint
import neth.iecal.questphone.core.ai.GemmaRepository
import neth.iecal.questphone.core.ai.GemmaRepositoryEntryPoint
import nethical.questphone.core.R as coreR
import nethical.questphone.core.core.utils.getCurrentDate
import java.util.Locale

const val ACTION_START       = "jarvis_start"
const val ACTION_STOP        = "jarvis_stop"
const val ACTION_LISTEN_ONCE = "jarvis_listen_once"

private const val NOTIF_CHANNEL = "kai_voice"
private const val NOTIF_ID = 456

// No @AndroidEntryPoint - use EntryPointAccessors for manual injection
class JarvisListenerService : Service() {

    // Injected lazily via EntryPointAccessors - no Hilt annotation needed
    private val userRepository: UserRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            UserRepositoryEntryPoint::class.java
        ).userRepository()
    }
    private val questRepository: QuestRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            QuestRepositoryEntryPoint::class.java
        ).questRepository()
    }
    private val gemmaRepository: GemmaRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            GemmaRepositoryEntryPoint::class.java
        ).gemmaRepository()
    }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var continuousMode = false
    @Volatile private var isSpeaking = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
        initTts()
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                NOTIF_CHANNEL, "Kai Voice", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Kai voice assistant" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun showListeningNotif() {
        val notif = androidx.core.app.NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(coreR.drawable.baseline_info_24)
            .setContentTitle("Kai is listening")
            .setContentText("Say your wake word")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
                // Restart mic AFTER TTS finishes so Kai's voice isn't picked up
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (continuousMode) startOneListen()
                        }, 400L)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    fun speak(text: String) {
        if (!isTtsReady) return
        val clean = text
            .replace(Regex("[*_#`~>]"), "")
            .replace(Regex("[\uD83C-\uDBFF\uDC00-\uDFFF]+"), "")
            .replace(Regex("\\s+"), " ").trim().take(300)
        // Stop mic BEFORE speaking - UtteranceProgressListener restarts it when done
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.cancel() } catch (_: Exception) {}
        isSpeaking = true
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "kai_${System.currentTimeMillis()}")
    }

    private fun setupRecognizer() {
        destroyRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: run {
                            if (continuousMode) scheduleNextListen() else stopForeground(true)
                            return
                        }
                    handleVoiceResults(matches)
                    // Only restart mic here if speak() wasn't called (utterance listener handles that case)
                    if (continuousMode && !isSpeaking) scheduleNextListen(1500L)
                    else stopForeground(true)
                }
                override fun onError(error: Int) {
                    if (continuousMode) scheduleNextListen(2000L)
                    else stopForeground(true)
                }
                override fun onReadyForSpeech(p: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: android.os.Bundle?) {}
                override fun onEvent(t: Int, b: android.os.Bundle?) {}
            })
        }
    }

    private fun scheduleNextListen(delayMs: Long = 500L) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (continuousMode) startOneListen()
        }, delayMs)
    }

    private fun startOneListen() {
        if (recognizer == null) setupRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) {}
    }

    private fun handleVoiceResults(matches: List<String>) {
        val u = userRepository.userInfo
        val wakeWord = u.jarvisWakeWord.ifBlank { "kai" }

        for (text in matches) {
            val lower = text.lowercase()

            // Custom voice actions first
            for (action in u.customVoiceActions) {
                if (action.phrase.isNotBlank() && lower.contains(action.phrase.lowercase())) {
                    try {
                        packageManager.getLaunchIntentForPackage(action.packageName)
                            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            ?.let { startActivity(it) }
                    } catch (_: Exception) {}
                    return
                }
            }

            if (!lower.contains(wakeWord)) continue

            // External assistant package override
            val assistantPkg = u.aiAssistantPackage
            if (assistantPkg.isNotBlank()) {
                try {
                    packageManager.getLaunchIntentForPackage(assistantPkg)
                        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        ?.let { startActivity(it); return }
                } catch (_: Exception) {}
            }

            val query = lower.substringAfter(wakeWord).trim()
                .ifBlank { "what can I help with?" }

            val quickReply = handleQuickQuery(query)
            if (quickReply != null) {
                speak(quickReply)
                return
            }

            scope.launch {
                speak("On it...")
                gemmaRepository.chat(emptyList(), query)
                    .onSuccess { resp -> speak(resp.text) }
                    .onFailure { speak("I couldn't connect right now.") }
            }
            return
        }
    }

    /** Opens any installed app by fuzzy name match. Returns spoken confirmation or null. */
    private fun openAppByName(appName: String): String? {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val allApps = pm.queryIntentActivities(launcherIntent, 0)
        
        // Try exact → starts with → contains (in order of confidence)
        val match = allApps.firstOrNull { it.loadLabel(pm).toString().lowercase() == appName }
            ?: allApps.firstOrNull { it.loadLabel(pm).toString().lowercase().startsWith(appName) }
            ?: allApps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(appName) }
            // Also try individual words: "a code" -> "acode"
            ?: allApps.firstOrNull {
                val label = it.loadLabel(pm).toString().lowercase().replace(" ", "")
                val query = appName.replace(" ", "")
                label.contains(query) || query.contains(label)
            }

        return if (match != null) {
            pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?.let { startActivity(it) }
            "Opening ${match.loadLabel(pm)}"
        } else {
            "App not found: $appName"
        }
    }

    private fun handleQuickQuery(query: String): String? {
        val lower = query.lowercase()
        return when {
            "next quest" in lower || "my quest" in lower || "today quest" in lower -> {
                val quests = try {
                    kotlinx.coroutines.runBlocking { questRepository.getAllQuestsAsList() }
                        .filter { !it.is_destroyed && it.last_completed_on != getCurrentDate() }
                        .take(3)
                } catch (_: Exception) { emptyList() }
                if (quests.isEmpty()) "No pending quests! Great job!"
                else "Your next quests are: ${quests.joinToString(", ") { it.title }}"
            }
            "my coins" in lower || "how many coins" in lower ->
                "You have ${userRepository.userInfo.coins} coins."
            "my streak" in lower ->
                "Your streak is ${userRepository.userInfo.streak.currentStreak} days!"
            "my level" in lower -> "You are level ${userRepository.userInfo.level}!"
            lower.startsWith("open ") -> {
                val appName = lower.removePrefix("open ").trim()
                openAppByName(appName)
            }
            else -> null
        }
    }

    private fun destroyRecognizer() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                continuousMode = true
                setupRecognizer()
                showListeningNotif()
                startOneListen()
            }
            ACTION_LISTEN_ONCE -> {
                continuousMode = false
                setupRecognizer()
                showListeningNotif()
                startOneListen()
            }
            ACTION_STOP -> {
                continuousMode = false
                destroyRecognizer()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        continuousMode = false
        destroyRecognizer()
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }
}
