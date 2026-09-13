package neth.iecal.questphone.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import neth.iecal.questphone.backed.repositories.PluginStoreRepository
import java.util.concurrent.TimeUnit

/**
 * Plugin Store spec §2 — "3-Hour Cache Auto-Cleaner Worker".
 * Every 3 hours: delete staged APKs for already-installed packages immediately (Rule A),
 * and delete staged APKs for still-uninstalled packages once they're older than 3h (Rule B),
 * resetting each plugin's cache-state pointer as it goes (Rule C).
 */
@HiltWorker
class PluginCacheCleanerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pluginStoreRepository: PluginStoreRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            pluginStoreRepository.runCacheSweep()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "plugin_apk_cache_cleaner"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PluginCacheCleanerWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints(requiresBatteryNotLow = true))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
