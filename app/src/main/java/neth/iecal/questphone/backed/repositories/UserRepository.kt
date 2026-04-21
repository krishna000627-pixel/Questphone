package neth.iecal.questphone.backed.repositories

import android.content.Context
import nethical.questphone.data.habitica.StatPoints
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import nethical.questphone.core.core.utils.getCurrentDate
import nethical.questphone.core.core.utils.getFullTimeAfter
import nethical.questphone.core.core.utils.isTimeOver
import nethical.questphone.data.UserInfo
import nethical.questphone.data.game.InventoryItem
import nethical.questphone.data.tracker.Tracker
import nethical.questphone.data.game.StreakFreezerReturn
import nethical.questphone.data.json
import nethical.questphone.data.xpToLevelUp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserRepositoryEntryPoint {
    fun userRepository(): UserRepository
}

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository,
    private val questRepository: QuestRepository
) {
    var userInfo: UserInfo = loadUserInfo()
    var coinsState = MutableStateFlow(userInfo.coins)
    var currentStreakState = MutableStateFlow(userInfo.streak.currentStreak)

    var activeBoostsState = MutableStateFlow(userInfo.active_boosts)
    
    // the below variables act as a trigger for launching the reward dialog declared in the MainActivity from a
    // different SubScreen.
    fun getUserId(): String {
        return ""
    }

    fun addXp(xp: Int) {
        removeInactiveBooster()
        val multiplier = if (isBoosterActive(InventoryItem.XP_BOOSTER)) 2 else 1
        userInfo.xp += xp * multiplier
        while (userInfo.xp >= xpToLevelUp(userInfo.level )) {
            userInfo.xp -= xpToLevelUp(userInfo.level)
            userInfo.level++
        }
        saveUserInfo()
    }

    fun removeInactiveBooster() {
        userInfo.active_boosts.entries.removeIf { isTimeOver(it.value) }
        activeBoostsState.value = userInfo.active_boosts
        saveUserInfo()
    }

    fun activateBoost(item: InventoryItem, hoursToAdd: Long, minsToAdd: Long){
        userInfo.active_boosts.put(InventoryItem.XP_BOOSTER, getFullTimeAfter(hoursToAdd, minsToAdd))
        saveUserInfo()
        //update state
        activeBoostsState.value = userInfo.active_boosts
    }
    fun isBoosterActive(reward: InventoryItem): Boolean {
        if (userInfo.active_boosts.contains(reward)) {
            val isActive = !isTimeOver(userInfo.active_boosts.getOrDefault(reward, "0069-69-69-69-69"))
            if (!isActive) removeInactiveBooster()
            return isActive
        }
        return false
    }

    fun addItemsToInventory(items: HashMap<InventoryItem, Int>) {
        items.forEach {
            userInfo.inventory[it.key] = it.value + getInventoryItemCount(it.key)
        }
        saveUserInfo()
    }

    fun saveUserInfo(isSetLastUpdated: Boolean = true) {
        if (isSetLastUpdated && !userInfo.isAnonymous) {
            userInfo.last_updated = System.currentTimeMillis()
            userInfo.needsSync = true
        }
        context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
            .edit { putString("user_info", json.encodeToString(userInfo)) }
        coinsState.value = userInfo.coins
    }

    fun getInventoryItemCount(item: InventoryItem): Int {
        return userInfo.inventory.getOrDefault(item, 0)
    }

    fun updateBlockedAppsSet(set: Set<String>){
        userInfo.blockedAndroidPackages = set
        saveUserInfo()
    }

    fun updateUnlockedAppsSet(set: Map<String,Long>){
        userInfo.unlockedAndroidPackages = set.toMutableMap()
        saveUserInfo()
    }
    fun deductFromInventory(item: InventoryItem, count: Int = 1) {
        if (getInventoryItemCount(item) > 0) {
            userInfo.inventory[item] = getInventoryItemCount(item) - count
            if (getInventoryItemCount(item) == 0) {
                userInfo.inventory.remove(item)
            }
            saveUserInfo()
        }
    }
    fun getBlockedPackages():Set<String>{
        return userInfo.blockedAndroidPackages?:emptySet()
    }
    fun getUnlockedPackages(): MutableMap<String, Long>{
        return userInfo.unlockedAndroidPackages ?: mutableMapOf()
    }

    fun getHiddenPackages(): Set<String> {
        return userInfo.hiddenPackages
    }

    fun hidePackage(pkg: String) {
        userInfo.hiddenPackages.add(pkg)
        // hidden apps are also distractions automatically
        val blocked = userInfo.blockedAndroidPackages?.toMutableSet() ?: mutableSetOf()
        blocked.add(pkg)
        userInfo.blockedAndroidPackages = blocked
        saveUserInfo()
    }

    /** Fix #2: hide only from launcher without adding to distraction/blocked list */
    fun hidePackageOnly(pkg: String) {
        userInfo.hiddenPackages.add(pkg)
        saveUserInfo()
    }

    /** Remove from distraction list if was auto-added by old hide behavior */
    fun unhideIfInDistraction(pkg: String) {
        // No-op cleanup — unhidePackage already handles removal
    }

    fun unhidePackage(pkg: String) {
        userInfo.hiddenPackages.remove(pkg)
        saveUserInfo()
        // Reload AppBlockerService blocked list so unhide takes effect immediately
        val intent = android.content.Intent().apply {
            action = "neth.iecal.questphone.REFRESH_APP_BLOCKER"
        }
        // Broadcast will be received by AppBlockerService to reload locked apps
    }

    fun getStudyApps(): Set<String> {
        return userInfo.studyApps
    }

    fun updateStudyApps(set: Set<String>) {
        userInfo.studyApps = set
        saveUserInfo()
    }

    fun getStudyToDistractionRatio(): Float {
        return userInfo.studyToDistractionRatio
    }

    fun updateStudyToDistractionRatio(ratio: Float) {
        userInfo.studyToDistractionRatio = ratio
        saveUserInfo()
    }

    fun setFullFreeDay() {
        userInfo.lastFullFreeDay = LocalDate.now().toString()
        saveUserInfo()
    }

    fun isFullFreeDay(): Boolean {
        return userInfo.lastFullFreeDay == LocalDate.now().toString()
    }

    fun useCoins(number: Int) {
        userInfo.coins -= number
        coinsState.value -= number
        saveUserInfo()
    }

    fun addCoins(addedCoins: Int) {
        userInfo.coins += addedCoins
        coinsState.value+=addedCoins
        saveUserInfo()
    }

    /**
     * @return failing for how many days or null if not failing
     */
    fun checkIfStreakFailed(): Int? {
        val today = LocalDate.now()
        val streakData = userInfo.streak
        val lastCompleted = LocalDate.parse(streakData.lastCompletedDate)
        val daysSince = ChronoUnit.DAYS.between(lastCompleted, today)
        Log.d("streak day since", daysSince.toString())

        if (daysSince > 1) {
            return daysSince.toInt()
        }
            return null

    }



    fun tryUsingStreakFreezers(daysSince:Int): StreakFreezerReturn {
        val requiredFreezers = (daysSince -1).toInt()
        val today = LocalDate.now()
        if (getInventoryItemCount(InventoryItem.STREAK_FREEZER) >= requiredFreezers) {
            deductFromInventory(InventoryItem.STREAK_FREEZER, requiredFreezers)

            val oldStreak = userInfo.streak.currentStreak
            userInfo.streak.currentStreak += requiredFreezers
            userInfo.streak.lastCompletedDate = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            currentStreakState.value = userInfo.streak.currentStreak
            saveUserInfo()
            return StreakFreezerReturn(isOngoing = true,streakFreezersUsed = requiredFreezers, lastStreak = oldStreak)
        } else {
            // User failed streak
            val oldStreak = userInfo.streak.currentStreak
            userInfo.streak.longestStreak = maxOf(userInfo.streak.currentStreak, userInfo.streak.longestStreak)
            userInfo.streak.currentStreak = 0
            currentStreakState.value = userInfo.streak.currentStreak
            updateStreakHistory(oldStreak)
            saveUserInfo()
            return StreakFreezerReturn(isOngoing = false,streakDaysLost = oldStreak)
        }
    }

    private fun updateStreakHistory(oldStreak: Int){
        val streakHistory = userInfo.streak.streakFailureHistory.toMutableMap()
        streakHistory[getCurrentDate()] = oldStreak
        userInfo.streak.streakFailureHistory = streakHistory
    }
    fun continueStreak(): Boolean {
        val today = LocalDate.now()
        val lastCompleted = LocalDate.parse(userInfo.streak.lastCompletedDate)
        val daysSince = ChronoUnit.DAYS.between(lastCompleted, today)

        Log.d("daysSince",daysSince.toString())
        if(daysSince!=0L){
            userInfo.streak.currentStreak += 1
            userInfo.streak.longestStreak = maxOf(userInfo.streak.currentStreak, userInfo.streak.longestStreak)
            userInfo.streak.lastCompletedDate = getCurrentDate()
            currentStreakState.value = userInfo.streak.currentStreak

            saveUserInfo()
            return true
        }
        return false
    }


    fun calculateLevelUpInvRewards(): HashMap<InventoryItem, Int> {
        val rewards = hashMapOf<InventoryItem, Int>()
        rewards[InventoryItem.QUEST_SKIPPER] = 1
        if(userInfo.level == 2) rewards[InventoryItem.REWARD_TIME_EDITOR] = 1
        if (userInfo.level % 2 == 0) rewards[InventoryItem.XP_BOOSTER] = 1
        if (userInfo.level % 5 == 0) rewards[InventoryItem.STREAK_FREEZER] = 1
        return rewards
    }
    fun calculateLevelUpCoinsRewards(): Int {
        return maxOf(userInfo.level.times(userInfo.level),50)
    }


    @OptIn(ExperimentalTime::class)
    fun loadUserInfo(): UserInfo {
        val sharedPreferences = context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
        val userInfoJson = sharedPreferences.getString("user_info", null)
        return userInfoJson?.let {
            json.decodeFromString(it)
        } ?: UserInfo()
    }

    private fun deleteLocalUserInfoCache(){
        val sharedPreferences = context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
        sharedPreferences.edit { remove("user_info") }
    }

    suspend fun signOut() {
        context.deleteSharedPreferences("crnt_pg_onboard")
        context.deleteSharedPreferences("onboard")
        deleteLocalUserInfoCache()

        questRepository.deleteAll()
        statsRepository.deleteAll()

    }

    fun getTrackers(): List<Tracker> = userInfo.trackers

    fun addTracker(tracker: Tracker) {
        userInfo.trackers.add(tracker)
        saveUserInfo()
    }

    fun updateTracker(tracker: Tracker) {
        val idx = userInfo.trackers.indexOfFirst { it.id == tracker.id }
        if (idx >= 0) { userInfo.trackers[idx] = tracker; saveUserInfo() }
    }

    fun deleteTracker(id: String) {
        userInfo.trackers.removeAll { it.id == id }
        saveUserInfo()
    }

    fun getSidePanelHidden(): Set<String> = userInfo.sidePanelHidden
    fun setPanelItemHidden(key: String, hidden: Boolean) {
        if (hidden) userInfo.sidePanelHidden.add(key)
        else userInfo.sidePanelHidden.remove(key)
        saveUserInfo()
    }

    fun getSidePanelOrder(): List<String> = userInfo.sidePanelOrder
    fun saveSidePanelOrder(order: List<String>) {
        userInfo.sidePanelOrder = order.toMutableList()
        saveUserInfo()
    }

    // -- App Rename System --------------------------------------------------
    fun getAppRenames(): Map<String, String> = userInfo.appRenames
    fun setAppRename(packageName: String, displayName: String) {
        if (displayName.isBlank()) userInfo.appRenames.remove(packageName)
        else userInfo.appRenames[packageName] = displayName.trim()
        saveUserInfo()
    }
    fun getAppDisplayName(packageName: String, fallback: String): String =
        userInfo.appRenames[packageName] ?: fallback

    // -- Custom Voice Actions -----------------------------------------------
    fun getCustomVoiceActions(): List<nethical.questphone.data.CustomVoiceAction> = userInfo.customVoiceActions
    fun addCustomVoiceAction(action: nethical.questphone.data.CustomVoiceAction) {
        userInfo.customVoiceActions.add(action); saveUserInfo()
    }
    fun removeCustomVoiceAction(phrase: String) {
        userInfo.customVoiceActions.removeAll { it.phrase.equals(phrase, ignoreCase = true) }
        saveUserInfo()
    }
    fun updateCustomVoiceAction(old: String, new: nethical.questphone.data.CustomVoiceAction) {
        val idx = userInfo.customVoiceActions.indexOfFirst { it.phrase.equals(old, ignoreCase = true) }
        if (idx >= 0) { userInfo.customVoiceActions[idx] = new; saveUserInfo() }
    }

    // -- Study Quota System -------------------------------------------------
    fun getPrimeStudyPackage(): String = userInfo.primeStudyPackage
    fun setPrimeStudyPackage(pkg: String) { userInfo.primeStudyPackage = pkg; saveUserInfo() }
    fun getDailyStudyQuotaHours(): Float = userInfo.dailyStudyQuotaHours
    fun setDailyStudyQuotaHours(hours: Float) { userInfo.dailyStudyQuotaHours = hours; saveUserInfo() }
    fun getStudyQuotaBlockDate(): String = userInfo.studyQuotaBlockDate
    fun setStudyQuotaBlockDate(date: String) { userInfo.studyQuotaBlockDate = date; saveUserInfo() }

    // -- Stranger Mode Whitelist --------------------------------------------
    fun getStrangerWhitelistSaved(): Set<String> = userInfo.strangerModeWhitelistSaved
    fun updateStrangerWhitelistSaved(set: Set<String>) {
        userInfo.strangerModeWhitelistSaved = set.toMutableSet(); saveUserInfo()
    }

    fun getStatPoints() = userInfo.statPoints
    fun allocateStatPoint(slot: Int) {
        if (userInfo.statPointsToAllocate <= 0) return
        val sp = userInfo.statPoints
        userInfo.statPoints = when (slot) {
            1 -> sp.copy(value1 = sp.value1 + 1); 2 -> sp.copy(value2 = sp.value2 + 1)
            3 -> sp.copy(value3 = sp.value3 + 1); 4 -> sp.copy(value4 = sp.value4 + 1)
            else -> sp
        }
        userInfo.statPointsToAllocate--; saveUserInfo()
    }
    fun renameStatPoint(slot: Int, name: String) {
        val sp = userInfo.statPoints
        userInfo.statPoints = when (slot) {
            1 -> sp.copy(name1 = name); 2 -> sp.copy(name2 = name)
            3 -> sp.copy(name3 = name); 4 -> sp.copy(name4 = name)
            else -> sp
        }
        saveUserInfo()
    }
    /** Restore from cloud backup — overwrites local data */
    fun restoreUserInfo(restored: nethical.questphone.data.UserInfo) {
        userInfo.coins = restored.coins
        userInfo.xp = restored.xp
        userInfo.level = restored.level
        userInfo.streak = restored.streak
        userInfo.trackers.clear()
        userInfo.trackers.addAll(restored.trackers)
        userInfo.statPoints = restored.statPoints
        userInfo.statPointsToAllocate = restored.statPointsToAllocate
        userInfo.diamonds = restored.diamonds
        saveUserInfo(isSetLastUpdated = false)
    }

    fun saveFcmToken(string: String) {
        val tokens = userInfo.fcm_tokens.toMutableList()
        tokens.add(string)
        userInfo.fcm_tokens = tokens
        saveUserInfo()
        Log.d("saved Fcm token", tokens.toString())
    }
}
