package neth.iecal.questphone.core.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import neth.iecal.questphone.core.rpg.AppNameResolver
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.questphone.MainActivity
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.app.screens.launcher.QuestReminderActivity
import neth.iecal.questphone.core.focus.BacklogWallActivity
import neth.iecal.questphone.core.focus.isFocusMode
import neth.iecal.questphone.core.focus.STUDY_WHITELIST
import neth.iecal.questphone.backed.repositories.QuestRepositoryEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import neth.iecal.questphone.core.ai.GemmaRepository
import neth.iecal.questphone.core.ai.GemmaRepositoryEntryPoint
import nethical.questphone.core.R
import nethical.questphone.core.core.utils.getCurrentDate
import nethical.questphone.core.core.utils.managers.getKeyboards
import nethical.questphone.core.core.utils.managers.reloadApps
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import neth.iecal.questphone.backed.sync.RenderSyncManager
import neth.iecal.questphone.backed.sync.RenderSyncPrefs
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

// Actions that used to target the separate JarvisListenerService.
// Kept identical so existing call sites only need a class-name swap.
const val ACTION_JARVIS_START       = "jarvis_start"
const val ACTION_JARVIS_STOP        = "jarvis_stop"
const val ACTION_JARVIS_LISTEN_ONCE = "jarvis_listen_once"

class AppBlockerService : Service() {

    private val TAG = "AppBockServiceFG"
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var handler: Handler
    private var timerRunnable: Runnable? = null
    private var lastForegroundPackage: String? = null
    private var isTimerRunning = false
    private var timerRunningForPackage = ""

    private val userRepository: UserRepository by lazy {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            applicationContext,
            neth.iecal.questphone.backed.repositories.UserRepositoryEntryPoint::class.java
        ).userRepository()
    }

    // --- Merged from JarvisListenerService: keeps both features under one
    // foreground service so only one ongoing notification is ever shown. ---
    private val questRepository: neth.iecal.questphone.backed.repositories.QuestRepository by lazy {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            applicationContext,
            neth.iecal.questphone.backed.repositories.QuestRepositoryEntryPoint::class.java
        ).questRepository()
    }
    private val gemmaRepository: GemmaRepository by lazy {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            applicationContext,
            GemmaRepositoryEntryPoint::class.java
        ).gemmaRepository()
    }
    private var jarvisRecognizer: SpeechRecognizer? = null
    private var jarvisTts: TextToSpeech? = null
    private var jarvisTtsReady = false
    private var jarvisContinuousMode = false
    @Volatile private var jarvisIsSpeaking = false
    private val jarvisScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )
    @Volatile private var jarvisListening = false

    // ── Live-sync WebSocket ───────────────────────────────────────────────────
    private val wsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsClient: OkHttpClient? = null
    private var wsSocket: WebSocket? = null
    @Volatile private var wsConnected = false
    private var wsReconnectJob: Job? = null
    private var wsBackoffMs = 2_000L          // starts at 2 s, caps at 5 min
    private val WS_BACKOFF_MAX_MS = 300_000L
    private val WS_URL = "wss://questphone-sync.onrender.com/sync/live"
    // ─────────────────────────────────────────────────────────────────────────

    // Default locked apps - will be overridden by saved preferences
    private val lockedApps = mutableSetOf<String>()
    private var lastReminderPkg = ""  // avoid re-showing for same app session
    private var lastReminderTime = 0L


    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AppBlockService"
        const val NOTIFICATION_ID = 123
        var isOverlayActive = false
        var currentLockedPackage: String? = null

        // Polling intervals
        private const val STANDARD_POLLING_INTERVAL_MS = 100L

    }

    override fun onCreate() {
        super.onCreate()
        // Channel MUST exist before startForeground() — create it first
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires foreground service type
            startForeground(NOTIFICATION_ID, createNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC or FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            // Android 12/13 — no type needed, using type causes Bad notification crash
            startForeground(NOTIFICATION_ID, createNotification())
        }
        Log.d(TAG, "AppBlockService onCreate")
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        handler = Handler(Looper.getMainLooper())
        setupBroadcastListeners()
        loadLockedApps()
        loadUnlockedAppsFromServer()
        if (AppBlockerServiceInfo.deepFocus.isRunning) turnDeepFocus()

        startMonitoringApps()
        AppBlockerServiceInfo.appBlockerService = this
        jarvisInitTts()
        wsConnect()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun setupBroadcastListeners() {
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_APP_BLOCKER)
            addAction(INTENT_ACTION_UNLOCK_APP)
            addAction(INTENT_ACTION_START_DEEP_FOCUS)
            addAction(INTENT_ACTION_STOP_DEEP_FOCUS)
        }

        Log.d("AppBlockerSrvieFg", "registering reciever")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(refreshReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("AppBlockerService", "Failed to register receiver: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        jarvisHandleAction(intent?.action)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(appMonitorRunnable)
        AppBlockerServiceInfo.appBlockerService = null
        showHomwScreenOverlay()
        // remove the notification when service is destroyed
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)

        unregisterReceiver(refreshReceiver)

        // WebSocket cleanup
        wsDisconnect()
        wsScope.cancel()

        // Jarvis cleanup
        jarvisContinuousMode = false
        jarvisDestroyRecognizer()
        jarvisTts?.stop(); jarvisTts?.shutdown(); jarvisTts = null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "App Block Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        // Add description to make the channel's purpose clear
        serviceChannel.description = "Allows the appblocker to be run"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_info_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)

        if (jarvisListening) {
            builder.setContentTitle("Protecting your time")
                .setContentText("Kai is listening · say your wake word")
        } else {
            builder.setContentTitle("Protecting your time")
                .setContentText("QuestPhone is active")
        }

        // Create a PendingIntent for when the notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    /** Re-renders the single shared notification to reflect current state
     * (called whenever Kai starts/stops listening) instead of starting a
     * second foreground notification. */
    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification())
    }

    private fun startMonitoringApps() {
        handler.post(appMonitorRunnable)
    }

    // ── Live-sync WebSocket implementation ───────────────────────────────────

    /** Opens (or re-opens) the WebSocket to the Render sync server.
     *  Called from onCreate and from the reconnect loop. */
    private fun wsConnect() {
        if (!RenderSyncPrefs.isEnabled(applicationContext)) return

        wsClient = OkHttpClient.Builder()
            .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val token  = RenderSyncPrefs.getSyncToken(applicationContext)
        val userId = RenderSyncPrefs.getUserId(applicationContext)

        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("x-sync-token", token)
            .addHeader("x-device-id", userId)
            .build()

        wsSocket = wsClient!!.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                wsConnected = true
                wsBackoffMs  = 2_000L          // reset backoff on successful connect
                wsReconnectJob?.cancel()
                Log.d(TAG, "WS: connected to $WS_URL")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS: message → $text")
                // Any message from the server means the other device pushed new data.
                // Fire a pull() so this device catches up immediately.
                wsTriggerPull()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "WS: binary message (${bytes.size} bytes)")
                wsTriggerPull()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                wsConnected = false
                Log.w(TAG, "WS: failure — ${t.message}; reconnecting in ${wsBackoffMs}ms")
                wsScheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                wsConnected = false
                Log.d(TAG, "WS: closed ($code $reason)")
                if (code != 1000) wsScheduleReconnect()
            }
        })
    }

    /** Gracefully closes the current socket without scheduling a reconnect. */
    private fun wsDisconnect() {
        wsReconnectJob?.cancel()
        wsSocket?.close(1000, "Service destroyed")
        wsSocket = null
        wsClient?.dispatcher?.executorService?.shutdown()
        wsClient = null
        wsConnected = false
    }

    /** Exponential-backoff reconnect loop (coroutine, cancelled on clean close). */
    private fun wsScheduleReconnect() {
        wsReconnectJob?.cancel()
        wsReconnectJob = wsScope.launch {
            delay(wsBackoffMs)
            if (!isActive) return@launch
            wsBackoffMs = minOf(wsBackoffMs * 2, WS_BACKOFF_MAX_MS)
            Log.d(TAG, "WS: attempting reconnect…")
            wsConnect()
        }
    }

    /** Calls RenderSyncManager.pull() on IO; safe to call from any thread. */
    private fun wsTriggerPull() {
        wsScope.launch {
            try {
                val qRepo = EntryPointAccessors
                    .fromApplication(applicationContext, QuestRepositoryEntryPoint::class.java)
                    .questRepository()
                val result = RenderSyncManager.pull(applicationContext, userRepository, qRepo)
                Log.d(TAG, "WS pull result: $result")
            } catch (e: Exception) {
                Log.e(TAG, "WS pull failed: ${e.message}")
            }
        }
    }

    // ── end Live-sync WebSocket ───────────────────────────────────────────────

    // ───────────────────────── Jarvis (Kai) — merged in ─────────────────────────

    private fun jarvisInitTts() {
        jarvisTts = TextToSpeech(this) { status ->
            jarvisTtsReady = status == TextToSpeech.SUCCESS
            if (jarvisTtsReady) {
                jarvisTts?.language = java.util.Locale.US
                jarvisTts?.setSpeechRate(0.95f)
                jarvisTts?.setPitch(1.0f)
                jarvisTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        jarvisIsSpeaking = false
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (jarvisContinuousMode) jarvisStartOneListen()
                        }, 400L)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }

    private fun jarvisSpeak(text: String) {
        if (!jarvisTtsReady) return
        val clean = text
            .replace(Regex("[*_#`~>]"), "")
            .replace(Regex("[\uD83C-\uDBFF\uDC00-\uDFFF]+"), "")
            .replace(Regex("\\s+"), " ").trim().take(300)
        try { jarvisRecognizer?.stopListening() } catch (_: Exception) {}
        try { jarvisRecognizer?.cancel() } catch (_: Exception) {}
        jarvisIsSpeaking = true
        jarvisTts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "kai_${System.currentTimeMillis()}")
    }

    private fun jarvisSetupRecognizer() {
        jarvisDestroyRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        jarvisRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: run {
                            if (jarvisContinuousMode) jarvisScheduleNextListen() else jarvisStopListening()
                            return
                        }
                    jarvisHandleVoiceResults(matches)
                    if (jarvisContinuousMode && !jarvisIsSpeaking) jarvisScheduleNextListen(1500L)
                    else jarvisStopListening()
                }
                override fun onError(error: Int) {
                    if (jarvisContinuousMode) jarvisScheduleNextListen(2000L) else jarvisStopListening()
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

    private fun jarvisScheduleNextListen(delayMs: Long = 500L) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (jarvisContinuousMode) jarvisStartOneListen()
        }, delayMs)
    }

    private fun jarvisStartOneListen() {
        if (jarvisRecognizer == null) jarvisSetupRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try { jarvisRecognizer?.startListening(intent) } catch (_: Exception) {}
    }

    private fun jarvisHandleVoiceResults(matches: List<String>) {
        val u = userRepository.userInfo
        val wakeWord = u.jarvisWakeWord.ifBlank { "kai" }

        for (text in matches) {
            val lower = text.lowercase()

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

            val quickReply = jarvisHandleQuickQuery(query)
            if (quickReply != null) {
                jarvisSpeak(quickReply)
                return
            }

            jarvisScope.launch {
                jarvisSpeak("On it...")
                gemmaRepository.chat(emptyList(), query)
                    .onSuccess { resp -> jarvisSpeak(resp.text) }
                    .onFailure { jarvisSpeak("I couldn't connect right now.") }
            }
            return
        }
    }

    private fun jarvisOpenAppByName(appName: String): String? {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val allApps = pm.queryIntentActivities(launcherIntent, 0)

        val match = allApps.firstOrNull { it.loadLabel(pm).toString().lowercase() == appName }
            ?: allApps.firstOrNull { it.loadLabel(pm).toString().lowercase().startsWith(appName) }
            ?: allApps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(appName) }
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

    private fun jarvisHandleQuickQuery(query: String): String? {
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
                jarvisOpenAppByName(appName)
            }
            else -> null
        }
    }

    private fun jarvisDestroyRecognizer() {
        try { jarvisRecognizer?.stopListening() } catch (_: Exception) {}
        try { jarvisRecognizer?.cancel() } catch (_: Exception) {}
        try { jarvisRecognizer?.destroy() } catch (_: Exception) {}
        jarvisRecognizer = null
    }

    /** Replaces the old standalone stopForeground(true) — we never stop the
     * foreground service just because Kai stopped listening, since
     * AppBlocker still needs it running. We just flip the notification back. */
    private fun jarvisStopListening() {
        jarvisListening = false
        refreshNotification()
    }

    private fun jarvisHandleAction(action: String?) {
        when (action) {
            ACTION_JARVIS_START -> {
                jarvisContinuousMode = true
                jarvisListening = true
                jarvisSetupRecognizer()
                refreshNotification()
                jarvisStartOneListen()
            }
            ACTION_JARVIS_LISTEN_ONCE -> {
                jarvisContinuousMode = false
                jarvisListening = true
                jarvisSetupRecognizer()
                refreshNotification()
                jarvisStartOneListen()
            }
            ACTION_JARVIS_STOP -> {
                jarvisContinuousMode = false
                jarvisDestroyRecognizer()
                jarvisStopListening()
            }
        }
    }

    // ─────────────────────── end Jarvis (Kai) ───────────────────────

    private val appMonitorRunnable = object : Runnable {
        override fun run() {
            detectAndHandleForegroundApp()
            handler.postDelayed(this, STANDARD_POLLING_INTERVAL_MS)
        }
    }

    private fun detectAndHandleForegroundApp() {
        val currentTime = System.currentTimeMillis()

        cleanUpExpiredUnlocks(currentTime)

        // Query events for a slightly longer period to catch transitions
        val usageEvents = usageStatsManager.queryEvents(currentTime - 2000, currentTime)
        val event = UsageEvents.Event()
        var detectedForegroundPackage: String?
        val recentLockedAppActivities = mutableSetOf<String>()

        // Process usage events to detect foreground app and recent locked app activities
        var latestTimestamp: Long = 0
        var currentForegroundAppFromEvents: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp > latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    currentForegroundAppFromEvents = event.packageName
                }
                if (lockedApps.contains(event.packageName)) {
                    recentLockedAppActivities.add(event.packageName)
                }
            }
        }
        detectedForegroundPackage = currentForegroundAppFromEvents ?: getCurrentForegroundApp()

        // If lock screen is active but pushed to background, bring it back
        if (isOverlayActive && detectedForegroundPackage != null && lockedApps.contains(
                detectedForegroundPackage
            )
        ) {
            // Check if the current foreground app is the one that should be locked
            if (currentLockedPackage == detectedForegroundPackage) {
                handler.post { refreshHomeScreenOverlay() }
                return
            } else {
                // If another locked app comes to foreground while overlay is active for a different app
                // or if an unlocked app comes to foreground..
            }
        }

        // Check if we're on home screen
        val isHomeScreen = isLauncherApp(detectedForegroundPackage)
        if (isHomeScreen) {
            handleHomeScreenDetected(detectedForegroundPackage)
            return
        }

        // ── Vault check FIRST — vault-launched apps skip ALL other blocks ──
        val isVaultActive = detectedForegroundPackage != null &&
            !isLauncherApp(detectedForegroundPackage) &&
            neth.iecal.questphone.core.vault.AppVaultManager.isVaultLaunch(applicationContext, detectedForegroundPackage)

        if (detectedForegroundPackage != null && !isLauncherApp(detectedForegroundPackage)) {
            if (neth.iecal.questphone.core.vault.AppVaultManager.isInVault(applicationContext, detectedForegroundPackage!!)) {
                if (!isVaultActive) {
                    showHomwScreenOverlay()
                    return
                }
                // Vault launch active — skip ALL other checks, app is allowed
                return
            } else {
                // Foreground is not a vault app — clear vault session
                neth.iecal.questphone.core.vault.AppVaultManager.clearVaultLaunch(applicationContext)
            }
        }

        // Handle locked app detection
        // ── App Locker ──────────────────────────────────────────────
        if (detectedForegroundPackage != null && !isLauncherApp(detectedForegroundPackage)) {
            val lockerPin = neth.iecal.questphone.core.locker.AppLockerManager.getPin(applicationContext)
            val isAppLocked = neth.iecal.questphone.core.locker.AppLockerManager.isLocked(applicationContext, detectedForegroundPackage!!)
            if (lockerPin.isNotBlank() && isAppLocked) {
                // Skip if we just launched from locker (bypass window)
                if (!neth.iecal.questphone.core.locker.AppLockerManager.isLockerLaunch(applicationContext)) {
                    val appName = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(detectedForegroundPackage!!, 0)
                        ).toString()
                    } catch (_: Exception) { detectedForegroundPackage!! }
                    neth.iecal.questphone.app.screens.locker.AppLockerActivity.launch(
                        applicationContext, detectedForegroundPackage!!, appName
                    )
                    return
                }
                // Bypass window active — fall through to distraction check below
            }
        }

        if (shouldShowLockScreen(recentLockedAppActivities, detectedForegroundPackage)) {
            return // Lock screen shown, no further processing needed in this cycle
        }

        // Process foreground app state
        detectedForegroundPackage?.let { foregroundPackage ->
            processForegroundApp(foregroundPackage)
        }
    }

    private fun getStudyTimeToday(): Long {
        val studyApps = userRepository.getStudyApps()
        if (studyApps.isEmpty()) return 0L
        val statsHelper = nethical.questphone.core.core.utils.ScreenUsageStatsHelper(this)
        val stats = statsHelper.getForegroundStatsByRelativeDay(0)
        return stats.filter { studyApps.contains(it.packageName) }.sumOf { it.totalTime }
    }

    private fun getDistractionTimeToday(): Long {
        val blockedApps = userRepository.getBlockedPackages()
        if (blockedApps.isEmpty()) return 0L
        val statsHelper = nethical.questphone.core.core.utils.ScreenUsageStatsHelper(this)
        val stats = statsHelper.getForegroundStatsByRelativeDay(0)
        return stats.filter { blockedApps.contains(it.packageName) }.sumOf { it.totalTime }
    }

    /** Fix #12: Check if study quota block should be active today.
     *  The block is set by NewDayReceiver when yesterday's study time was below quota. */
    private fun isStudyQuotaBlockActive(): Boolean {
        val primePkg = userRepository.getPrimeStudyPackage()
        if (primePkg.isBlank()) return false
        val blockDate = userRepository.getStudyQuotaBlockDate()
        if (blockDate.isBlank()) return false
        val today = nethical.questphone.core.core.utils.getCurrentDate()
        if (blockDate != today) return false
        // Check if today's study quota has now been met (auto-unblock)
        val quotaMs = (userRepository.getDailyStudyQuotaHours() * 3600_000L).toLong()
        val statsHelper = nethical.questphone.core.core.utils.ScreenUsageStatsHelper(this)
        val stats = statsHelper.getForegroundStatsByRelativeDay(0)
        val todayStudyMs = stats.filter { it.packageName == primePkg }.sumOf { it.totalTime }
        if (todayStudyMs >= quotaMs) {
            // Quota met for today — lift the block
            userRepository.setStudyQuotaBlockDate("")
            return false
        }
        return true
    }

    // Cleans up apps whose temporary unlock duration has expired
    private fun cleanUpExpiredUnlocks(currentTime: Long) {
        loadUnlockedAppsFromServer()
        val expiredApps = mutableListOf<String>()
        for ((packageName, expiryTime) in AppBlockerServiceInfo.unlockedApps) {
            if (currentTime >= expiryTime) {
                expiredApps.add(packageName)
                Log.d(TAG, "Temporary unlock expired for: $packageName")
            }
        }
        expiredApps.forEach { AppBlockerServiceInfo.unlockedApps.remove(it) }
    }

    private fun shouldShowLockScreen(
        recentLockedAppActivities: Set<String>,
        detectedForegroundPackage: String?
    ): Boolean {
        if (detectedForegroundPackage == null) return false

        // Skip showing lock screen for our own app
        if (detectedForegroundPackage == packageName) {
            return false
        }

        // Skip if this app was launched from the vault — it has full access
        if (neth.iecal.questphone.core.vault.AppVaultManager.isVaultLaunch(applicationContext, detectedForegroundPackage)) {
            return false
        }

        val isAppCurrentlyLocked = lockedApps.contains(detectedForegroundPackage)
        // Check if the app is currently temporarily unlocked (and not expired)
        val isTemporarilyUnlocked =
            AppBlockerServiceInfo.unlockedApps.containsKey(detectedForegroundPackage)

        // Check for Full Free Day
        if (userRepository.isFullFreeDay()) return false

        // -- Fix #12: Study Quota Hard-Block ------------------------------
        // If prime study app missed quota yesterday, block all non-study apps today
        val studyApps = userRepository.getStudyApps()
        val isStudyApp = studyApps.contains(detectedForegroundPackage)
        if (!isStudyApp && isStudyQuotaBlockActive()) {
            // Hard block — coins cannot bypass quota block
            if (!isOverlayActive) {
                Log.d(TAG, "Study quota block active for: $detectedForegroundPackage")
                showLockScreenFor(detectedForegroundPackage)
                return true
            }
        }

        // Check for Study Ratio
        val ratio = userRepository.getStudyToDistractionRatio()
        val studyTime = getStudyTimeToday()
        val distractionTime = getDistractionTimeToday()
        val allowedDistractionTime = (studyTime / ratio).toLong()
        val isRatioUnlocked = distractionTime < allowedDistractionTime

        if (isAppCurrentlyLocked &&
            !isOverlayActive &&
            !isTemporarilyUnlocked && // Make sure it's not temporarily unlocked
            !isRatioUnlocked
        ) {
            Log.d(TAG, "Lock condition met for: $detectedForegroundPackage (showing lock screen)")
            showLockScreenFor(detectedForegroundPackage)
            return true
        } else {
            try {
                val interval =
                    AppBlockerServiceInfo.unlockedApps[detectedForegroundPackage]!! - System.currentTimeMillis()
                startCooldownTimer(detectedForegroundPackage, interval.toLong())
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun showLockScreenFor(packageName: String) {
        currentLockedPackage = packageName
        isOverlayActive = true
        // Ensure biometric auth in progress is reset if we are showing a new lock screen
        handler.post { showHomwScreenOverlay() }
    }

    private fun handleHomeScreenDetected(detectedForegroundPackage: String?) {
        // If we came from a locked app that was temporarily unlocked, its timer continues.
        // We only clear the `currentLockedPackage` and `isOverlayActive` flags.
        if (currentLockedPackage != null) {
            Log.d(TAG, "User exited locked app, clearing current lock state flags.")
            currentLockedPackage = null
            isOverlayActive = false
        }
        lastForegroundPackage = detectedForegroundPackage
    }


    private fun showQuestReminder(targetPkg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val questRepo = EntryPointAccessors
                    .fromApplication(applicationContext, QuestRepositoryEntryPoint::class.java)
                    .questRepository()
                val allQuests = questRepo.getAllQuests().first()
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val today = nethical.questphone.core.core.utils.getCurrentDay()
                val todayDate = nethical.questphone.core.core.utils.getCurrentDate()
                val active = allQuests.filter { q ->
                    !q.is_destroyed &&
                    q.selected_days.any { it == today } &&
                    q.last_completed_on != todayDate &&
                    currentHour in q.time_range[0]..q.time_range[1]
                }.firstOrNull()
                if (active != null) {
                    val intent = Intent(this@AppBlockerService, QuestReminderActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra(QuestReminderActivity.EXTRA_QUEST_TITLE, active.title)
                        putExtra(QuestReminderActivity.EXTRA_QUEST_TIME,
                            "${active.time_range[0]}:00 - ${active.time_range[1]}:00")
                        putExtra(QuestReminderActivity.EXTRA_TARGET_PKG, targetPkg)
                    }
                    startActivity(intent)
                }
            } catch (_: Exception) {}
        }
    }

    private fun processForegroundApp(foregroundPackage: String) {
        // Handle launcher app detection (already handled in detectAndHandleForegroundApp, but good for clarity)
        if (isLauncherApp(foregroundPackage)) {
            lastReminderPkg = ""  // reset so next app shows reminder
            handleHomeScreenDetected(foregroundPackage)
            return
        }
        // Focus mode: block non-study apps during 07:50-16:00 weekdays
        if (isFocusMode() && foregroundPackage !in STUDY_WHITELIST && foregroundPackage != packageName) {
            try {
                val wall = Intent(this, BacklogWallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(BacklogWallActivity.EXTRA_BLOCKED_APP, foregroundPackage)
                }
                startActivity(wall)
            } catch (_: Exception) {}
            lastForegroundPackage = foregroundPackage
            return
        }

        // Show quest reminder: new app, not our app, cooldown 2 min
        if (foregroundPackage != packageName &&
            foregroundPackage != lastReminderPkg &&
            System.currentTimeMillis() - lastReminderTime > 120_000L) {
            lastReminderPkg = foregroundPackage
            lastReminderTime = System.currentTimeMillis()
            // Small delay so app settles before overlay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showQuestReminder(foregroundPackage)
            }, 2000)
        }

        // NEW: Check if the app is currently temporarily unlocked (and not expired)
        val isCurrentlyTemporarilyUnlocked =
            AppBlockerServiceInfo.unlockedApps.containsKey(foregroundPackage)

        // If the current foreground app is one that is temporarily unlocked, do nothing further.
        if (isCurrentlyTemporarilyUnlocked) {
            if (currentLockedPackage == foregroundPackage) { // Ensure consistency
                currentLockedPackage = null
            }
            lastForegroundPackage = foregroundPackage
            return
        }

        // If a new app (not the temporarily unlocked one) comes to the foreground,
        // and it's not due to biometric auth flow for the *same* app.
        // We now handle `temporarilyUnlockedAppsWithExpiry` as a map, so we don't clear a single flag.
        // The cleanup is handled by `cleanUpExpiredUnlocks`.

        // Check if the current foreground app needs to be locked
        // This is the main locking condition after other checks.
        if (lockedApps.contains(foregroundPackage) &&
            !isOverlayActive // Don't show if already showing
        ) {
            Log.d(
                TAG,
                "Locked app detected in processForegroundApp: $foregroundPackage (showing lock screen)"
            )
            showLockScreenFor(foregroundPackage)
        }

        lastForegroundPackage = foregroundPackage
    }

    private fun refreshHomeScreenOverlay() {
        if (isOverlayActive && currentLockedPackage != null) {
            Log.d(TAG, "Refreshing overlay for $currentLockedPackage")
            val currentIntent = Intent(this, MainActivity::class.java)
            currentIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            // Ensure the package name is passed, in case the overlay needs to re-verify
            currentIntent.putExtra("locked_package", currentLockedPackage)
            startActivity(currentIntent)
        }
    }

    private fun showHomwScreenOverlay() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        intent.putExtra("locked_package", currentLockedPackage)
        startActivity(intent)
    }

    // unlockApp to set an expiry time
    fun unlockApp(unlockedPackageName: String, duration: Long) {
        if (!isAppUnlocked(unlockedPackageName)) {
            val expiryTime = System.currentTimeMillis() + duration
            AppBlockerServiceInfo.unlockedApps[unlockedPackageName] = expiryTime
            saveUnlockedAppsToServer()
            Log.d(TAG, "App ked via PIN: $unlockedPackageName. Unlocked until: $expiryTime")
            isOverlayActive = false
            if (currentLockedPackage == unlockedPackageName) {
                currentLockedPackage = null
            }
        }
    }

    fun saveUnlockedAppsToServer() {
        userRepository.updateUnlockedAppsSet(AppBlockerServiceInfo.unlockedApps)
    }

    fun loadUnlockedAppsFromServer() {
        AppBlockerServiceInfo.unlockedApps = userRepository.getUnlockedPackages()
    }

    fun isAppUnlocked(packageName: String): Boolean {
        return AppBlockerServiceInfo.unlockedApps.containsKey(packageName)
    }

    fun isAppLocked(packageName: String): Boolean {
        return lockedApps.contains(packageName)
    }

    // Modified isAppTemporarilyUnlocked to check for expiry
    fun isAppTemporarilyUnlocked(packageName: String): Boolean {
        val expiryTime = AppBlockerServiceInfo.unlockedApps[packageName]
        return expiryTime != null && System.currentTimeMillis() < expiryTime
    }

    fun loadLockedApps() {
        lockedApps.clear()
        lockedApps.addAll(userRepository.getBlockedPackages())

        Log.d(TAG, "Loaded locked apps: $lockedApps")
    }

    fun addLockedApp(packageName: String) {
        lockedApps.add(packageName)
    }

    fun removeLockedApp(packageName: String) {
        lockedApps.remove(packageName)
        // If removing a locked app, also remove it from temporary unlock if it was there
        AppBlockerServiceInfo.unlockedApps.remove(packageName)
    }

    fun getLockedApps(): Set<String> {
        return lockedApps.toSet() // Return a copy
    }

    private fun isLauncherApp(packageName: String?): Boolean {
        if (packageName == null) return false
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun getCurrentForegroundApp(): String? {
        var currentApp: String? = null
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        if (appList != null && appList.isNotEmpty()) {
            val sortedMap = sortedMapOf<Long, String>()
            for (usageStats in appList) {
                sortedMap[usageStats.lastTimeUsed] = usageStats.packageName
            }
            if (sortedMap.isNotEmpty()) {
                currentApp = sortedMap[sortedMap.lastKey()]
            }
        }
        return currentApp
    }

    private fun turnDeepFocus() {
        CoroutineScope(Dispatchers.IO).launch {
            val pm = applicationContext.packageManager
            val result = reloadApps(pm, applicationContext)

            if (result.isSuccess) {
                var allApps = result.getOrDefault(emptyList())
                val keyboardApps = getKeyboards(applicationContext)

                allApps = allApps.filter {
                    !AppBlockerServiceInfo.deepFocus.exceptionApps.contains(it.packageName) && !keyboardApps.contains(
                        it.packageName
                    ) && it.packageName != "neth.iecal.questphone"
                }

                lockedApps.clear()
                lockedApps.addAll(allApps.map { it.packageName })
                Log.d("AppBlockerServiceFg", "Turning on FocusMode ${lockedApps.toString()}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Deep Focus Activated", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, intent?.action.toString())
            if (intent == null) return
            when (intent.action) {
                INTENT_ACTION_REFRESH_APP_BLOCKER -> loadLockedApps()
                INTENT_ACTION_START_DEEP_FOCUS -> {
                    AppBlockerServiceInfo.deepFocus.exceptionApps =
                        intent.getStringArrayListExtra("exception")?.toHashSet() ?: hashSetOf()
                    AppBlockerServiceInfo.deepFocus.isRunning = true
                    AppBlockerServiceInfo.deepFocus.duration = intent.getLongExtra("duration", 0L)
                    startCooldownTimer("deepfocus", AppBlockerServiceInfo.deepFocus.duration)
                    Log.d("Turning deep focus", AppBlockerServiceInfo.deepFocus.duration.toString())
                    turnDeepFocus()
                    Toast.makeText(applicationContext,"Initializing Deep Focus", Toast.LENGTH_SHORT).show()
                    setReminderInMinutes(AppBlockerServiceInfo.deepFocus.duration)
                }

                INTENT_ACTION_STOP_DEEP_FOCUS -> {
                    Toast.makeText(applicationContext,"Stopping Deep Focus", Toast.LENGTH_SHORT).show()
                    AppBlockerServiceInfo.deepFocus.isRunning = false
                    AppBlockerServiceInfo.deepFocus.exceptionApps = hashSetOf<String>()
                    AppBlockerServiceInfo.deepFocus.duration = 0
                    loadLockedApps()
                    stopCooldownTimer()
                }

                INTENT_ACTION_UNLOCK_APP -> {
                    val interval = intent.getLongExtra("selected_time", 0)
                    val coolPackage = intent.getStringExtra("package_name") ?: ""

                    Log.d(
                        "AppBlockerServiceFG",
                        "Received cooldown broadcast for $coolPackage, duration: $interval ms"
                    )

                    // Only proceed if we have a valid package and duration
                    if (coolPackage.isNotEmpty() && interval > 0) {

                        createNotificationChannel()
                        startCooldownTimer(coolPackage, interval.toLong())
                        unlockApp(coolPackage, interval)
                        setReminderInMinutes(interval)
                    } else {
                        Log.e(
                            "AppBlockerServiceFG",
                            "Invalid cooldown parameters: package=$coolPackage, interval=$interval"
                        )
                    }
                }
            }
        }
    }

    private fun setReminderInMinutes(msFromNow: Long) {
        val triggerTimeMillis = System.currentTimeMillis() + msFromNow

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, AppBlockerReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            100, // Unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
                Log.d(
                    "NotificationScheduler",
                    "Scheduled alarm in ${msFromNow/60_000L} minutes at ${Date(triggerTimeMillis)}"
                )
            } else {
                Log.w(
                    "NotificationScheduler",
                    "Exact alarm permission not granted. Cannot schedule alarm."
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            Log.d(
                "NotificationScheduler",
                "Scheduled alarm in $msFromNow minutes at ${Date(triggerTimeMillis)}"
            )
        }
    }

    private fun startCooldownTimer(packageName: String, duration: Long) {
        // Stop any existing timer first
        if (timerRunningForPackage == packageName) return
        stopCooldownTimer()

        val startTime = SystemClock.uptimeMillis()
        val endTime = startTime + duration
        val totalSeconds = duration

        // Show initial notification immediately
        updateTimerNotification(packageName, 0f, totalSeconds)

        isTimerRunning = true
        timerRunningForPackage = packageName
        timerRunnable = object : Runnable {
            override fun run() {
                val currentTime = SystemClock.uptimeMillis()
                val elapsedMs = currentTime - startTime
                val remainingMs = endTime - currentTime

                // Convert to seconds for display and calculations
                val remainingSeconds = remainingMs / 1000

                val progress = elapsedMs.toFloat() / duration.toFloat()

                if(remainingSeconds<10){
                    Toast.makeText(this@AppBlockerService, "Remaining time: $remainingSeconds", Toast.LENGTH_SHORT).show()
                }

                if (remainingSeconds > 0) {
                    Log.d(
                        "AppBlockerService",
                        "Updating notification: $packageName, progress: ${progress * 100}%, remaining: $remainingSeconds s"
                    )
                    updateTimerNotification(packageName, progress, remainingSeconds)
                    handler.postDelayed(this, 1000)
                } else {
                    Log.d("AppBlockerService", "Cooldown timer completed for $packageName")

                    // Final notification update showing completion
                    updateTimerNotification(packageName, 1f, 0)

                    // Small delay before removing the notification
                    handler.postDelayed({
                        stopCooldownTimer()
                    }, 2000)
                }
            }
        }

        // Start the timer runnable
        handler.post(timerRunnable!!)
    }

    private fun stopCooldownTimer() {
        timerRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("AppBlockerService", "Stopped cooldown timer")
        }
        isTimerRunning = false
        timerRunnable = null
        timerRunningForPackage = ""
        cancelTimerNotification()
    }


    @SuppressLint("DefaultLocale")
    private fun updateTimerNotification(
        packageName: String,
        progress: Float,
        remainingSeconds: Long
    ) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Check if notifications are enabled
            val channel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (channel != null) {
                if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    Log.w("AppBlockerService", "Notification channel is disabled")
                    return
                }
            }

            // Create a basic intent for the app
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Format time display
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            val timeText = String.format("%d:%02d", minutes, seconds)

            // Get app name for better UX

            val title =
                if (AppBlockerServiceInfo.deepFocus.isRunning) "Focus Session Ongoing" else {
                    val appName = try {
                        packageManager.getApplicationInfo(packageName, 0)
                            .loadLabel(packageManager).toString()
                    } catch (e: Exception) {
                        Log.w("AppBlockerService", "Failed to get app name: ${e.message}")
                        packageName
                    }
                    "Unlocked App $appName"
                }
            // Build the notification
            val builder = NotificationCompat.Builder(
                this,
                NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText("Time remaining: $timeText")
                .setProgress(100, (progress * 100).toInt(), false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setSilent(true)

            // Set foreground if device is on Android 8.0 or higher
            builder.setChannelId(NOTIFICATION_CHANNEL_ID)

            val notification = builder.build()

            // Post the notification
            Log.d("AppBlockerService", "Posting notification for $packageName with time $timeText")
            notificationManager.notify(NOTIFICATION_ID, notification)

        } catch (e: Exception) {
            Log.e("AppBlockerService", "Failed to update notification: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun cancelTimerNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC or FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d("AppBlockerService", "Notification cancelled")
        } catch (e: Exception) {
            Log.e("AppBlockerService", "Failed to cancel notification: ${e.message}")
        }
    }
}

class AppBlockerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context,"Restarting Service", Toast.LENGTH_SHORT).show()
        context.startForegroundService(Intent(context, AppBlockerService::class.java))

    }
}