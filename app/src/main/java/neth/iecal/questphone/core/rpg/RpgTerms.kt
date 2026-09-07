package neth.iecal.questphone.core.rpg

import nethical.questphone.data.UserInfo

/**
 * Central resolver for all RPG-mode term substitutions.
 * When rpgModeEnabled = false → returns normal term.
 * When true → returns the user-configured RPG term.
 *
 * Usage anywhere: RpgTerms.quest(userInfo) → "Quest" or "Mission" etc.
 */
object RpgTerms {
    fun quest(u: UserInfo)     = if (u.rpgModeEnabled) u.rpgTermQuest        else "Quest"
    fun quests(u: UserInfo)    = if (u.rpgModeEnabled) "${u.rpgTermQuest}s"  else "Quests"
    fun coins(u: UserInfo)     = if (u.rpgModeEnabled) u.rpgTermCoins        else "Coins"
    fun level(u: UserInfo)     = if (u.rpgModeEnabled) u.rpgTermLevel        else "Level"
    fun streak(u: UserInfo)    = if (u.rpgModeEnabled) u.rpgTermStreak       else "Streak"
    fun xp(u: UserInfo)        = if (u.rpgModeEnabled) u.rpgTermXp           else "XP"
    fun appDrawer(u: UserInfo) = if (u.rpgModeEnabled) u.rpgTermAppDrawer    else "Apps"
    fun settings(u: UserInfo)  = if (u.rpgModeEnabled) u.rpgTermSettings     else "Settings"
    fun store(u: UserInfo)     = if (u.rpgModeEnabled) u.rpgTermStore        else "Store"

    /** Always respects manual renames (appRenames map) regardless of RPG mode */
    fun appName(u: UserInfo, packageName: String, realName: String): String {
        val manual = u.appRenames[packageName]
        if (!manual.isNullOrBlank()) return manual
        return realName
    }
}
