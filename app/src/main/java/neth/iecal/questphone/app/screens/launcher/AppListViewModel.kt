package neth.iecal.questphone.app.screens.launcher

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.focus.isStrangerMode
import neth.iecal.questphone.core.focus.getStrangerWhitelist
import neth.iecal.questphone.core.services.AppBlockerService
import neth.iecal.questphone.core.services.AppBlockerServiceInfo
import neth.iecal.questphone.core.services.INTENT_ACTION_UNLOCK_APP
import neth.iecal.questphone.core.utils.managers.QuestHelper
import nethical.questphone.core.core.utils.ScreenUsageStatsHelper
import nethical.questphone.core.core.utils.getCurrentDate
import nethical.questphone.core.core.utils.getCurrentDay
import nethical.questphone.core.core.utils.managers.cacheApps
import nethical.questphone.core.core.utils.managers.getCachedApps
import nethical.questphone.core.core.utils.managers.reloadApps
import nethical.questphone.data.AppInfo
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

@HiltViewModel
class AppListViewModel @Inject constructor(
    application: Application,
    private val userRepository: UserRepository,
    private val questRepository: QuestRepository
) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext

    val coins = userRepository.coinsState

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    // Always deduplicate before storing — OPPO/custom ROMs return same pkg multiple times
    private fun setApps(list: List<AppInfo>) {
        _apps.value = list.distinctBy { it.packageName }
    }

    private val _filteredApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val filteredApps: StateFlow<List<AppInfo>> = _filteredApps.asStateFlow()

    // Always deduplicate before showing in UI
    private fun setFilteredApps(list: List<AppInfo>) {
        _filteredApps.value = list.distinctBy { it.packageName }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _showCoinDialog = MutableStateFlow(false)
    val showCoinDialog = _showCoinDialog.asStateFlow()

    private val _selectedPackage = MutableStateFlow("")
    val selectedPackage = _selectedPackage.asStateFlow()

    private val _distractions = MutableStateFlow<Set<String>>(emptySet())
    val distractions = _distractions.asStateFlow()
    private val unlockedDistractions = MutableStateFlow<Map<String, Long>>(mapOf())

    private val _hiddenPackages = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPackages = _hiddenPackages.asStateFlow()

    private val _showHideConfirm = MutableStateFlow("")
    val showHideConfirm = _showHideConfirm.asStateFlow()


    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    var minutesPerFiveCoins = MutableStateFlow(10)
        private set

    var remainingFreePassesToday = MutableStateFlow(0)

    val isHardLockedQuestsToday = MutableStateFlow<Boolean>(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadApps()
            initFreePasses()
            reloadDistractions()
            reloadUnlockedApps()
            loadHardLockedQuests()
        }
    }

    suspend fun loadHardLockedQuests() {
        questRepository.getHardLockedQuests().collectLatest { unfiltered ->
            val todayDay = getCurrentDay()
            val filtered = unfiltered.filter {
                !it.is_destroyed && it.selected_days.contains(todayDay) && it.last_completed_on != getCurrentDate() &&
                !QuestHelper.isTimeOver(it)
            }
            isHardLockedQuestsToday.value = filtered.isNotEmpty()
        }

    }

    fun loadMinutePer5Coins(){
        val prefs = context.getSharedPreferences("minutes_per_5", Context.MODE_PRIVATE)
        minutesPerFiveCoins.value = prefs.getInt("minutes_per_5", 10)

    }

     suspend fun loadApps() {
         loadMinutePer5Coins()
        val cached = getCachedApps(context)
        if (cached.isNotEmpty()) {
            val deduped = cached.distinctBy { it.packageName }
            // If deduped size differs, the cache was corrupt — overwrite it immediately
            if (deduped.size != cached.size) {
                cacheApps(context, deduped)
            }
            setApps(deduped)
            setFilteredApps(deduped)
            _isLoading.value = false
        }

        withContext(Dispatchers.IO) {
            reloadApps(context.packageManager, context).onSuccess { all ->
                val hidden = userRepository.getHiddenPackages()
                val whitelist = if (context.isStrangerMode()) context.getStrangerWhitelist() else null
                val visible = all
                    .distinctBy { it.packageName } // fix: OPPO/custom ROMs register same pkg twice
                    .filter {
                        it.packageName !in hidden &&
                        (whitelist == null || it.packageName in whitelist)
                    }
                setApps(visible)
                setFilteredApps(visible)
                _isLoading.value = false
            }.onFailure {
                _error.value = it.message
                _isLoading.value = false
            }
        }

    }

    fun reloadDistractions(){
        _distractions.value = userRepository.getBlockedPackages()
        _hiddenPackages.value = userRepository.getHiddenPackages()
    }
    fun reloadUnlockedApps(){
        unlockedDistractions.value = userRepository.getUnlockedPackages()
    }
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        setFilteredApps(_apps.value.filter { it.name.contains(query, ignoreCase = true) })
    }

    fun onAppClick(packageName: String) {
        reloadDistractions()
        loadMinutePer5Coins()
        reloadUnlockedApps()
        val cooldownUntil = unlockedDistractions.value[packageName] ?: 0L
        val isDistraction = _distractions.value.contains(packageName)

        Log.d("Distracting apps", _distractions.value.toString())
        if (isDistraction && (cooldownUntil == -1L || System.currentTimeMillis() > cooldownUntil)) {
            _selectedPackage.value = packageName
            _showCoinDialog.value = true
        } else {
            launchApp(context, packageName)
            onSearchQueryChange("")
        }
    }

    fun onLongAppClick(packageName: String) {
        _showHideConfirm.value = packageName
    }

    fun confirmHideApp(packageName: String) {
        // Fix #2: hide only — do NOT auto-add to blocked/distraction list
        userRepository.unhideIfInDistraction(packageName) // ensure clean state
        userRepository.hidePackageOnly(packageName)
        _hiddenPackages.value = userRepository.getHiddenPackages()
        setApps(_apps.value.filter { it.packageName != packageName })
        setFilteredApps(_filteredApps.value.filter { it.packageName != packageName })
        _showHideConfirm.value = ""
    }

    fun dismissHideConfirm() { _showHideConfirm.value = "" }

    fun reloadHidden() { _hiddenPackages.value = userRepository.getHiddenPackages() }

    // -- App Rename Support -------------------------------------------------
    /** Returns custom display name if set, otherwise the original app name */
    fun getDisplayName(packageName: String): String {
        val rename = userRepository.getAppRenames()[packageName]
        if (!rename.isNullOrBlank()) return rename
        return _apps.value.firstOrNull { it.packageName == packageName }?.name ?: packageName
    }

    /** Returns the custom rename if one is explicitly set, or null */
    fun getAppRenameIfSet(packageName: String): String? {
        return userRepository.getAppRenames()[packageName]?.takeIf { it.isNotBlank() }
    }

    /** Set or clear a custom display name for an app */
    fun renameApp(packageName: String, displayName: String) {
        userRepository.setAppRename(packageName, displayName)
        val query = _searchQuery.value
        setFilteredApps(_apps.value.filter {
            getDisplayName(it.packageName).contains(query, ignoreCase = true) ||
            it.name.contains(query, ignoreCase = true)
        })
    }

    fun onConfirmUnlockApp(coins: Int) {
        Log.d("Unlocking app ${_selectedPackage.value}","duration ${minutesPerFiveCoins.value * coins}")

        requestAppUnlock(minutesPerFiveCoins.value * (coins/5))
        userRepository.useCoins(coins)
        launchApp(context, _selectedPackage.value)
        _showCoinDialog.value = false
    }

    fun requestAppUnlock(cooldownInMins:Int){
        if (
            AppBlockerServiceInfo.appBlockerService == null
        ) {
            startForegroundService(context, Intent(context, AppBlockerService::class.java))
        }

        val cooldownTime =  cooldownInMins * 60_000L
        val pkg = _selectedPackage.value
        val intent = Intent().apply {
            action = INTENT_ACTION_UNLOCK_APP
            putExtra("selected_time", cooldownTime)
            putExtra("package_name", pkg)
        }
        context.sendBroadcast(intent)

    }

    fun dismissDialog() {
        _showCoinDialog.value = false
    }


    @OptIn(ExperimentalTime::class)
    fun calculateAvailableFreePasses(screenTimes: List<Double>): Int {
        if (screenTimes.size < 7) return 3 // Fallback for partial data

        val now = kotlin.time.Clock.System.now()
        val questStreak = userRepository.userInfo.streak.currentStreak
        val daysSinceCreated = userRepository.userInfo.created_on.toKotlinInstant()
            .daysUntil(now, TimeZone.currentSystemDefault())
        val weeksSinceFirstUse = daysSinceCreated / 7.0
        val userLevel = userRepository.userInfo.level

        val weights = listOf(0.25, 0.2, 0.15, 0.15, 0.1, 0.1, 0.05)
        val weightedAvg = screenTimes.zip(weights).sumOf { (t, w) -> t * w }

        val today = screenTimes[0]
        val yesterday = screenTimes[1]
        val yesterdayAvg = screenTimes.drop(1).average()

        val isNewUser = daysSinceCreated < 7
        val isImproving = today < yesterdayAvg - 1
        val isConsistent = questStreak >= (2 + userLevel / 2)

        val generosityBoost = if (isNewUser) 2.0 else (1.5 - 0.1 * weeksSinceFirstUse).coerceAtLeast(0.5)
        val difficulty = 2.0 + (userLevel * 0.25)
        val baseUnlocks = ((weightedAvg / difficulty) * generosityBoost)

        val progressBonus = if (isImproving) 1 else 0
        val streakBonus = if (isConsistent) 1 else 0

        // 📊 Dynamic max based on yesterday's screen time
        val baseCap = (yesterday * 60 / 10).roundToInt() // 10 min = 1 unlock
        val trendFactor = if (isImproving) 0.75 else 1.0
        val generosityDecay = (1.2 - 0.1 * weeksSinceFirstUse).coerceAtLeast(0.5)
        val dynamicMax = (baseCap * trendFactor * generosityDecay).roundToInt().coerceIn(2, 10)

        return (baseUnlocks + progressBonus + streakBonus).roundToInt().coerceIn(1, dynamicMax)
    }

    fun initFreePasses(){
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val lastUsedDate = prefs.getString("last_freepass_date", null)

        if (lastUsedDate == today) {
            remainingFreePassesToday .value= prefs.getInt("freepass_count", 0)
        } else {
            val stats = ScreenUsageStatsHelper(context).getStatsForLast7Days()
            val filteredTimes = stats.filter { it.packageName == selectedPackage.value }.map { it.totalTime.toDouble() }
            remainingFreePassesToday.value = calculateAvailableFreePasses(filteredTimes)
            prefs.edit {
                putString("last_freepass_date", today)
                putInt("freepass_count", remainingFreePassesToday.value)
            }
        }
    }

    fun useFreePass() {

        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val remainingFreePassesToday = prefs.getInt("freepass_count", 0) - 1

        prefs.edit {
            putString("ladistracting_appsst_freepass_date", today)
            putInt("freepass_count", remainingFreePassesToday)
        }

        requestAppUnlock(10)
        launchApp(context,_selectedPackage.value)
        onConfirmUnlockApp(0)
    }


}

fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    intent?.let { context.startActivity(it) }

}
