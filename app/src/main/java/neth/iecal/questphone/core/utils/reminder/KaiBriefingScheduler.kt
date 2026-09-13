package neth.iecal.questphone.core.utils.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepositoryEntryPoint
import nethical.questphone.core.core.utils.getCurrentDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

private const val CHANNEL_ID = "kai_briefing_channel"
private const val NOTIF_ID = 9901
private const val ACTION_KAI_BRIEFING = "neth.iecal.questphone.KAI_BRIEFING"

/**
 * Schedules Kai's daily morning briefing notification.
 * The notification body lists today's quests; tapping opens GemmaChat.
 */
fun scheduleDailyKaiBriefing(context: Context, hourOfDay: Int = 8) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(ACTION_KAI_BRIEFING).setPackage(context.packageName)
    val pi = PendingIntent.getBroadcast(
        context, NOTIF_ID, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
}

fun cancelDailyKaiBriefing(context: Context) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        context, NOTIF_ID,
        Intent(ACTION_KAI_BRIEFING).setPackage(context.packageName),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    ) ?: return
    am.cancel(pi)
}

class KaiBriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KAI_BRIEFING) return
        CoroutineScope(Dispatchers.IO).launch {
            val userRepo = EntryPoints.get(
                context.applicationContext,
                UserRepositoryEntryPoint::class.java
            ).userRepository()
            if (!userRepo.userInfo.dailyBriefingEnabled) return@launch

            val questRepo = EntryPoints.get(
                context.applicationContext,
                neth.iecal.questphone.backed.repositories.QuestRepositoryEntryPoint::class.java
            ).questRepository()
            val today = getCurrentDate()
            val allQuests = questRepo.getAllQuests().first()
            val todayQuests = allQuests.filter { q ->
                !q.is_destroyed && q.last_completed_on != today
            }
            val body = if (todayQuests.isEmpty()) "All quests cleared! Great work 🏆"
            else todayQuests.take(4).joinToString(" · ") { it.title } +
                    if (todayQuests.size > 4) " +${todayQuests.size - 4} more" else ""

            // Add study context to briefing
            val u = userRepo.userInfo
            val studyLines = buildString {
                if (u.studyTargetExam.isNotBlank()) {
                    val daysLeft = runCatching {
                        java.time.temporal.ChronoUnit.DAYS.between(
                            java.time.LocalDate.now(),
                            java.time.LocalDate.parse(u.studyTargetDate))
                    }.getOrNull()
                    if (daysLeft != null) append("📚 $daysLeft days to ${u.studyTargetExam} · ")
                }
                val todayClasses = u.studyClasses.filter {
                    java.time.LocalDate.now().dayOfWeek.value in it.daysOfWeek
                }
                if (todayClasses.isNotEmpty())
                    append("Classes today: ${todayClasses.joinToString(", ") { it.name }}")
            }
            val fullBody = if (studyLines.isNotBlank()) "$body\n$studyLines" else body

            ensureChannel(context)
            val openIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply { putExtra("nav_to", "gemma_chat/") }
            val pi = PendingIntent.getActivity(
                context, 0, openIntent ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_bolt_24)
                .setContentTitle("Good morning, ${u.username.ifBlank { "Explorer" }}! ⚡")
                .setContentText(fullBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kai Daily Briefing", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Morning quest summary from Kai" }
        )
    }
}
