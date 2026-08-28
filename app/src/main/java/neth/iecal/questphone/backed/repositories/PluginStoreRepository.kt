package neth.iecal.questphone.backed.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nethical.questphone.data.PLUGIN_STORE_REACTIVATION_PENALTY
import nethical.questphone.data.PluginEntry
import nethical.questphone.data.PluginLifecycleStatus
import nethical.questphone.data.PluginOwnershipState
import java.io.File
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plugin Store spec §2/§3/§4 — ownership, weekly upkeep, grace/frozen lifecycle,
 * the on-disk APK staging cache under cacheDir/plugin_apks/, and an in-memory
 * catalog cache so the launcher's Distraction Blocker Intercept (§2.1) can check
 * whether a package is a plugin even if the user never opened the Plugin Store screen
 * this session.
 */
@Singleton
class PluginStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) {
    companion object {
        const val PLUGINS_URL = "https://raw.githubusercontent.com/krishna000627-pixel/Questphone/main/plugins.json"
    }

    val stagingCacheDir: File
        get() = File(context.cacheDir, "plugin_apks").apply { mkdirs() }

    private val _ownershipTick = MutableStateFlow(0)
    /** Bumped on every mutation so Compose screens observing it recompose. */
    val ownershipTick = _ownershipTick.asStateFlow()

    // ─── Catalog cache (in-memory, refreshed whenever PluginStoreScreen fetches) ──

    private val _catalog = MutableStateFlow<Map<String, PluginEntry>>(emptyMap())
    val catalog = _catalog.asStateFlow()

    fun updateCatalog(entries: List<PluginEntry>) {
        _catalog.value = entries.associateBy { it.packageName }
    }

    fun getCatalogEntry(packageName: String): PluginEntry? = _catalog.value[packageName]

    /**
     * Fetches plugins.json and populates the in-memory catalog. Safe to call from anywhere
     * (launcher startup, Plugin Store screen open, pull-to-refresh) — last call wins.
     */
    suspend fun refreshCatalogFromNetwork(): Result<List<PluginEntry>> = try {
        val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            java.net.URL(PLUGINS_URL).readText()
        }
        val entries: List<PluginEntry> = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(json)
        updateCatalog(entries)
        Result.success(entries)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ─── Distraction Blocker trigger (spec §2.1) ──────────────────────────

    private val _pendingUnlockBlocker = MutableStateFlow<PluginEntry?>(null)
    /** Non-null when a launch attempt on an unowned plugin should show the purchase overlay. */
    val pendingUnlockBlocker = _pendingUnlockBlocker.asStateFlow()

    fun requestUnlockBlocker(entry: PluginEntry) { _pendingUnlockBlocker.value = entry }
    fun dismissUnlockBlocker() { _pendingUnlockBlocker.value = null }

    private fun currentIsoWeek(): String {
        val now = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        return "${now.year}-W${now.get(weekFields.weekOfWeekBasedYear())}"
    }

    fun getState(packageName: String): PluginOwnershipState =
        userRepository.userInfo.pluginOwnership.getOrPut(packageName) { PluginOwnershipState() }

    fun isUnlocked(packageName: String): Boolean = getState(packageName).isUnlocked

    /** Purchase Validation (spec §4): only clears if balance covers the unlock price. */
    fun tryUnlock(packageName: String, unlockCost: Int, pluginName: String): Boolean {
        if (userRepository.userInfo.coins < unlockCost) return false
        userRepository.useCoins(unlockCost, "Plugin unlock: $pluginName")
        val state = getState(packageName)
        state.isUnlocked = true
        state.status = PluginLifecycleStatus.INSTALLED
        state.graceDaysRemaining = 0
        state.lastUpkeepPaidWeek = currentIsoWeek()
        userRepository.saveUserInfo()
        _ownershipTick.value++
        return true
    }

    /** Manual reactivation from FROZEN: pay upkeep + the flat penalty (spec §3). */
    fun reactivate(packageName: String, weeklyUpkeep: Int, pluginName: String): Boolean {
        val total = weeklyUpkeep + PLUGIN_STORE_REACTIVATION_PENALTY
        if (userRepository.userInfo.coins < total) return false
        userRepository.useCoins(total, "Plugin reactivation: $pluginName")
        val state = getState(packageName)
        state.status = PluginLifecycleStatus.INSTALLED
        state.graceDaysRemaining = 0
        state.lastUpkeepPaidWeek = currentIsoWeek()
        userRepository.saveUserInfo()
        _ownershipTick.value++
        return true
    }

    /**
     * Weekly Execution (spec §3): run every Sunday 00:00 for every unlocked plugin.
     * gracePeriodDaysFor: lookup so the caller supplies each plugin's tier-defined grace window.
     */
    fun processWeeklyUpkeep(
        pluginsByPackage: Map<String, Pair<Int, Int>> // packageName -> (weeklyUpkeep, gracePeriodDays)
    ) {
        val week = currentIsoWeek()
        var changed = false
        for ((pkg, costAndGrace) in pluginsByPackage) {
            val (upkeep, graceDays) = costAndGrace
            val state = userRepository.userInfo.pluginOwnership[pkg] ?: continue
            if (!state.isUnlocked) continue
            if (state.lastUpkeepPaidWeek == week) continue // already settled this week

            if (userRepository.userInfo.coins >= upkeep) {
                userRepository.useCoins(upkeep, "Plugin weekly upkeep")
                state.lastUpkeepPaidWeek = week
                state.status = PluginLifecycleStatus.INSTALLED
                state.graceDaysRemaining = 0
            } else if (state.status == PluginLifecycleStatus.INSTALLED) {
                // Insufficient balance -> enter grace period
                state.status = PluginLifecycleStatus.GRACE_PERIOD
                state.graceDaysRemaining = graceDays
            } else if (state.status == PluginLifecycleStatus.GRACE_PERIOD) {
                state.graceDaysRemaining -= 7
                if (state.graceDaysRemaining <= 0) {
                    state.status = PluginLifecycleStatus.FROZEN
                    state.graceDaysRemaining = 0
                }
            }
            changed = true
        }
        if (changed) {
            userRepository.saveUserInfo()
            _ownershipTick.value++
        }
    }

    // ─── Staging cache (spec §2) ───────────────────────────────────────────

    fun cachedApkFile(packageName: String): File = File(stagingCacheDir, "$packageName.apk")

    fun hasValidCachedApk(packageName: String): Boolean {
        val f = cachedApkFile(packageName)
        return f.exists() && f.length() > 0
    }

    fun recordCachedApk(packageName: String) {
        val state = getState(packageName)
        state.cachedApkPath = cachedApkFile(packageName).absolutePath
        state.cachedApkTimestampMillis = System.currentTimeMillis()
        userRepository.saveUserInfo()
    }

    fun clearCacheRecord(packageName: String) {
        val state = userRepository.userInfo.pluginOwnership[packageName] ?: return
        state.cachedApkPath = null
        state.cachedApkTimestampMillis = null
        userRepository.saveUserInfo()
    }

    /**
     * 3-Hour Cache Auto-Cleaner Worker (spec §2) body, called from the periodic WorkManager job.
     * Rule A: package already installed -> delete cached installer immediately.
     * Rule B: not installed AND cache older than 3h -> delete, reset state (Rule C).
     */
    fun runCacheSweep() {
        val threeHoursMillis = 3 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val dir = stagingCacheDir
        val files = dir.listFiles() ?: return
        for (file in files) {
            val pkg = file.name.removeSuffix(".apk")
            val installed = try {
                context.packageManager.getPackageInfo(pkg, 0); true
            } catch (_: Exception) { false }

            if (installed) {
                file.delete()
                clearCacheRecord(pkg)
            } else if (now - file.lastModified() > threeHoursMillis) {
                file.delete()
                clearCacheRecord(pkg)
            }
        }
    }
}
