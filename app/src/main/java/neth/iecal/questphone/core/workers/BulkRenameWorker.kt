package neth.iecal.questphone.core.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.ai.GemmaRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs AI bulk rename in the background.
 * Shows a persistent notification with progress. Safe to leave and come back.
 * 
 * Input data keys:
 *   "mode" - "rpg" | "friendly" | "personality"
 *   "packages" - comma-separated package names to rename
 *   "names" - comma-separated real names (aligned with packages)
 */
@HiltWorker
class BulkRenameWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userRepository: UserRepository,
    private val gemmaRepository: GemmaRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mode = inputData.getString("mode") ?: "friendly"
        val packages = inputData.getString("packages")?.split(",") ?: return Result.failure()
        val names = inputData.getString("names")?.split(",") ?: return Result.failure()
        val personality = userRepository.userInfo.kaiPersonality

        ensureNotifChannel()

        packages.forEachIndexed { i, pkg ->
            val realName = names.getOrElse(i) { pkg }
            setForeground(createForegroundInfo(i + 1, packages.size, realName))

            // Skip if already renamed
            if (userRepository.userInfo.appRenames.containsKey(pkg)) return@forEachIndexed

            try {
                val styleHint = when (mode) {
                    "rpg" -> "RPG/fantasy theme. Example: Notes→Tome, Camera→Vision Orb, YouTube→Bard Stage"
                    "friendly" -> "warm friendly modern"
                    else -> "$personality style, creative, short"
                }
                val prompt = "App: $realName. Give a $styleHint display name. Reply with the name ONLY. No explanation. No punctuation. Max 3 words."
                val result = gemmaRepository.quickChat(prompt)
                val suggestion = result.getOrNull()
                    ?.replace(Regex("[\"'*`]"), "")
                    ?.trim()?.take(25) ?: ""

                val isLeaked = suggestion.isBlank() ||
                    suggestion.contains("User") ||
                    suggestion.contains("wants") ||
                    suggestion.length > 30

                if (!isLeaked) {
                    userRepository.setAppRename(pkg, suggestion)
                }
            } catch (_: Exception) { /* skip on error */ }

            delay(400L) // rate limiting buffer
        }

        userRepository.saveUserInfo()
        showCompletionNotif(packages.size)
        return Result.success()
    }

    private fun createForegroundInfo(current: Int, total: Int, appName: String): ForegroundInfo {
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_bolt_24)
            .setContentTitle("AI Renaming Apps ($current/$total)")
            .setContentText("Renaming: $appName")
            .setProgress(total, current, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notif)
    }

    private fun showCompletionNotif(count: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_bolt_24)
            .setContentTitle("✅ AI Rename Complete")
            .setContentText("$count apps renamed. Tap to open launcher.")
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID + 1, notif)
    }

    private fun ensureNotifChannel() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI Bulk Rename", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Background app rename progress" }
        )
    }

    companion object {
        const val CHANNEL_ID = "bulk_rename"
        const val NOTIF_ID = 7700
        const val WORK_NAME = "bulk_ai_rename"

        fun enqueue(
            context: Context,
            mode: String,
            packages: List<String>,
            names: List<String>
        ) {
            val data = workDataOf(
                "mode" to mode,
                "packages" to packages.joinToString(","),
                "names" to names.joinToString(",")
            )
            val req = OneTimeWorkRequestBuilder<BulkRenameWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
