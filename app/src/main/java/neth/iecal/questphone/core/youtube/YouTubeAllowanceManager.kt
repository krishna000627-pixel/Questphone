package neth.iecal.questphone.core.youtube

import android.content.Context
import androidx.core.content.edit
import neth.iecal.questphone.core.utils.UsageStatsHelper
import nethical.questphone.core.core.utils.getCurrentDate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private const val PREFS = "youtube_allowance"
private const val KEY_DATE = "allowance_date"
private const val KEY_USED_MS = "used_ms"
private const val KEY_EARNED_MS = "earned_ms"

/**
 * Manages the YouTube allowance mode:
 *  - tracks time spent in study apps today
 *  - computes earned YouTube minutes (ratio from UserInfo.youtubeStudyRatio)
 *  - tracks how much YouTube time has been used today
 *  - resets daily
 */
object YouTubeAllowanceManager {

    fun getState(context: Context, studyPackages: Set<String>, ratio: Float): AllowanceState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = getCurrentDate()

        // Reset on new day
        if (prefs.getString(KEY_DATE, "") != today) {
            prefs.edit {
                putString(KEY_DATE, today)
                putLong(KEY_USED_MS, 0L)
                putLong(KEY_EARNED_MS, recomputeEarned(context, studyPackages, ratio))
            }
        }

        val usedMs = prefs.getLong(KEY_USED_MS, 0L)
        val earnedMs = prefs.getLong(KEY_EARNED_MS, recomputeEarned(context, studyPackages, ratio))
        val remainingMs = (earnedMs - usedMs).coerceAtLeast(0L)

        return AllowanceState(
            earnedMinutes = TimeUnit.MILLISECONDS.toMinutes(earnedMs).toInt(),
            usedMinutes = TimeUnit.MILLISECONDS.toMinutes(usedMs).toInt(),
            remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs).toInt()
        )
    }

    /** Called by AppBlockerService when YouTube usage is detected. */
    fun recordYouTubeUsage(context: Context, durationMs: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getLong(KEY_USED_MS, 0L)
        prefs.edit { putLong(KEY_USED_MS, current + durationMs) }
    }

    /** Returns true if YouTube is currently allowed (remaining > 0). */
    fun isYouTubeAllowed(context: Context, studyPackages: Set<String>, ratio: Float): Boolean {
        val state = getState(context, studyPackages, ratio)
        return state.remainingMinutes > 0
    }

    private fun recomputeEarned(context: Context, studyPackages: Set<String>, ratio: Float): Long {
        if (studyPackages.isEmpty()) return 0L
        val helper = UsageStatsHelper(context)
        val today = java.time.LocalDate.now()
        var studyMs = 0L
        try {
            val stats = helper.getForegroundStatsByDay(today)
            studyMs = stats.filter { it.packageName in studyPackages }.sumOf { it.totalTime }
        } catch (_: Exception) { }
        return (studyMs * ratio).toLong()
    }
}

data class AllowanceState(
    val earnedMinutes: Int,
    val usedMinutes: Int,
    val remainingMinutes: Int
)
