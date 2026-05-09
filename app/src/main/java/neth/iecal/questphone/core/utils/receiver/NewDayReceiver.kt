package neth.iecal.questphone.core.utils.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import neth.iecal.questphone.app.screens.game.handleStreakFreezers
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.core.core.utils.ScreenUsageStatsHelper
import nethical.questphone.core.core.utils.getCurrentDate
import javax.inject.Inject

class NewDayReceiver : BroadcastReceiver() {
    @Inject lateinit var userRepository: UserRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_DATE_CHANGED) {
            Log.d("New Day", "Date changed")
            // Fix #12: Check study quota for the day that just ended (yesterday)
            checkStudyQuotaForYesterday(context)
        }
    }

    /**
     * Fix #12: At midnight, check if yesterday's prime study app screen time met the quota.
     * If not, set the block date to TODAY so AppBlockerService will hard-block non-study apps.
     */
    private fun checkStudyQuotaForYesterday(context: Context) {
        try {
            val primePkg = userRepository.getPrimeStudyPackage()
            if (primePkg.isBlank()) return
            val quotaMs = (userRepository.getDailyStudyQuotaHours() * 3600_000L).toLong()
            // relativeDay = 1 means yesterday
            val statsHelper = ScreenUsageStatsHelper(context)
            val yesterdayStats = statsHelper.getForegroundStatsByRelativeDay(1)
            val studyTimeYesterday = yesterdayStats
                .filter { it.packageName == primePkg }
                .sumOf { it.totalTime }

            val today = getCurrentDate()
            if (studyTimeYesterday < quotaMs) {
                Log.d("StudyQuota", "Quota missed yesterday ($studyTimeYesterday ms < $quotaMs ms). Blocking today.")
                userRepository.setStudyQuotaBlockDate(today)
                sendQuotaNotification(
                    context,
                    "Study Quota Missed!",
                    "You didn't reach your ${userRepository.getDailyStudyQuotaHours()}h study goal. Non-study apps are blocked until you catch up today."
                )
            } else {
                Log.d("StudyQuota", "Quota met yesterday ($studyTimeYesterday ms). No block.")
                userRepository.setStudyQuotaBlockDate("")
            }
        } catch (e: Exception) {
            Log.e("StudyQuota", "Error checking quota: ${e.message}")
        }
    }

    fun handleDayChange() {
        if (userRepository.userInfo.streak.currentStreak != 0) {
            val daysSince = userRepository.checkIfStreakFailed()
            if (daysSince != null) {
                handleStreakFreezers(userRepository.tryUsingStreakFreezers(daysSince))
            }
        }
    }

    private fun sendQuotaNotification(context: Context, title: String, msg: String) {
        val channelId = "studyQuota"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Study Quota Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifies when study quota is missed"
        }
        notificationManager.createNotificationChannel(channel)
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(neth.iecal.questphone.R.drawable.baseline_info_24)
            .setContentTitle(title)
            .setContentText(msg)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(42, notification)
    }
}
