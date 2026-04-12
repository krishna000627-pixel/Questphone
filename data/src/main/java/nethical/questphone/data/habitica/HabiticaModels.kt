package nethical.questphone.data.habitica

import kotlinx.serialization.Serializable
import java.util.UUID

// ── Task Types ────────────────────────────────────────────────────────
@Serializable
enum class HabiticaTaskType { DAILY, HABIT, TODO }

@Serializable
enum class HabiticaAttribute { STRENGTH, INTELLIGENCE, PERCEPTION, CONSTITUTION }

// ── Core Task (Daily / Habit / Todo) ─────────────────────────────────
@Serializable
data class HabiticaTask(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var notes: String = "",
    var type: HabiticaTaskType = HabiticaTaskType.DAILY,
    var attribute: HabiticaAttribute = HabiticaAttribute.STRENGTH,
    var priority: Float = 1f,          // 0.1=trivial 1=easy 1.5=med 2=hard
    var coinReward: Int = 5,
    var xpReward: Int = 10,

    // Daily-specific
    var isDue: Boolean = true,
    var streak: Int = 0,
    var lastCompleted: String = "",    // yyyy-MM-dd
    var daysOfWeek: Set<String> = setOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun"),
    var startDate: String = "",
    var position: Int = 0,             // 0 = top of list

    // Habit-specific
    var counterUp: Int = 0,
    var counterDown: Int = 0,
    var canScore: Boolean = true,      // up direction
    var canScoreDown: Boolean = false, // down direction

    // Completion
    var completed: Boolean = false,
    var isDestroyed: Boolean = false
) {
    fun isActiveDaily(todayName: String): Boolean =
        type == HabiticaTaskType.DAILY && !isDestroyed && !completed &&
        daysOfWeek.any { it.equals(todayName, ignoreCase = true) }
}

// ── Pet ──────────────────────────────────────────────────────────────
@Serializable
data class HabiticaPet(
    val key: String = "",              // e.g. "Wolf-Base"
    val animal: String = "",           // "Wolf"
    val color: String = "",            // "Base"
    val text: String = "",
    var owned: Boolean = false,
    var currentFeed: Int = 0,          // 0-50, at 50 becomes mount
    var isMount: Boolean = false
)

// ── Boss Fight (offline) ─────────────────────────────────────────────
@Serializable
data class BossFight(
    val id: String = UUID.randomUUID().toString(),
    var bossName: String = "The Procrastinator",
    var bossEmoji: String = "🐉",
    var bossMaxHp: Int = 500,
    var bossCurrentHp: Int = 500,
    var bossRage: Int = 0,             // increases when daily missed
    var bossRageMax: Int = 100,
    var playerDamagePerTask: Int = 20, // damage dealt when task completed
    var bossDamagePerMiss: Int = 10,   // boss deals damage when daily missed
    var playerHp: Int = 50,
    var playerMaxHp: Int = 50,
    var isActive: Boolean = false,
    var isWon: Boolean = false,
    var isLost: Boolean = false,
    var coinRewardOnWin: Int = 200,
    var xpRewardOnWin: Int = 500
)

// ── Custom Achievement ────────────────────────────────────────────────
@Serializable
data class CustomAchievement(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var description: String = "",
    var coinReward: Int = 100,
    var isCompleted: Boolean = false,
    var completedDate: String = ""
)

// ── Stat Points ──────────────────────────────────────────────────────
@Serializable
data class StatPoints(
    var name1: String = "Strength",    var value1: Int = 0,
    var name2: String = "Intelligence", var value2: Int = 0,
    var name3: String = "Focus",       var value3: Int = 0,
    var name4: String = "Discipline",  var value4: Int = 0
)
