package neth.iecal.questphone.core.ai

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf()
)

@Singleton
class KaiMemoryManager @Inject constructor() {

    private val PREFS = "kai_memory"
    private val KEY_SESSIONS = "sessions"
    private val KEY_ACTIVE = "active_session"
    private val MAX_SESSIONS = 20
    private val MAX_MESSAGES_PER_SESSION = 100

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun getSessions(context: Context): List<ChatSession> {
        val raw = prefs(context).getString(KEY_SESSIONS, null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    fun saveSessions(context: Context, sessions: List<ChatSession>) {
        prefs(context).edit { putString(KEY_SESSIONS, json.encodeToString(sessions)) }
    }

    fun getActiveSessionId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE, null)

    fun setActiveSessionId(context: Context, id: String) {
        prefs(context).edit { putString(KEY_ACTIVE, id) }
    }

    fun createSession(context: Context, firstMessage: String? = null): ChatSession {
        val sessions = getSessions(context).toMutableList()
        val title = firstMessage?.take(40) ?: dateLabel()
        val session = ChatSession(title = title)
        sessions.add(0, session)
        if (sessions.size > MAX_SESSIONS) sessions.dropLast(sessions.size - MAX_SESSIONS)
        saveSessions(context, sessions)
        setActiveSessionId(context, session.id)
        return session
    }

    fun addMessage(context: Context, sessionId: String, message: ChatMessage) {
        val sessions = getSessions(context).toMutableList()
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx < 0) return
        val msgs = sessions[idx].messages
        msgs.add(message)
        if (msgs.size > MAX_MESSAGES_PER_SESSION)
            msgs.subList(0, msgs.size - MAX_MESSAGES_PER_SESSION).clear()
        saveSessions(context, sessions)
    }

    fun getSession(context: Context, sessionId: String): ChatSession? =
        getSessions(context).firstOrNull { it.id == sessionId }

    fun deleteSession(context: Context, sessionId: String) {
        val sessions = getSessions(context).toMutableList()
        sessions.removeAll { it.id == sessionId }
        saveSessions(context, sessions)
        if (getActiveSessionId(context) == sessionId)
            setActiveSessionId(context, sessions.firstOrNull()?.id ?: "")
    }

    private fun dateLabel() = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
