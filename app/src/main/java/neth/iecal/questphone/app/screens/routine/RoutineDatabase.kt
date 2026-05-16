package neth.iecal.questphone.app.screens.routine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RoutineStep(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",   // flavour / story text for this step
    val durationMinutes: Int = 0,   // 0 = no timer
    val completed: Boolean = false
)

@Serializable
data class Routine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val storyIntro: String = "",    // narrative flavour text shown before steps
    val emoji: String = "⚔️",
    val steps: List<RoutineStep> = emptyList(),
    val isEnabled: Boolean = true,
    val lastCompletedDate: String = "",
    val totalCompletions: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

object RoutineDatabase {
    private const val PREFS = "routine_db"
    private const val KEY = "routines_json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(context: Context): List<Routine> {
        val raw = prefs(context).getString(KEY, null) ?: return defaultRoutines()
        return try { json.decodeFromString(raw) } catch (_: Exception) { defaultRoutines() }
    }

    fun save(context: Context, routines: List<Routine>) {
        prefs(context).edit().putString(KEY, json.encodeToString(routines)).apply()
    }

    fun upsert(context: Context, routine: Routine) {
        val all = load(context).toMutableList()
        val idx = all.indexOfFirst { it.id == routine.id }
        if (idx >= 0) all[idx] = routine else all.add(0, routine)
        save(context, all)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filter { it.id != id })
    }

    fun toggle(context: Context, id: String) {
        val all = load(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) all[idx] = all[idx].copy(isEnabled = !all[idx].isEnabled)
        save(context, all)
    }

    /** Mark a routine as completed today, incrementing its counter */
    fun markCompleted(context: Context, id: String) {
        val today = java.time.LocalDate.now().toString()
        val all = load(context).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) all[idx] = all[idx].copy(
            lastCompletedDate = today,
            totalCompletions = all[idx].totalCompletions + 1,
            steps = all[idx].steps.map { it.copy(completed = false) } // reset for tomorrow
        )
        save(context, all)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val EMOJI_OPTIONS = listOf(
        "⚔️","🌅","🧘","📚","🏃","💪","🌙","🔥","🎯","🧠",
        "✍️","🎨","🎵","🌿","☕","⚡","🛡️","🗡️","🌊","🦅"
    )

    /** Ships with two example routines so the screen isn't empty on first launch */
    private fun defaultRoutines() = listOf(
        Routine(
            name = "Morning Warrior Ritual",
            emoji = "🌅",
            storyIntro = "The sun rises. The realm stirs. Before the world demands your strength, claim it for yourself. This is your sacred hour.",
            steps = listOf(
                RoutineStep(title = "Rise without snooze", description = "The warrior does not negotiate with weakness. Rise on the first call.", durationMinutes = 0),
                RoutineStep(title = "Cold water — face first", description = "Shock the mind awake. The discomfort is the point.", durationMinutes = 1),
                RoutineStep(title = "5-minute stretch", description = "Prepare the vessel. A blade unsharpened grows dull.", durationMinutes = 5),
                RoutineStep(title = "Set your 3 targets for the day", description = "Not tasks — targets. Things that, if done, make today a victory.", durationMinutes = 3)
            ),
            isEnabled = true
        ),
        Routine(
            name = "Night Sage Wind-Down",
            emoji = "🌙",
            storyIntro = "The day's battles are done. Even the greatest mage must let the mind rest. What you protect in sleep, you wield more sharply at dawn.",
            steps = listOf(
                RoutineStep(title = "Lock the scroll (no phone)", description = "Set the device aside. The feed will still be there. Your sleep won't.", durationMinutes = 0),
                RoutineStep(title = "Write one thing you won today", description = "Even small victories are worth recording. The chronicle demands honesty.", durationMinutes = 3),
                RoutineStep(title = "Read for 10 minutes", description = "Feed the mind something chosen, not algorithmic.", durationMinutes = 10),
                RoutineStep(title = "Dim the lights, breathe slow", description = "Signal to the body: the day is done. The guardian rests.", durationMinutes = 5)
            ),
            isEnabled = true
        )
    )
}
