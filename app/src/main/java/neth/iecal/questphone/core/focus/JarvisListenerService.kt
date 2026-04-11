package neth.iecal.questphone.core.focus

import android.app.Service
import android.content.Intent
import android.os.Bundle
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            setupRecognizer()
        }
    }

    private fun setupRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { text ->
                    if (text.contains("jarvis", ignoreCase = true)) {
                        Log.d("Jarvis", "Triggered: $text")
                        launchAssistant()
                    }
                }
                // Keep listening
                if (isListening) startListening()
            }
            override fun onError(error: Int) { if (isListening) startListening() }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) {}
    }

    private fun launchAssistant() {
        try {
            startActivity(Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            // Fallback to Google Assistant
            try {
                startActivity(packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> { isListening = true; startListening() }
            ACTION_STOP  -> { isListening = false; recognizer?.stopListening() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isListening = false
        recognizer?.destroy()
        super.onDestroy()
    }
}
