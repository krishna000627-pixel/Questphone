package neth.iecal.questphone.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import neth.iecal.questphone.backed.repositories.PluginStoreRepository
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Plugin Store spec §3 — "Weekly Execution: Every Sunday at 00:00 UTC, total upkeep for all
 * owned plugins is calculated against the user's Gold Coin balance." Handles the
 * sufficient-balance deduction, grace-period entry, and grace-expiration -> FROZEN transition.
 */
@HiltWorker
class PluginUpkeepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pluginStoreRepository: PluginStoreRepository,
    private val userRepository: UserRepository
) : CoroutineWorker(appContext, workerParams) {

    @Serializable
    private data class PluginEconomyEntry(
        val packageName: String = "",
        val weeklyUpkeep: Int = 0,
        val gracePeriodDays: Int = 3,
        val emiAvailable: Boolean = false,
        val emiUpkeepMultiplier: Float = 1.5f,
        val emiXpBoostBonus: Int = 10
    )

    override suspend fun doWork(): Result {
        return try {
            val json = withContext(Dispatchers.IO) {
                URL(neth.iecal.questphone.backed.repositories.PluginStoreRepository.PLUGINS_URL).readText()
            }
            val entries: List<PluginEconomyEntry> = Json { ignoreUnknownKeys = true }.decodeFromString(json)

            // Apply EMI multiplier to upkeep for opted-in plugins
            val map = entries.associate { entry ->
                val effectiveUpkeep = userRepository.getEffectiveUpkeep(
                    entry.packageName, entry.weeklyUpkeep, entry.emiUpkeepMultiplier
                )
                entry.packageName to (effectiveUpkeep to entry.gracePeriodDays)
            }
            pluginStoreRepository.processWeeklyUpkeep(map)

            // Process daily loan penalty
            userRepository.processDailyLoanPenalty()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "plugin_weekly_upkeep"

        fun schedule(context: Context) {
            val nowUtc = java.time.ZonedDateTime.now(ZoneOffset.UTC)
            var nextSunday = nowUtc.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .with(LocalTime.MIDNIGHT)
            if (nextSunday.isBefore(nowUtc)) {
                nextSunday = nextSunday.plusWeeks(1)
            }
            val delayMinutes = ChronoUnit.MINUTES.between(nowUtc, nextSunday).coerceAtLeast(0)

            val req = PeriodicWorkRequestBuilder<PluginUpkeepWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
