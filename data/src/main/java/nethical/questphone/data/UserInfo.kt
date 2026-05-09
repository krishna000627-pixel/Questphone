package nethical.questphone.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import nethical.questphone.data.game.Achievements
import nethical.questphone.data.game.InventoryItem
import nethical.questphone.data.tracker.Tracker
import nethical.questphone.data.game.StreakData
import nethical.questphone.data.habitica.StatPoints
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.ExperimentalTime

@Serializable
data class CustomVoiceAction(
    val phrase: String = "",       // what the user says, e.g. "open study app"
    val packageName: String = ""   // app to launch
)

/**
 * Represents the user's information in the game
 * @param active_boosts A map of active boosts in the game. Format <BoostObject,Timestamp>
 *     timeStamp format: yyyy-dd-mm-hh-mm
 */
@Serializable
data class UserInfo constructor(
    var username: String = "",
    var full_name: String = "",
    var has_profile: Boolean = false,
    var xp : Int= 0,
    var coins: Int = 90,
    var level : Int = 1,
    val inventory: HashMap<InventoryItem, Int> = hashMapOf(Pair(InventoryItem.STREAK_FREEZER,2)),
    var customization_info: CustomizationInfo = CustomizationInfo(),
    val achievements: List<Achievements> = listOf(Achievements.THE_EARLY_FEW),
    var active_boosts: HashMap<InventoryItem,String> = hashMapOf(),
    var last_updated: Long = System.currentTimeMillis(),
    @Serializable(with = JavaInstantSerializer::class) var created_on: Instant = Clock.system(ZoneId.systemDefault()).instant(),
    var streak : StreakData = StreakData(),
    var blockedAndroidPackages: Set<String>? = setOf(),
    var unlockedAndroidPackages: MutableMap<String, Long>? = mutableMapOf(),
    var studyApps: Set<String> = setOf(),
    var studyToDistractionRatio: Float = 10f, // 10:1 default
    var lastFullFreeDay: String? = null,
    var hiddenPackages: MutableSet<String> = mutableSetOf(),
    var trackers: MutableList<Tracker> = mutableListOf(),

    // -- Habitica System ---------------------------------------------------
    var diamonds: Int = 0,
    var statPoints: StatPoints = StatPoints(),
    var statPointsToAllocate: Int = 0,

    // -- Profile -----------------------------------------------------------
    var profileType: String = "school",
    var profileClass: String = "12th PCM",
    var profileSkills: String = "AI Prompt Engineering",
    var profileDob: String = "18/03/11",
    var profileSideHustle: String = "",
    var jarvisWakeWord: String = "jarvis",
    var focusStartHour: Int = 7,
    var focusStartMin: Int = 50,
    var focusEndHour: Int = 16,
    var focusEndMin: Int = 0,
    var coinToMinuteRatio: Int = 100,
    var launcherAppName: String = "QuestPhone",
    // Keys matching SidePanelItem.contentDesc — true = hidden from panel
    var sidePanelHidden: MutableSet<String> = mutableSetOf(),
    var sidePanelOrder: MutableList<String> = mutableListOf(),
    var aiModel: String = "gemini-2.0-flash",      // active AI model (gemma-3-27b-it deprecated)
    var aiAvatarIndex: Int = 0,                    // which pixel art avatar (0-19)
    var aiAssistantPackage: String = "",           // "" = built-in, else launch this pkg
    var aiMemory: MutableList<String> = mutableListOf(), // persisted AI memory snippets
    var questCreateCost: Int = 0,                  // coins to create a quest (0=free)
    var questDeleteCost: Int = 100,                // coins to delete a quest via AI
    var aiCoinCostPerMin: Int = 5,                 // coins per minute chatting with Kai
    var pomodoroWorkMinutes: Int = 25,             // Pomodoro work duration
    var pomodoroBreakMinutes: Int = 5,             // Pomodoro short break
    var pomodoroCoinReward: Int = 10,              // coins per completed Pomodoro
    var dailyBriefingEnabled: Boolean = true,      // Kai morning briefing notification
    var dailyBriefingHour: Int = 8,                // hour to send briefing (24h)
    var isFocusModeOn: Boolean = false,            // is focus mode currently active
    // Notification blocker settings
    var notificationBlockEnabled: Boolean = false,
    var blockAllNotificationsInFocus: Boolean = false,   // block ALL notifs during focus
    var blockDistractingAppNotifications: Boolean = true, // block notifs from blocked apps
    // Device admin settings
    var adminLockEnabled: Boolean = false,          // user has chosen to enable device admin
    var questReminderEnabled: Boolean = true,      // remind at quest time_start
    var streakWarningEnabled: Boolean = true,      // warn at 21:00 if streak incomplete
    var youtubeAllowanceEnabled: Boolean = false,  // YouTube allowance mode
    var youtubeStudyRatio: Float = 0.1f,            // 0.1 = 10 min YT per 1h study
    var fcm_tokens : List<String> = listOf(),
    // -- App Rename System -------------------------------------------------
    var appRenames: MutableMap<String, String> = mutableMapOf(), // packageName -> displayName
    // -- Custom Voice Actions (Jarvis) --------------------------------------
    var customVoiceActions: MutableList<CustomVoiceAction> = mutableListOf(),
    // -- Study Quota System ------------------------------------------------
    var primeStudyPackage: String = "",  // pkg name of prime study app
    var dailyStudyQuotaHours: Float = 4f, // required study hours per day
    var studyQuotaBlockDate: String = "", // date when block was triggered (yyyy-MM-dd)
    var strangerModeWhitelistSaved: MutableSet<String> = mutableSetOf(), // saved whitelist for stranger mode
    // -- Boss Battle System ------------------------------------------------
    var lastBossBattleWeek: String = "",           // ISO week "2025-W22" to prevent repeats
    var bossDefeatedCount: Int = 0,                // total bosses defeated

    // -- Quest Chain System ------------------------------------------------
    var activeQuestChainIds: MutableList<String> = mutableListOf(), // chain IDs in progress

    // -- Rival System ------------------------------------------------------
    var rivalName: String = "Shadow",              // auto-generated rival name
    var rivalStreak: Int = 0,                      // rival's simulated streak
    var rivalLevel: Int = 1,                       // rival's simulated level
    var lastRivalUpdate: String = "",              // date rival stats were last updated

    // -- Productivity Score ------------------------------------------------
    var lastProductivityScore: Int = 0,
    var lastProductivityDate: String = "",

    // -- Lockdown Escalation -----------------------------------------------
    var missedQuestDays: Int = 0,                  // consecutive days with ≥1 missed quest
    var lockdownEscalationEnabled: Boolean = false,
    var panicButtonCooldownMs: Long = 0L,          // epoch ms when cooldown expires

    // -- Kai Personality ---------------------------------------------------
    var kaiPersonality: String = "friendly",       // "strict" | "friendly" | "rival"

    // -- RPG Mode ----------------------------------------------------------
    var rpgModeEnabled: Boolean = false,           // OFF by default; renames UI to RPG terms
    // RPG term overrides (used when rpgModeEnabled = true)
    var rpgTermQuest: String = "Mission",          // "Quest" → user choice
    var rpgTermCoins: String = "Gold",             // "Coins" → user choice
    var rpgTermLevel: String = "Power Level",      // "Level" → user choice
    var rpgTermStreak: String = "Discipline",      // "Streak" → user choice
    var rpgTermXp: String = "Cultivation XP",      // "XP" → user choice
    var rpgTermAppDrawer: String = "Grimoire",      // "Apps" → user choice
    var rpgTermSettings: String = "Sanctum",        // "Settings" → user choice
    var rpgTermStore: String = "Emporium",          // "Store" → user choice
    // App RPG rename — enabled separately
    var rpgAppRenameEnabled: Boolean = false,

    @Transient
    var needsSync: Boolean = true,
    @Transient
    var isAnonymous : Boolean = true,
){
    fun getFirstName(): String {
        return full_name.trim().split(" ").firstOrNull() ?: ""
    }

    @OptIn(ExperimentalTime::class)
    fun getCreatedOnString():String{
        return formatInstantToDate(created_on)
    }
}

/**
 * format: yyyy-MM-dd
 */
private fun formatInstantToDate(instant: Instant): String {
    val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return localDate.toString() // yyyy-MM-dd
}


object JavaInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString()) // ISO-8601
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

/**
 * Converts the level to xp required to level up
 */
fun xpToLevelUp(level: Int): Int {
    return (100 * level * level)
}

/**
 * The xp that is rewarded when user completes a quest
 */
fun xpToRewardForQuest(level: Int, multiplier: Int = 1): Int {
    return maxOf((30 * level + 50) * multiplier, 150)
}