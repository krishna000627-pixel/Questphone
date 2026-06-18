package neth.iecal.questphone.app.screens.etc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.StatsRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.ai.GemmaRepository
import nethical.questphone.core.core.utils.getCurrentDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "kai_weekly_summary"
private const val NOTIF_ID = 9902

/**
 * Runs every Sunday evening. Generates a Kai weekly summary covering:
 * - quests completed vs missed this week
 * - stat gains
 * - streak health
 * Then fires a notification; tapping opens GemmaChat.
 */
@HiltWorker
class KaiWeeklySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userRepository: UserRepository,
    private val questRepository: QuestRepository,
    private val statsRepository: StatsRepository,
    private val gemmaRepository: GemmaRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val u = userRepository.userInfo
            val sp = u.statPoints
            val allQuests = questRepository.getAllQuests().first()
            val today = LocalDate.now()
            val weekStart = today.with(DayOfWeek.MONDAY)
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            val completedThisWeek = allQuests.count {
                val last = runCatching { LocalDate.parse(it.last_completed_on, fmt) }.getOrNull()
                last != null && !last.isBefore(weekStart)
            }
            val totalActive = allQuests.count { !it.is_destroyed }

            val prompt = buildString {
                appendLine("Write a short, motivating Sunday summary for a student gamifying their productivity.")
                appendLine("Stats this week: Completed $completedThisWeek / $totalActive quests.")
                appendLine("Current streak: ${u.streak.currentStreak} days.")
                appendLine("Longest streak: ${u.streak.longestStreak} days.")
                appendLine("Stats: ${sp.name1}=${sp.value1}, ${sp.name2}=${sp.value2}, ${sp.name3}=${sp.value3}, ${sp.name4}=${sp.value4}")
                appendLine("Write 3-4 sentences max. Be warm, motivating, and specific. Mention one improvement tip.")
            }

            val result = gemmaRepository.chat(emptyList(), prompt)
            val summaryText = if (result.isSuccess)
                result.getOrNull()?.text ?: "Great week! Keep it up."
            else "Great work this week! Check your progress in the app."

            ensureChannel()
            val launchIntent = applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?.apply { putExtra("nav_to", "gemma_chat/") }
            val pi = android.app.PendingIntent.getActivity(
                applicationContext, 0, launchIntent ?: android.content.Intent(),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_bolt_24)
                .setContentTitle("Kai's Weekly Report ⚡")
                .setContentText(summaryText.take(80))
                .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notif)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun ensureChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kai Weekly Summary", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        fun schedule(context: Context) {
            val today = LocalDate.now()
            val nextSunday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val delayDays = java.time.temporal.ChronoUnit.DAYS.between(today, nextSunday)

            val req = PeriodicWorkRequestBuilder<KaiWeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayDays, TimeUnit.DAYS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "kai_weekly_summary",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
