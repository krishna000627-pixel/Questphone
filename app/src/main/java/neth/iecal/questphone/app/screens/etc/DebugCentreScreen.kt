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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class DebugViewModel @Inject constructor(
    val questRepository: QuestRepository,
    val userRepository: neth.iecal.questphone.backed.repositories.UserRepository
) : ViewModel() {

    var log = mutableStateOf("")

    val PLAN_DELETE_COST = 50  // GC per plan quest deleted
    val QUEST_DELETE_COST = 100 // GC per regular quest deleted

    fun deletePlanQuests() = viewModelScope.launch {
        val all = questRepository.getAllQuestsAsList()
        val plan = all.filter { it.start_date.isNotBlank() && it.auto_destruct.isNotBlank() }
        if (plan.isEmpty()) { log.value = "ℹ No plan quests found"; return@launch }
        val totalCost = PLAN_DELETE_COST // flat 50 GC for entire plan regardless of size
        val coins = userRepository.coinsState.value ?: 0
        if (coins < totalCost) {
            log.value = "❌ Need ◈$totalCost GC to delete plan (${plan.size} quests) · have ◈$coins"
            return@launch
        }
        userRepository.deductCoins(totalCost, "Debug: deleted plan (${plan.size} quests)")
        plan.forEach { questRepository.deleteQuest(it) }
        log.value = "✅ Deleted ${plan.size} plan quests · ◈$totalCost GC spent"
    }

    fun deleteAllQuests() = viewModelScope.launch {
        val all = questRepository.getAllQuestsAsList()
        val plan = all.filter { it.start_date.isNotBlank() && it.auto_destruct.isNotBlank() }
        val regular = all.filter { it.start_date.isBlank() || it.auto_destruct.isBlank() }
        val totalCost = (if (plan.isNotEmpty()) PLAN_DELETE_COST else 0) + (regular.size * QUEST_DELETE_COST)
        val coins = userRepository.coinsState.value ?: 0
        if (coins < totalCost) {
            log.value = "❌ Need ◈$totalCost GC to delete all quests (have ◈$coins)"
            return@launch
        }
        userRepository.deductCoins(totalCost, "Debug: deleted all quests")
        questRepository.deleteAll()
        log.value = "✅ Deleted all quests · ◈$totalCost GC spent (plan flat ◈50 + ${regular.size} regular × ◈100)"
    }

    fun resetBankPrices() {
        // Clears persisted stock prices so they reset to base on next open
        // (no context here — done via SharedPreferences key clear)
        log.value = "ℹ Clear app data or use Settings → Bank → Reset to reset prices"
    }

    fun showQuestStats() = viewModelScope.launch {
        val all = questRepository.getAllQuestsAsList()
        val plan = all.filter { it.start_date.isNotBlank() && it.auto_destruct.isNotBlank() }
        val regular = all.filter { it.start_date.isBlank() && it.auto_destruct.isBlank() }
        log.value = """
📊 Quest Stats:
  Total: ${all.size}
  Plan quests (dated): ${plan.size}
  Regular quests: ${regular.size}
  Completed today: ${all.count { it.last_completed_on == java.time.LocalDate.now().toString() }}
        """.trimIndent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCentreScreen(navController: NavController, vm: DebugViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val log by vm.log
    var showDeleteAll by remember { mutableStateOf(false) }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Delete ALL quests?") },
            text = { Text("This will permanently delete every quest. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllQuests(); showDeleteAll = false }) {
                    Text("Delete All", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") }
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
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Log output
            if (log.isNotBlank()) item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(log, modifier = Modifier.padding(14.dp),
                        fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }

            // ── Quest Tools ───────────────────────────────────────────────
            item {
                DebugSection("⚔ Quest Tools") {
                    DebugButton("📊 Show Quest Stats") { vm.showQuestStats() }
                    DebugButton("🗓 Delete Plan Quests (dated only)",
                        subtitle = "Removes quests with start_date + auto_destruct (imported plans)") {
                        vm.deletePlanQuests()
                    }
                    DebugButton("💣 Delete ALL Quests", color = Color.Red) {
                        showDeleteAll = true
                    }
                }
            }

            // ── Bank Tools ─────────────────────────────────────────────────
            item {
                DebugSection("🏦 Bank Tools") {
                    DebugButton("💹 Reset Stock Prices to Base") {
                        val prefs = ctx.getSharedPreferences("bank_sim", android.content.Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        vm.log.value = "✅ Stock prices reset — reopen Bank to apply"
                    }
                    DebugButton("➕ Add 500 GC (test)") {
                        val prefs = ctx.getSharedPreferences("user_info", android.content.Context.MODE_PRIVATE)
                        vm.log.value = "ℹ Use Jarvis command 'add coins 500' to add GC safely"
                    }
                }
            }

            // ── Jarvis Tools ───────────────────────────────────────────────
            item {
                DebugSection("🤖 Jarvis Tools") {
                    DebugButton("🗑 Clear Jarvis Chat History") {
                        neth.iecal.questphone.app.screens.jarvis.JarvisStorage.clearHistory(ctx)
                        vm.log.value = "✅ Jarvis chat history cleared"
                    }
                    DebugButton("🔌 Reset Offline AI Config") {
                        ctx.getSharedPreferences("jarvis_llm", android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        vm.log.value = "✅ Offline AI config reset — reconfigure in Jarvis → Settings"
                    }
                }
            }

            // ── App Tools ──────────────────────────────────────────────────
            item {
                DebugSection("📱 App Tools") {
                    DebugButton("🧹 Clear Update Cache") {
                        neth.iecal.questphone.backed.update.AppUpdater.clearCache(ctx)
                        vm.log.value = "✅ Update cache cleared"
                    }
                    DebugButton("ℹ App Info") {
                        val pm = ctx.packageManager
                        val info = pm.getPackageInfo(ctx.packageName, 0)
                        vm.log.value = """
📱 App Info:
  Package: ${ctx.packageName}
  Version: ${info.versionName} (${info.versionCode})
  Install: ${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(info.firstInstallTime))}
  Updated: ${java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(info.lastUpdateTime))}
                        """.trimIndent()
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
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
private fun DebugButton(
    label: String,
    subtitle: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
            if (subtitle != null)
                Text(subtitle, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
