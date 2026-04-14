package neth.iecal.questphone.core.focus

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

const val ACTION_START = "jarvis_start"
const val ACTION_STOP  = "jarvis_stop"

class JarvisListenerService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    /** Request audio focus transiently — we only need it while the mic is live */
    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /** Always abandon focus after recognition cycle so other apps regain speaker */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun setupAndStart() {
        destroyRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    abandonAudioFocus() // release mic/audio immediately
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val wakeWord = applicationContext
                        .getSharedPreferences("onboard", 0)
                        .getString("jarvis_word", "jarvis") ?: "jarvis"

                    // Check custom voice actions first
                    val userPrefs = applicationContext.getSharedPreferences("user_voice_actions", 0)
                    val actionCount = userPrefs.getInt("action_count", 0)
                    var handled = false
                    matches?.forEach { text ->
                        if (handled) return@forEach
                        // Check custom actions
                        for (i in 0 until actionCount) {
                            val phrase = userPrefs.getString("phrase_$i", "") ?: continue
                            val pkg = userPrefs.getString("pkg_$i", "") ?: continue
                            if (phrase.isNotBlank() && text.contains(phrase, ignoreCase = true)) {
                                try {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                    launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    launchIntent?.let { startActivity(it) }
                                    handled = true
                                } catch (_: Exception) {}
                                return@forEach
                            }
                        }
                        // Default wake word
                        if (!handled && text.contains(wakeWord, ignoreCase = true)) {
                            Log.d("Jarvis", "Triggered: $text")
                            releaseAndLaunch()
                            handled = true
                            return@forEach
                        }
                    }
                    // Resume listening only if still active — but first yield audio
                    if (isListening && !handled) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isListening) startListeningCycle()
                        }, 300)
                    }
                }
                override fun onError(error: Int) {
                    abandonAudioFocus()
                    if (error == SpeechRecognizer.ERROR_AUDIO ||
                        error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        isListening = false
                        return
                    }
                    if (isListening) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isListening) startListeningCycle()
                        }, 500)
                    }
                }
                override fun onReadyForSpeech(p: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { abandonAudioFocus() }
                override fun onPartialResults(b: android.os.Bundle?) {}
                override fun onEvent(t: Int, b: android.os.Bundle?) {}
            })
        }
        startListeningCycle()
    }

    private fun startListeningCycle() {
        requestAudioFocus()
        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) {}
    }

    /** Stop recognizer FULLY and release microphone/audio before launching assistant */
    private fun releaseAndLaunch() {
        abandonAudioFocus()
        destroyRecognizer()
        isListening = false
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                startActivity(Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                try {
                    packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
                        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        ?.let { startActivity(it) }
                } catch (_: Exception) {}
            }
        }, 200)
    }

    private fun destroyRecognizer() {
        abandonAudioFocus()
        try { recognizer?.stopListening() } catch (_: Exception) {}
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isListening = true
                setupAndStart()
            }
            ACTION_STOP -> {
                isListening = false
                destroyRecognizer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isListening = false
        destroyRecognizer()
        super.onDestroy()
    }
}
