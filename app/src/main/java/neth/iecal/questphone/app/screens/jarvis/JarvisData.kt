package neth.iecal.questphone.app.screens.jarvis

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── Action types ───────────────────────────────────────────────────────────────

enum class CommandAction {
    LAUNCH_APP,       // open an installed app
    NAVIGATE,         // go to a QuestPhone screen
    SPEAK,            // respond with custom text
    TOGGLE,           // flip a setting
    SEQUENCE,         // run multiple sub-commands
    RANDOM,           // pick random response from list
    OPEN_URL,         // open URL in browser
    SHARE,            // share text via share sheet
    CLIPBOARD,        // copy text to clipboard
    NOTIFICATION,     // post local notification
    CALCULATE,        // evaluate a math expression
    CONDITIONAL,      // if/else command
}

// ── Data models ───────────────────────────────────────────────────────────────

@Serializable
data class CustomCommand(
    val id: String = java.util.UUID.randomUUID().toString(),
    val triggers: List<String> = emptyList(),          // ["open notes", "my notes", "notes"]
    val action: CommandAction = CommandAction.SPEAK,
    val actionParam: String = "",                       // package / route / text / url
    val response: String = "",                          // text response shown in chat
    val isEnabled: Boolean = true,
    val category: String = "Custom",
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class JarvisMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class JarvisPrefs(
    val ttsEnabled: Boolean = false,
    val personality: String = "normal",   // normal | system | sensei | stoic
    val fuzzyMatch: Boolean = true,
    val showSuggestions: Boolean = true
)

// ── Storage ───────────────────────────────────────────────────────────────────

object JarvisStorage {
    private const val PREFS = "jarvis_db"
    private const val KEY_COMMANDS = "commands"
    private const val KEY_HISTORY = "history"
    private const val KEY_PREFS = "jarvis_prefs"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun loadCommands(ctx: Context): List<CustomCommand> {
        val raw = prefs(ctx).getString(KEY_COMMANDS, null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    fun saveCommands(ctx: Context, commands: List<CustomCommand>) {
        prefs(ctx).edit().putString(KEY_COMMANDS, json.encodeToString(commands)).apply()
    }

    fun upsertCommand(ctx: Context, cmd: CustomCommand) {
        val all = loadCommands(ctx).toMutableList()
        val idx = all.indexOfFirst { it.id == cmd.id }
        if (idx >= 0) all[idx] = cmd else all.add(cmd)
        saveCommands(ctx, all)
    }

    fun deleteCommand(ctx: Context, id: String) {
        saveCommands(ctx, loadCommands(ctx).filter { it.id != id })
    }

    fun incrementUsage(ctx: Context, id: String) {
        val all = loadCommands(ctx).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) all[idx] = all[idx].copy(usageCount = all[idx].usageCount + 1)
        saveCommands(ctx, all)
    }

    fun loadHistory(ctx: Context): List<JarvisMessage> {
        val raw = prefs(ctx).getString(KEY_HISTORY, null) ?: return emptyList()
        return try { json.decodeFromString<List<JarvisMessage>>(raw).takeLast(100) }
        catch (_: Exception) { emptyList() }
    }

    fun appendMessage(ctx: Context, msg: JarvisMessage) {
        val history = loadHistory(ctx).toMutableList()
        history.add(msg)
        prefs(ctx).edit().putString(KEY_HISTORY, json.encodeToString(history.takeLast(100))).apply()
    }

    fun clearHistory(ctx: Context) {
        prefs(ctx).edit().remove(KEY_HISTORY).apply()
    }

    fun loadJarvisPrefs(ctx: Context): JarvisPrefs {
        val raw = prefs(ctx).getString(KEY_PREFS, null) ?: return JarvisPrefs()
        return try { json.decodeFromString(raw) } catch (_: Exception) { JarvisPrefs() }
    }

    fun saveJarvisPrefs(ctx: Context, p: JarvisPrefs) {
        prefs(ctx).edit().putString(KEY_PREFS, json.encodeToString(p)).apply()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
