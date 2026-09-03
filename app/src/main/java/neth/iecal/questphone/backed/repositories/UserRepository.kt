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
import neth.iecal.questphone.app.screens.game.CoinTransactionLogger
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
        syncStateFlows()
        neth.iecal.questphone.backed.sync.SyncTrigger.push()
    }

    /**
     * Refreshes ALL UI-facing StateFlows from the current userInfo snapshot.
     * Call after any bulk restore so the UI updates instantly with no recompose needed.
     */
    fun syncStateFlows() {
        coinsState.value = userInfo.coins
        currentStreakState.value = userInfo.streak.currentStreak
        activeBoostsState.value = userInfo.active_boosts
    }

    /**
     * Persists userInfo + refreshes UI StateFlows WITHOUT triggering a push.
     * Use exclusively after pull() to avoid a push↔pull loop.
     */
    fun saveUserInfoSilent() {
        context.getSharedPreferences("user_info", Context.MODE_PRIVATE)
            .edit { putString("user_info", json.encodeToString(userInfo)) }
        syncStateFlows()
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

    fun getPluginPackages(): Set<String> = userInfo.pluginPackages
    fun addPluginPackage(pkg: String) { userInfo.pluginPackages.add(pkg); saveUserInfo() }
    fun removePluginPackage(pkg: String) { userInfo.pluginPackages.remove(pkg); saveUserInfo() }

    // -- Game Booster Plugin -----------------------------------------------
    fun getGameBoosterPackage(): String = userInfo.gameBoosterPackage
    fun getGameBoosterName(): String = userInfo.gameBoosterName
    fun setGameBoosterPackage(pkg: String, displayName: String = "") {
        val old = userInfo.gameBoosterPackage
        if (old.isNotEmpty()) userInfo.pluginPackages.remove(old)
        userInfo.gameBoosterPackage = pkg
        userInfo.gameBoosterName = displayName
        if (pkg.isNotEmpty()) userInfo.pluginPackages.add(pkg)
        saveUserInfo()
    }
    fun getGameApps(): Set<String> = userInfo.gameApps
    fun addGameApp(pkg: String) { userInfo.gameApps.add(pkg); saveUserInfo() }
    fun removeGameApp(pkg: String) { userInfo.gameApps.remove(pkg); saveUserInfo() }

    // -- Loan System -------------------------------------------------------
    fun hasActiveLoan(): Boolean = userInfo.loanAmount > 0 && userInfo.loanRepaidAmount < userInfo.loanAmount
    fun getLoanAmount(): Int = userInfo.loanAmount
    fun getLoanRepaidAmount(): Int = userInfo.loanRepaidAmount
    fun getLoanRemaining(): Int = (userInfo.loanAmount - userInfo.loanRepaidAmount).coerceAtLeast(0)
    fun getLoanTakenDate(): String = userInfo.loanTakenDate
    fun getLoanDueDate(): String = userInfo.loanDueDate
    fun getLoanPenaltyDays(): Int = userInfo.loanPenaltyDays

    fun takeLoan(amount: Int) {
        val today = java.time.LocalDate.now()
        userInfo.loanAmount = amount
        userInfo.loanRepaidAmount = 0
        userInfo.loanTakenDate = today.toString()
        userInfo.loanDueDate = today.plusDays(7).toString()
        userInfo.loanPenaltyDays = 0
        userInfo.coins += amount
        coinsState.value += amount
        saveUserInfo()
        CoinTransactionLogger.record(context, amount, "Loan: borrowed $amount GC")
    }

    fun repayLoan(amount: Int): Boolean {
        val available = userInfo.coins
        val remaining = getLoanRemaining()
        val toRepay = minOf(amount, remaining, available)
        if (toRepay <= 0) return false
        userInfo.coins -= toRepay
        coinsState.value -= toRepay
        userInfo.loanRepaidAmount += toRepay
        if (userInfo.loanRepaidAmount >= userInfo.loanAmount) {
            // Loan fully repaid — clear it
            userInfo.loanAmount = 0
            userInfo.loanRepaidAmount = 0
            userInfo.loanTakenDate = ""
            userInfo.loanDueDate = ""
            userInfo.loanPenaltyDays = 0
        }
        saveUserInfo()
        CoinTransactionLogger.record(context, -toRepay, "Loan repayment: $toRepay GC")
        return true
    }

    fun processDailyLoanPenalty() {
        if (!hasActiveLoan()) return
        val due = runCatching { java.time.LocalDate.parse(userInfo.loanDueDate) }.getOrNull() ?: return
        val today = java.time.LocalDate.now()
        if (today.isAfter(due)) {
            val daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(due, today).toInt()
            userInfo.loanPenaltyDays = daysOverdue
            val penalty = (getLoanRemaining() * userInfo.loanInterestRate).toInt().coerceAtLeast(1)
            userInfo.loanAmount += penalty
            saveUserInfo()
        }
    }

    // -- Paper Trading -----------------------------------------------------
    fun getStockHoldings(): Map<String, nethical.questphone.data.StockHolding> = userInfo.stockHoldings
    fun getTradingTransactions(): List<nethical.questphone.data.TradeTransaction> = userInfo.tradingTransactions

    fun buyStock(symbol: String, companyName: String, quantity: Float, pricePerUnit: Float): Boolean {
        val totalCost = (quantity * pricePerUnit).toInt()
        if (userInfo.coins < totalCost) return false
        userInfo.coins -= totalCost
        coinsState.value -= totalCost
        val existing = userInfo.stockHoldings[symbol]
        if (existing != null) {
            val newTotal = existing.totalInvested + totalCost
            val newQty = existing.quantity + quantity
            existing.avgBuyPrice = newTotal / newQty
            existing.quantity = newQty
            existing.totalInvested = newTotal
        } else {
            userInfo.stockHoldings[symbol] = nethical.questphone.data.StockHolding(
                symbol = symbol, companyName = companyName,
                quantity = quantity, avgBuyPrice = pricePerUnit, totalInvested = totalCost.toFloat()
            )
        }
        userInfo.tradingTransactions.add(0, nethical.questphone.data.TradeTransaction(
            symbol = symbol, type = "BUY", quantity = quantity,
            priceAtTrade = pricePerUnit, totalGC = totalCost.toFloat(),
            date = java.time.LocalDate.now().toString()
        ))
        if (userInfo.tradingTransactions.size > 100) userInfo.tradingTransactions.removeLast()
        saveUserInfo()
        CoinTransactionLogger.record(context, -totalCost, "Buy $quantity x $symbol @ $pricePerUnit GC")
        return true
    }

    fun sellStock(symbol: String, quantity: Float, pricePerUnit: Float): Boolean {
        val holding = userInfo.stockHoldings[symbol] ?: return false
        if (holding.quantity < quantity) return false
        val totalGain = (quantity * pricePerUnit).toInt()
        userInfo.coins += totalGain
        coinsState.value += totalGain
        holding.quantity -= quantity
        holding.totalInvested -= (quantity * holding.avgBuyPrice)
        if (holding.quantity <= 0f) userInfo.stockHoldings.remove(symbol)
        userInfo.tradingTransactions.add(0, nethical.questphone.data.TradeTransaction(
            symbol = symbol, type = "SELL", quantity = quantity,
            priceAtTrade = pricePerUnit, totalGC = totalGain.toFloat(),
            date = java.time.LocalDate.now().toString()
        ))
        if (userInfo.tradingTransactions.size > 100) userInfo.tradingTransactions.removeLast()
        saveUserInfo()
        CoinTransactionLogger.record(context, totalGain, "Sell $quantity x $symbol @ $pricePerUnit GC")
        return true
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

    fun useCoins(number: Int, reason: String = "Purchase") {
        userInfo.coins -= number
        coinsState.value -= number
        saveUserInfo()
        CoinTransactionLogger.record(context, -number, reason)
    }

    fun addCoins(addedCoins: Int, reason: String = "Quest reward"): Int {
        // Daily Coin Cap (Plugin Store spec §3): 150 GC/day max across all faucets
        val today = getCurrentDate()
        if (userInfo.dailyCoinsEarnedDate != today) {
            userInfo.dailyCoinsEarnedDate = today
            userInfo.dailyCoinsEarnedAmount = 0
        }
        val remainingRoom = (nethical.questphone.data.PLUGIN_STORE_DAILY_COIN_CAP - userInfo.dailyCoinsEarnedAmount).coerceAtLeast(0)
        val actualAdded = minOf(addedCoins, remainingRoom)
        if (actualAdded <= 0) return 0

        userInfo.dailyCoinsEarnedAmount += actualAdded
        userInfo.coins += actualAdded
        coinsState.value += actualAdded
        saveUserInfo()
        CoinTransactionLogger.record(context, actualAdded, reason)
        return actualAdded
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
