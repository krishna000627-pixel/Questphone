package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    val questRepository: QuestRepository,
    val userRepository: UserRepository
) : ViewModel() {

    var log = mutableStateOf("")

    private val PLAN_DELETE_COST  = 50
    private val QUEST_DELETE_COST = 100

    // ── Quest ─────────────────────────────────────────────────────

    fun showQuestStats() = viewModelScope.launch {
        val all  = questRepository.getAllQuestsAsList()
        val plan = all.filter { it.start_date.isNotBlank() && it.auto_destruct.isNotBlank() }
        val reg  = all.filter { it.start_date.isBlank() && it.auto_destruct.isBlank() }
        val today = java.time.LocalDate.now().toString()
        log.value = """
📊 Quest Stats
  Total quests   : ${all.size}
  Plan quests    : ${plan.size}  (dated)
  Regular quests : ${reg.size}
  Done today     : ${all.count { it.last_completed_on == today }}
  Locked (hard)  : ${all.count { it.isHardLock }}
  Active days    : ${all.flatMap { it.selected_days }.distinct().sorted()}
        """.trimIndent()
    }

    fun deletePlanQuests() = viewModelScope.launch {
        val all  = questRepository.getAllQuestsAsList()
        val plan = all.filter { it.start_date.isNotBlank() && it.auto_destruct.isNotBlank() }
        if (plan.isEmpty()) { log.value = "ℹ No plan quests found"; return@launch }
        val coins = userRepository.coinsState.value ?: 0
        if (coins < PLAN_DELETE_COST) {
            log.value = "❌ Need ◈$PLAN_DELETE_COST GC · have ◈$coins"
            return@launch
        }
        userRepository.deductCoins(PLAN_DELETE_COST, "Debug: deleted plan (${plan.size} quests)")
        plan.forEach { questRepository.deleteQuest(it) }
        log.value = "✅ Deleted ${plan.size} plan quests · ◈$PLAN_DELETE_COST GC spent"
    }

    fun deleteAllQuests() = viewModelScope.launch {
        val all  = questRepository.getAllQuestsAsList()
        val plan = all.filter { it.start_date.isNotBlank() && it.auto_destruct.isNotBlank() }
        val reg  = all.filter { it.start_date.isBlank() || it.auto_destruct.isBlank() }
        val cost = (if (plan.isNotEmpty()) PLAN_DELETE_COST else 0) + (reg.size * QUEST_DELETE_COST)
        val coins = userRepository.coinsState.value ?: 0
        if (coins < cost) {
            log.value = "❌ Need ◈$cost GC · have ◈$coins\n  (plan flat ◈$PLAN_DELETE_COST + ${reg.size} regular × ◈$QUEST_DELETE_COST)"
            return@launch
        }
        userRepository.deductCoins(cost, "Debug: deleted all ${all.size} quests")
        questRepository.deleteAll()
        log.value = "✅ Deleted all ${all.size} quests · ◈$cost GC spent"
    }

    fun fixPlanQuestDates() = viewModelScope.launch {
        // Fix old quests where auto_destruct = start_date + 1 day (off by one bug)
        val all  = questRepository.getAllQuestsAsList()
        val plan = all.filter { it.start_date.isNotBlank() && it.auto_destruct.isNotBlank() }
        var fixed = 0
        for (q in plan) {
            val start = java.time.LocalDate.parse(q.start_date)
            val destruct = java.time.LocalDate.parse(q.auto_destruct)
            if (destruct == start.plusDays(1)) {
                questRepository.updateQuest(q.copy(auto_destruct = start.toString()))
                fixed++
            }
        }
        log.value = if (fixed > 0) "✅ Fixed $fixed plan quests (auto_destruct shifted back 1 day)"
                    else "ℹ No quests needed fixing"
    }

    // ── Bank ──────────────────────────────────────────────────────

    fun showBankStats(ctx: android.content.Context) {
        val holdings = userRepository.getStockHoldings()
        val txns = userRepository.getTransactionHistory()
        val coins = userRepository.coinsState.value ?: 0
        log.value = """
🏦 Bank Stats
  GC Balance   : ◈$coins
  Holdings     : ${holdings.size} stocks
  Transactions : ${txns.size} total
  ${holdings.entries.joinToString("\n  ") { (sym, h) ->
    "$sym: ${h.quantity} shares @ avg ◈${"%.2f".format(h.avgBuyPrice)}"
  }}
        """.trimIndent()
    }

    fun resetBankPrices(ctx: android.content.Context) {
        ctx.getSharedPreferences("bank_sim", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        log.value = "✅ Stock prices reset to base — reopen Bank to apply"
    }

    fun clearHoldings(ctx: android.content.Context) {
        userRepository.clearStockHoldings()
        ctx.getSharedPreferences("bank_sim", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        log.value = "✅ All holdings cleared + prices reset"
    }

    // ── Jarvis ────────────────────────────────────────────────────

    fun clearJarvisHistory(ctx: android.content.Context) {
        neth.iecal.questphone.app.screens.jarvis.JarvisStorage.clearHistory(ctx)
        log.value = "✅ Jarvis chat history cleared"
    }

    fun resetOfflineAi(ctx: android.content.Context) {
        ctx.getSharedPreferences("jarvis_llm", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        log.value = "✅ Offline AI config reset"
    }

    // ── App ───────────────────────────────────────────────────────

    fun showAppInfo(ctx: android.content.Context) {
        val pm   = ctx.packageManager
        val info = pm.getPackageInfo(ctx.packageName, 0)
        val fmt  = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        log.value = """
📱 App Info
  Version  : ${info.versionName} (code ${info.versionCode})
  Package  : ${ctx.packageName}
  Installed: ${fmt.format(java.util.Date(info.firstInstallTime))}
  Updated  : ${fmt.format(java.util.Date(info.lastUpdateTime))}
  GC coins : ◈${userRepository.coinsState.value ?: 0}
        """.trimIndent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCentreScreen(navController: NavController, vm: DebugViewModel = hiltViewModel()) {
    val ctx  = LocalContext.current
    val log  by vm.log
    var confirmDialog by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    confirmDialog?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title  = { Text("Confirm") },
            text   = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { action(); confirmDialog = null }) {
                    Text("Confirm", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛠 Debug Centre", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── Log ───────────────────────────────────────────────
            if (log.isNotBlank()) item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Text(log, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.log.value = "" },
                            contentPadding = PaddingValues(0.dp)) {
                            Text("✕", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Quest Tools ───────────────────────────────────────
            item {
                DebugSection("⚔ Quest Tools") {
                    DebugBtn("📊 Quest Stats") { vm.showQuestStats() }
                    DebugBtn("🔧 Fix Plan Quest Dates",
                        sub = "Corrects auto_destruct off-by-one on old imported plans") {
                        vm.fixPlanQuestDates()
                    }
                    DebugBtn("🗓 Delete Plan Quests · ◈50 flat",
                        sub = "Removes all dated plan quests (start_date + auto_destruct set)") {
                        confirmDialog = "Delete all plan quests for ◈50 GC?" to { vm.deletePlanQuests() }
                    }
                    DebugBtn("💣 Delete ALL Quests", color = Color.Red,
                        sub = "◈50 (plan) + ◈100 per regular quest") {
                        confirmDialog = "Delete every single quest? This cannot be undone." to { vm.deleteAllQuests() }
                    }
                }
            }

            // ── Bank Tools ────────────────────────────────────────
            item {
                DebugSection("🏦 Bank Tools") {
                    DebugBtn("📊 Bank Stats") { vm.showBankStats(ctx) }
                    DebugBtn("💹 Reset Stock Prices",
                        sub = "Prices go back to base values on next Bank open") {
                        vm.resetBankPrices(ctx)
                    }
                    DebugBtn("🗑 Clear All Holdings", color = Color.Red,
                        sub = "Removes all stocks from portfolio + resets prices") {
                        confirmDialog = "Clear all stock holdings and reset prices?" to { vm.clearHoldings(ctx) }
                    }
                }
            }

            // ── Jarvis Tools ──────────────────────────────────────
            item {
                DebugSection("🤖 Jarvis Tools") {
                    DebugBtn("🗑 Clear Chat History") { vm.clearJarvisHistory(ctx) }
                    DebugBtn("🔌 Reset Offline AI Config") { vm.resetOfflineAi(ctx) }
                }
            }

            // ── App Tools ─────────────────────────────────────────
            item {
                DebugSection("📱 App Tools") {
                    DebugBtn("ℹ App Info") { vm.showAppInfo(ctx) }
                    DebugBtn("🧹 Clear Update Cache") {
                        neth.iecal.questphone.backed.update.AppUpdater.clearCache(ctx)
                        vm.log.value = "✅ Update cache cleared"
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun DebugBtn(
    label: String,
    sub:   String? = null,
    color: Color   = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
            if (sub != null)
                Text(sub, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
    }
}
