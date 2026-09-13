package nethical.questphone.data.habitica

import kotlinx.serialization.Serializable

/**
 * Stat Points — inspired by Habitica's attribute system.
 * 4 fully renameable stats earned by leveling up.
 * No icons/emoji so custom names always look clean.
 */
@Serializable
data class StatPoints(
    var name1: String = "Strength",
    var value1: Int = 0,
    var name2: String = "Intelligence",
    var value2: Int = 0,
    var name3: String = "Focus",
    var value3: Int = 0,
    var name4: String = "Discipline",
    var value4: Int = 0
)
