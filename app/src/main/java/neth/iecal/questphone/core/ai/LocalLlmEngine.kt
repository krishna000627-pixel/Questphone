package neth.iecal.questphone.core.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Singleton local LLM engine backed by a llama.cpp server.
 *
 * llama.cpp server runs on the same device (Termux) or a PC on the same WiFi
 * and exposes an OpenAI-compatible /v1/chat/completions endpoint.
 *
 * Start llama-server like:
 *   llama-server -m SmolLM2-135M-Instruct-Q8_0.gguf --port 8080
 *
 * Then set Base URL = http://192.168.x.x:8080 (or http://localhost:8080 if Termux)
 */
object LocalLlmEngine {

    // ── State ──────────────────────────────────────────────────────────────────

    sealed class LlmState {
        object Unconfigured : LlmState()
        object Idle : LlmState()
        object Connecting : LlmState()
        data class Ready(val modelName: String) : LlmState()
        object Generating : LlmState()
        data class Error(val msg: String) : LlmState()
    }

    private val _state = MutableStateFlow<LlmState>(LlmState.Unconfigured)
    val state: StateFlow<LlmState> = _state.asStateFlow()

    val isReady get() = _state.value is LlmState.Ready || _state.value is LlmState.Idle

    // ── Prefs keys ─────────────────────────────────────────────────────────────

    private const val PREFS    = "jarvis_llm"
    private const val KEY_URL  = "llama_url"
    private const val KEY_MODEL = "llama_model"

    fun savedUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, "") ?: ""

    fun savedModel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODEL, "") ?: ""

    fun saveConfig(context: Context, url: String, model: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url.trimEnd('/'))
            .putString(KEY_MODEL, model.trim())
            .apply()
        _state.value = if (url.isBlank()) LlmState.Unconfigured
                       else LlmState.Ready(model.trim().ifBlank { "local-model" })
    }

    fun clearConfig(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _state.value = LlmState.Unconfigured
    }

    fun initFromPrefs(context: Context) {
        val url   = savedUrl(context)
        val model = savedModel(context)
        _state.value = if (url.isBlank()) LlmState.Unconfigured
                       else LlmState.Ready(model.ifBlank { "local-model" })
    }

    // ── Probe ──────────────────────────────────────────────────────────────────

    suspend fun probe(context: Context): Boolean = withContext(Dispatchers.IO) {
        val base = savedUrl(context)
        if (base.isBlank()) { _state.value = LlmState.Unconfigured; return@withContext false }
        _state.value = LlmState.Connecting
        return@withContext try {
            val conn = URL("$base/v1/models").openConnection() as HttpURLConnection
            conn.connectTimeout = 3_000
            conn.readTimeout    = 3_000
            conn.requestMethod  = "GET"
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                _state.value = LlmState.Ready(savedModel(context).ifBlank { "local-model" })
                true
            } else {
                _state.value = LlmState.Error("Server returned HTTP $code")
                false
            }
        } catch (e: Exception) {
            _state.value = LlmState.Error("Cannot reach server: ${e.message}")
            false
        }
    }

    // ── Generate (multi-turn) ──────────────────────────────────────────────────

    suspend fun generate(
        context: Context,
        messages: List<Pair<String, String>>,
        onToken: (token: String, done: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        val base = savedUrl(context)
        if (base.isBlank()) {
            onToken("⚠ No llama.cpp server URL configured. Set it in Jarvis → Settings → Offline AI.", true)
            return@withContext
        }
        val model = savedModel(context).ifBlank { "local-model" }
        val messagesJson = JSONArray().apply {
            for ((role, content) in messages) {
                put(JSONObject().apply { put("role", role); put("content", content) })
            }
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messagesJson)
            put("stream", true)
            put("max_tokens", 512)
            put("temperature", 0.7)
        }.toString()

        _state.value = LlmState.Generating
        try {
            val conn = URL("$base/v1/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod  = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout    = 60_000
            conn.doOutput       = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            if (code !in 200..299) {
                _state.value = LlmState.Error("HTTP $code from llama server")
                onToken("⚠ llama.cpp server error: HTTP $code", true)
                conn.disconnect()
                return@withContext
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            var fullText = ""
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data == "[DONE]") { onToken("", true); break }
                try {
                    val delta = JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content", "") ?: ""
                    if (delta.isNotEmpty()) { fullText += delta; onToken(delta, false) }
                } catch (_: Exception) {}
            }
            reader.close()
            conn.disconnect()
            if (fullText.isEmpty()) onToken("(empty response from model)", true)
            _state.value = LlmState.Ready(model)
        } catch (e: Exception) {
            _state.value = LlmState.Error(e.message ?: "Connection failed")
            onToken("⚠ Error: ${e.message}", true)
        }
    }

    // ── Generate (single-turn, prompt string) — used by AppBlockerService ─────

    /**
     * Legacy single-string overload so AppBlockerService can call:
     *   LocalLlmEngine.generate(prompt) { token, done -> … }
     * without needing a Context (URL already loaded in state).
     * Falls back gracefully if no context is available to fetch URL.
     */
    suspend fun generate(
        prompt: String,
        onToken: (token: String, done: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        // We can't use savedUrl() without context here — reconstruct from state
        if (_state.value is LlmState.Unconfigured) {
            onToken("⚠ Offline AI not configured.", true)
            return@withContext
        }
        // Wrap in messages format using the raw prompt directly
        val body = JSONObject().apply {
            put("model", "local-model")
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("stream", true)
            put("max_tokens", 512)
            put("temperature", 0.7)
        }.toString()

        // Re-read base URL from state model name not available — use last known
        // AppBlockerService path: best-effort, state must be Ready
        val base = (_state.value as? LlmState.Ready)?.let {
            // Can't recover URL from state alone — need a cached field
            _cachedUrl
        } ?: run { onToken("⚠ Offline AI not ready.", true); return@withContext }

        _state.value = LlmState.Generating
        try {
            val conn = URL("$base/v1/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod  = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout    = 60_000
            conn.doOutput       = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            if (code !in 200..299) {
                _state.value = LlmState.Error("HTTP $code")
                onToken("⚠ llama.cpp error: HTTP $code", true)
                conn.disconnect(); return@withContext
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            var fullText = ""
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data == "[DONE]") { onToken("", true); break }
                try {
                    val delta = JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content", "") ?: ""
                    if (delta.isNotEmpty()) { fullText += delta; onToken(delta, false) }
                } catch (_: Exception) {}
            }
            reader.close(); conn.disconnect()
            if (fullText.isEmpty()) onToken("(empty response)", true)
            _state.value = LlmState.Ready(_cachedModel)
        } catch (e: Exception) {
            _state.value = LlmState.Error(e.message ?: "Failed")
            onToken("⚠ Error: ${e.message}", true)
        }
    }

    // ── Cached URL/model for context-free generate ─────────────────────────────

    private var _cachedUrl   = ""
    private var _cachedModel = "local-model"

    fun saveConfig(url: String, model: String) {
        _cachedUrl   = url.trimEnd('/')
        _cachedModel = model.trim().ifBlank { "local-model" }
    }

    // ── buildPrompt — used by AppBlockerService ────────────────────────────────

    fun buildPrompt(query: String, personality: String): String {
        val system = when (personality) {
            "system" -> "You are JARVIS, a cold precise AI. Answer in 1-2 sentences. No filler."
            "sensei" -> "You are a strict sensei. Direct answer only. No sugarcoating."
            "stoic"  -> "You are a stoic AI. Calm, measured. Short answer."
            else     -> "You are JARVIS, a concise helpful assistant. Answer in 1-2 sentences."
        }
        return "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$query<|im_end|>\n<|im_start|>assistant\n"
    }

    // ── buildSystemPrompt ──────────────────────────────────────────────────────

    fun buildSystemPrompt(personality: String): String = when (personality) {
        "system" -> "You are JARVIS, a cold precise AI. Answer in 1-2 sentences. No filler."
        "sensei" -> "You are a strict sensei. Direct answer only. No sugarcoating."
        "stoic"  -> "You are a stoic AI. Calm, measured. Short answer."
        else     -> "You are JARVIS, a concise helpful assistant. Answer in 1-2 sentences."
    }
}
