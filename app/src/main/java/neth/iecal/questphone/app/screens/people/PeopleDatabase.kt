package neth.iecal.questphone.app.screens.people

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CustomField(
    val label: String = "",
    val value: String = ""
)

@Serializable
data class PersonEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val relation: String = "",
    val emoji: String = "👤",
    val notes: String = "",
    val birthday: String = "",
    val phone: String = "",
    val email: String = "",
    val tags: List<String> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object PeopleDatabase {
    private const val PREFS = "people_db"
    private const val KEY = "people_json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(context: Context): List<PersonEntry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, people: List<PersonEntry>) {
        prefs(context).edit().putString(KEY, json.encodeToString(people)).apply()
    }

    fun upsert(context: Context, person: PersonEntry) {
        val all = load(context).toMutableList()
        val idx = all.indexOfFirst { it.id == person.id }
        if (idx >= 0) all[idx] = person.copy(updatedAt = System.currentTimeMillis())
        else all.add(0, person)
        save(context, all)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filter { it.id != id })
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val RELATION_OPTIONS = listOf(
        "Self", "Best Friend", "Friend", "Family", "Classmate",
        "Teacher", "Mentor", "Colleague", "Crush", "Partner", "Rival", "Acquaintance", "Other"
    )

    val EMOJI_OPTIONS = listOf(
        "👤","👦","👧","👨","👩","🧑","👴","👵","🧒","👶",
        "🦸","🧙","🧝","👑","⚔️","🎓","💼","🏆","❤️","🌟",
        "🔥","💪","🎯","🧠","😊","😎","🤝","💫","⚡","🌙"
    )
}
