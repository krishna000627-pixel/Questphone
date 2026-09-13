package neth.iecal.questphone.core.sync

import nethical.questphone.data.UserInfo

/**
 * Strips all fields that must NEVER leave the device before any sync operation.
 *
 * Fields stripped:
 *  - adminLockEnabled          (security posture — private)
 *  - notificationBlockEnabled  (security posture — private)
 *  - blockAllNotificationsInFocus
 *  - blockDistractingAppNotifications
 *  - panicButtonCooldownMs     (exploit vector if forged)
 *  - missedQuestDays           (discipline data — personal)
 *  - lockdownEscalationEnabled (security posture — private)
 *  - lastRivalUpdate / rivalStreak / rivalLevel (local gameplay state)
 *  - lastBossBattleWeek / bossDefeatedCount    (local gameplay state)
 *  - lastProductivityScore / lastProductivityDate
 *
 * Fields kept (safe to sync):
 *  - coins, xp, level, streak, statPoints, customization_info
 *  - username, full_name, quests (via separate channel)
 *  - kaiPersonality (preference, not sensitive)
 *  - studyApps, dailyStudyQuotaHours, dailyBriefingEnabled etc.
 */
object SyncSanitizer {

    fun sanitizeForSync(info: UserInfo): UserInfo = info.copy(
        // Security fields — never synced
        adminLockEnabled = false,
        notificationBlockEnabled = false,
        blockAllNotificationsInFocus = false,
        blockDistractingAppNotifications = true,
        panicButtonCooldownMs = 0L,
        missedQuestDays = 0,
        lockdownEscalationEnabled = false,

        // Local gameplay state — each device manages its own
        lastBossBattleWeek = "",
        bossDefeatedCount = 0,
        rivalStreak = 0,
        rivalLevel = 1,
        lastRivalUpdate = "",
        lastProductivityScore = 0,
        lastProductivityDate = "",

        // Always clear active chain IDs — chain state is per-device
        activeQuestChainIds = mutableListOf(),

        // Never sync app renames — they're device-specific
        appRenames = mutableMapOf(),

        // Sync flag
        needsSync = false
    )

    /**
     * Validates incoming UserInfo from sync before applying it.
     * Rejects payloads that look tampered with.
     * Returns null if the payload should be rejected.
     */
    fun validateIncoming(incoming: UserInfo, current: UserInfo): UserInfo? {
        // Reject if coins would jump by more than 10x current (tamper detection)
        if (current.coins > 0 && incoming.coins > current.coins * 10) return null

        // Reject if level would decrease (invalid)
        if (incoming.level < 0) return null

        // Reject if streak would be impossibly long (> 3650 days / 10 years)
        if (incoming.streak.currentStreak > 3650) return null

        // Strip all security fields regardless of what remote sent
        return sanitizeForSync(incoming)
    }
}
