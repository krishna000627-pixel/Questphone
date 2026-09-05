package nethical.questphone.data

import kotlinx.serialization.Serializable

/**
 * A single Plugin Store catalog item, as fetched from plugins.json.
 * Fields match QuestPhone: Plugin Store Specification §1 (UI/card) and §4 (data model).
 */
@Serializable
data class PluginEntry(
    val name: String = "",
    val packageName: String = "",
    val description: String = "",
    val category: String = "Utilities",
    val source: String = "playstore", // "playstore", "github", "direct"
    val githubRepo: String = "",
    val apkAssetPattern: String = ".apk",
    val downloadUrl: String = "",
    val autoPlugin: Boolean = false,
    // -- Economy fields (spec §3/§4) ---------------------------------------
    val tier: String = "Light Plugin",
    val unlockCost: Int = 10,
    val weeklyUpkeep: Int = 3,
    val xpBonusPercent: Int = 5,
    val gracePeriodDays: Int = 3,
    val downloadType: String = "Play Store", // "Play Store" or "Direct APK"
    // -- EMI Plugin fields -------------------------------------------------
    val emiAvailable: Boolean = false,          // whether this plugin supports EMI mode
    val emiUpkeepMultiplier: Float = 1.5f,      // upkeep multiplier when EMI enabled (e.g. 1.5 = 50% more)
    val emiXpBoostBonus: Int = 10               // extra xpBonusPercent when EMI enabled
)
