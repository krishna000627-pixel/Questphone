package neth.iecal.questphone.app.screens.habitica

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.habitica.*
import javax.inject.Inject

@HiltViewModel
class HabiticaViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<HabiticaTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _boss = MutableStateFlow<BossFight?>(null)
    val boss = _boss.asStateFlow()

    val coins = userRepository.coinsState
    val diamonds get() = userRepository.getDiamonds()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            userRepository.resetDailyCompletions()
            _tasks.value = userRepository.getHabiticaTasks()
            _boss.value = userRepository.getBossFight()
        }
    }

    fun completeTask(id: String) {
        userRepository.completeHabiticaTask(id)
        refresh()
    }

    fun incrementHabit(id: String) {
        val t = userRepository.getHabiticaTasks().find { it.id == id } ?: return
        userRepository.updateHabiticaTask(t.copy(counterUp = t.counterUp + 1))
        userRepository.addCoins(t.coinReward)
        userRepository.addXp(t.xpReward)
        refresh()
    }

    fun addTask(t: HabiticaTask) { userRepository.addHabiticaTask(t); refresh() }
    fun deleteTask(id: String) { userRepository.deleteHabiticaTask(id); refresh() }
    fun startBoss(b: BossFight) { userRepository.startBossFight(b); refresh() }
    fun resetBoss() { userRepository.resetBossFight(); refresh() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabiticaScreen(navController: NavController, vm: HabiticaViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsState()
    val boss by vm.boss.collectAsState()
    val coins by vm.coins.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBossSetup by remember { mutableStateOf(false) }

    if (showAddDialog) {
        TaskAddDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { vm.addTask(it); showAddDialog = false }
        )
    }
    if (showBossSetup) {
        BossSetupDialog(
            onDismiss = { showBossSetup = false },
            onStart = { vm.startBoss(it); showBossSetup = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quests & Habits", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                actions = {
                    // Coins
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)) {
                        Text("🪙 $coins", fontSize = 13.sp, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("💎 ${vm.diamonds}", fontSize = 13.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab bar
            TabRow(selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D0D0D),
                contentColor = Color.White) {
                listOf("📅 Dailies", "⚡ Habits", "🐉 Boss").forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(label, fontSize = 12.sp) })
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                when (selectedTab) {
                    // ── DAILIES ────────────────────────────────────────
                    0 -> {
                        val dailies = tasks.filter { it.type == HabiticaTaskType.DAILY }
                            .sortedWith(compareBy({ it.completed }, { it.position }))

                        if (dailies.isEmpty()) {
                            item {
                                EmptyState("No dailies yet. Tap + to add one.")
                            }
                        }
                        items(dailies, key = { it.id }) { task ->
                            DailyCard(
                                task = task,
                                onComplete = { vm.completeTask(task.id) },
                                onDelete = { vm.deleteTask(task.id) }
                            )
                        }
                    }

                    // ── HABITS ─────────────────────────────────────────
                    1 -> {
                        val habits = tasks.filter { it.type == HabiticaTaskType.HABIT }
                        if (habits.isEmpty()) item { EmptyState("No habits yet. Tap + to add a counter habit.") }
                        items(habits, key = { it.id }) { task ->
                            HabitCard(
                                task = task,
                                onIncrement = { vm.incrementHabit(task.id) },
                                onDelete = { vm.deleteTask(task.id) }
                            )
                        }
                    }

                    // ── BOSS FIGHT ─────────────────────────────────────
                    2 -> {
                        item {
                            if (boss == null || (!boss!!.isActive)) {
                                BossStartCard { showBossSetup = true }
                            } else {
                                BossFightCard(boss = boss!!, onReset = { vm.resetBoss() })
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun DailyCard(task: HabiticaTask, onComplete: () -> Unit, onDelete: () -> Unit) {
    val bg = if (task.completed) Color(0xFF0A1A0A) else Color(0xFF0D0D0D)
    val accent = if (task.completed) Color(0xFF4CAF50) else Color(0xFF7B1FA2)

    Row(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(bg)
        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
        .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Check circle
        Box(modifier = Modifier.size(36.dp)
            .clip(CircleShape)
            .background(if (task.completed) Color(0xFF4CAF50) else Color(0xFF1A1A1A))
            .border(2.dp, accent, CircleShape)
            .clickable { if (!task.completed) onComplete() },
            contentAlignment = Alignment.Center
        ) {
            if (task.completed) Text("✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.weight(1f)) {
            Text(task.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (task.completed) Color.Gray else Color.White)
            if (task.notes.isNotEmpty())
                Text(task.notes, fontSize = 11.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🪙 ${task.coinReward}", fontSize = 11.sp, color = Color(0xFFFFB300))
                Text("⭐ ${task.xpReward} xp", fontSize = 11.sp, color = Color(0xFF7B1FA2))
                if (task.streak > 0) Text("🔥 ${task.streak}", fontSize = 11.sp, color = Color(0xFFFF6D00))
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFF333333), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HabitCard(task: HabiticaTask, onIncrement: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF0D0D0D))
        .border(1.dp, Color(0xFF00BCD4).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
        .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(task.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (task.notes.isNotEmpty()) Text(task.notes, fontSize = 11.sp, color = Color.Gray)
            Text("Count: ${task.counterUp}  •  🪙 ${task.coinReward}/tap", fontSize = 11.sp, color = Color(0xFF00BCD4))
        }
        Box(modifier = Modifier.size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF00BCD4).copy(alpha = 0.15f))
            .border(1.dp, Color(0xFF00BCD4), RoundedCornerShape(10.dp))
            .clickable { onIncrement() },
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = 22.sp, color = Color(0xFF00BCD4), fontWeight = FontWeight.Black) }

        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFF333333), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun BossFightCard(boss: BossFight, onReset: () -> Unit) {
    val hpFraction = boss.bossCurrentHp.toFloat() / boss.bossMaxHp.coerceAtLeast(1)
    val rageFraction = boss.bossRage.toFloat() / boss.bossRageMax.coerceAtLeast(1)
    val animHp by animateFloatAsState(hpFraction, tween(600), label = "hp")
    val animRage by animateFloatAsState(rageFraction, tween(600), label = "rage")

    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Color(0xFF0D0D0D))
        .border(2.dp, Color(0xFFEF5350).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (boss.isWon) {
            Text("🎉 BOSS DEFEATED!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            Text("You earned 🪙 ${boss.coinRewardOnWin} coins + ⭐ ${boss.xpRewardOnWin} XP", color = Color.Gray)
            TextButton(onClick = onReset) { Text("Start New Fight") }
            return@Column
        }
        if (boss.isLost) {
            Text("💀 DEFEATED", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF5350))
            Text("The boss overwhelmed you. Train harder.", color = Color.Gray)
            TextButton(onClick = onReset) { Text("Try Again") }
            return@Column
        }

        Text(boss.bossEmoji + " " + boss.bossName, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)

        // Boss HP
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Boss HP", fontSize = 11.sp, color = Color.Gray)
                Text("${boss.bossCurrentHp} / ${boss.bossMaxHp}", fontSize = 11.sp, color = Color.White)
            }
            LinearProgressIndicator(progress = { animHp }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFFEF5350), trackColor = Color(0xFF2A0000))
        }

        // Rage
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Boss Rage", fontSize = 11.sp, color = Color.Gray)
                Text("${boss.bossRage} / ${boss.bossRageMax}", fontSize = 11.sp, color = Color(0xFFFF6D00))
            }
            LinearProgressIndicator(progress = { animRage }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFFFF6D00), trackColor = Color(0xFF1A0D00))
        }

        // Player HP
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Your HP", fontSize = 11.sp, color = Color.Gray)
                Text("${boss.playerHp} / ${boss.playerMaxHp}", fontSize = 11.sp, color = Color(0xFF4CAF50))
            }
            LinearProgressIndicator(
                progress = { boss.playerHp.toFloat() / boss.playerMaxHp.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFF4CAF50), trackColor = Color(0xFF001A00))
        }

        Text("Complete dailies to deal damage. Missing dailies increases boss rage.",
            fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun BossStartCard(onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Color(0xFF0D0D0D))
        .border(1.dp, Color(0xFF333333), RoundedCornerShape(20.dp))
        .clickable { onStart() }
        .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🐉", fontSize = 56.sp)
        Text("Start Boss Fight", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Complete dailies to defeat the boss.\nMiss dailies and it attacks you.", fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable
private fun EmptyState(msg: String) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
private fun TaskAddDialog(onDismiss: () -> Unit, onAdd: (HabiticaTask) -> Unit) {
    var text by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabiticaTaskType.DAILY) }
    var coinReward by remember { mutableIntStateOf(5) }
    var xpReward by remember { mutableIntStateOf(10) }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Add Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                // Type
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(HabiticaTaskType.DAILY, HabiticaTaskType.HABIT).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t },
                            label = { Text(t.name, fontSize = 12.sp) })
                    }
                }

                // Rewards
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙", fontSize = 16.sp)
                    OutlinedTextField(value = "$coinReward", onValueChange = { coinReward = it.toIntOrNull() ?: 5 },
                        label = { Text("Coins") }, modifier = Modifier.weight(1f), singleLine = true)
                    Text("⭐", fontSize = 16.sp)
                    OutlinedTextField(value = "$xpReward", onValueChange = { xpReward = it.toIntOrNull() ?: 10 },
                        label = { Text("XP") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onAdd(HabiticaTask(text = text, notes = notes,
                    type = type, coinReward = coinReward, xpReward = xpReward))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BossSetupDialog(onDismiss: () -> Unit, onStart: (BossFight) -> Unit) {
    var name by remember { mutableStateOf("The Procrastinator") }
    var hp by remember { mutableStateOf("500") }
    var coinReward by remember { mutableStateOf("200") }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Setup Boss Fight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Boss Name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = hp, onValueChange = { hp = it }, label = { Text("Boss HP") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = coinReward, onValueChange = { coinReward = it },
                    label = { Text("Coins on Win") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Complete dailies to damage the boss (based on task coin reward). Missing dailies increases boss rage.", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hpInt = hp.toIntOrNull() ?: 500
                onStart(BossFight(bossName = name, bossMaxHp = hpInt, bossCurrentHp = hpInt, coinRewardOnWin = coinReward.toIntOrNull() ?: 200))
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
