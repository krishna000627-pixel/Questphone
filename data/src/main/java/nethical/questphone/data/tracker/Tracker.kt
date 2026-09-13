package nethical.questphone.data.tracker

import kotlinx.serialization.Serializable

@Serializable
enum class TrackerType {
    COUNTDOWN,   // e.g. "3 days left"
    BACKLOG,     // e.g. "Lecture 1 — backlog left"
    COUNTER,     // simple number that goes up
    CHECKBOX     // done / not done
}

@Serializable
data class Tracker(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "",
    var emoji: String = "📌",
    var type: TrackerType = TrackerType.COUNTDOWN,
    var value: Int = 0,          // current value (days left, backlog count, counter)
    var target: Int = 0,         // target (0 = no target)
    var note: String = "",       // e.g. "Chapter 3 pending"
    var isCompleted: Boolean = false,
    var color: Long = 0xFF1565C0 // stored as Long for serialization
)
