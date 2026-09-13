package neth.iecal.questphone.app.screens.etc

import android.content.Context
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.core.core.utils.getCurrentDate

/**
 * Lockdown Escalation System
 *
 * If the user misses quests for 3+ consecutive days AND escalation is enabled:
 *  - Stranger Mode auto-activates for 24h
 *  - Panic button has a 60-second cooldown before unblocking apps
 *
 * Call checkAndEscalate() from the new day receiver or app startup.
 */
object LockdownEscalationManager {

    private const val ESCALATION_THRESHOLD = 3    // days of missed quests
    private const val PANIC_COOLDOWN_MS = 60_000L // 60 seconds

    /**
     * Called once per day (from StreakReminderReceiver or boot).
     * Checks if escalation should trigger.
     */
    fun checkAndEscalate(context: Context, userRepository: UserRepository) {
        val u = userRepository.userInfo
        if (!u.lockdownEscalationEnabled) return

        val today = getCurrentDate()
        // This is called AFTER yesterday is evaluated
        // missedQuestDays is incremented by QuestCompletionChecker if streak failed
        if (u.missedQuestDays >= ESCALATION_THRESHOLD) {
            activateStrangerMode(context, userRepository)
        }
    }

    fun recordMissedDay(userRepository: UserRepository) {
        userRepository.userInfo.missedQuestDays++
        userRepository.saveUserInfo()
    }

    fun recordSuccessfulDay(userRepository: UserRepository) {
        userRepository.userInfo.missedQuestDays = 0
        userRepository.saveUserInfo()
    }

    private fun activateStrangerMode(context: Context, userRepository: UserRepository) {
        val u = userRepository.userInfo
        // Save current whitelist and activate stranger mode
        u.strangerModeWhitelistSaved = u.hiddenPackages.toMutableSet()
        // Stranger mode activation is handled by FocusMode.setStrangerMode
        // We mark the time it should lift (24h from now)
        u.panicButtonCooldownMs = System.currentTimeMillis() + (24 * 3_600_000L)
        userRepository.saveUserInfo()
    }

    /** Returns true if panic button should be on cooldown */
    fun isPanicOnCooldown(userRepository: UserRepository): Boolean {
        val cooldownEnd = userRepository.userInfo.panicButtonCooldownMs
        return System.currentTimeMillis() < cooldownEnd
    }

    /** Returns remaining cooldown seconds */
    fun panicCooldownSeconds(userRepository: UserRepository): Long {
        val remaining = userRepository.userInfo.panicButtonCooldownMs - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000L else 0L
    }

    /** Called when user taps "unlock app" — enforces cooldown */
    fun tryUnlock(userRepository: UserRepository, onAllowed: () -> Unit, onBlocked: (Long) -> Unit) {
        val remaining = panicCooldownSeconds(userRepository)
        if (remaining > 0) {
            onBlocked(remaining)
        } else {
            onAllowed()
        }
    }
}
