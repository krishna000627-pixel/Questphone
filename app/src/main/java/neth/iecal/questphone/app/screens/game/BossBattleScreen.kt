package neth.iecal.questphone.app.screens.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.core.core.utils.getCurrentDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import javax.inject.Inject

data class BossData(
    val name: String,
    val emoji: String,
    val description: String,
    val hpMax: Int,
    val reward: Int,         // coins
    val xpReward: Int,
    val penaltyCoins: Int    // coins lost on defeat
)

private val BOSSES = listOf(
    BossData("The Procrastinator", "🐉", "A dragon that feeds on your wasted hours. Slay it by completing all quests this week.", 100, 500, 300, 150),
    BossData("Shadow of Distraction", "👁", "An eye that watches you scroll. Close it by hitting your screen time limit every day.", 120, 600, 350, 180),
    BossData("The Sloth King", "🦥", "Rules the kingdom of laziness. Defeat him by maintaining your streak all 7 days.", 80, 400, 250, 100),
    BossData("Exam Demon", "💀", "Emerges before every test. Beat him by completing all study quests this week.", 150, 700, 400, 200),
    BossData("The Midnight Owl", "🦉", "Drains your sleep. Defeat by finishing all quests before 9 PM every day.", 90, 450, 280, 120),
)

@HiltViewModel
class BossBattleViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _boss = MutableStateFlow<BossData?>(null)
    val boss = _boss.asStateFlow()

    private val _bossHp = MutableStateFlow(100)
    val bossHp = _bossHp.asStateFlow()

    private val _questsCompleted = MutableStateFlow(0)
    val questsCompleted = _questsCompleted.asStateFlow()

    private val _questsTotal = MutableStateFlow(0)
    val questsTotal = _questsTotal.asStateFlow()

    private val _alreadyDefeated = MutableStateFlow(false)
    val alreadyDefeated = _alreadyDefeated.asStateFlow()

    private val _victory = MutableStateFlow(false)
    val victory = _victory.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val u = userRepository.userInfo
        val thisWeek = getIsoWeek()

        // Pick a boss deterministically by week number
        val weekNum = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val boss = BOSSES[weekNum % BOSSES.size]
        _boss.value = boss
        _bossHp.value = boss.hpMax

        _alreadyDefeated.value = u.lastBossBattleWeek == thisWeek && u.bossDefeatedCount > 0

        val today = getCurrentDate()
        val allQuests = questRepository.getAllQuests().first()
        val todayQuests = allQuests.filter { !it.is_destroyed }
        val completedToday = todayQuests.count { it.last_completed_on == today }

        _questsTotal.value = todayQuests.size
        _questsCompleted.value = completedToday

        // HP decreases as you complete quests: each quest removes hpMax/total HP
        val dmgPerQuest = if (todayQuests.isNotEmpty()) boss.hpMax / todayQuests.size else 0
        val currentHp = (boss.hpMax - (completedToday * dmgPerQuest)).coerceAtLeast(0)
        _bossHp.value = currentHp

        if (currentHp == 0 && u.lastBossBattleWeek != thisWeek) {
            _victory.value = true
        }
    }

    fun claimVictory() {
        val boss = _boss.value ?: return
        val u = userRepository.userInfo
        u.lastBossBattleWeek = getIsoWeek()
        u.bossDefeatedCount++
        userRepository.addCoins(boss.reward, "Boss Battle victory — ${boss.name}")
        userRepository.addXp(boss.xpReward)
        userRepository.saveUserInfo()
        _victory.value = false
        _alreadyDefeated.value = true
    }

    private fun getIsoWeek(): String {
        val date = LocalDate.now()
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        return "$year-W$week"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossBattleScreen(navController: NavController, vm: BossBattleViewModel = hiltViewModel()) {
    val boss by vm.boss.collectAsState()
    val hp by vm.bossHp.collectAsState()
    val qDone by vm.questsCompleted.collectAsState()
    val qTotal by vm.questsTotal.collectAsState()
    val defeated by vm.alreadyDefeated.collectAsState()
    val victory by vm.victory.collectAsState()

    val hpFraction = if (boss != null) hp.toFloat() / boss!!.hpMax else 1f
    val hpColor = when {
        hpFraction > 0.6f -> Color(0xFF4CAF50)
        hpFraction > 0.3f -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }

    // Rotating boss animation
    val rotation = rememberInfiniteTransition(label = "boss")
    val angle by rotation.animateFloat(
        initialValue = -5f, targetValue = 5f, label = "swing",
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse)
    )

    if (victory) {
        VictoryDialog(boss = boss!!, onClaim = { vm.claimVictory() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Boss", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        boss?.let { b ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Boss card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Canvas(Modifier.size(100.dp)) {
                                rotate(angle, pivot = center) {
                                    drawCircle(Color(0x22FF5722), radius = size.minDimension / 2)
                                    // glow lines
                                    repeat(8) { i ->
                                        val a = i * 45f
                                        val r = size.minDimension / 2
                                        drawLine(
                                            Color(0x44FF5722),
                                            Offset(center.x, center.y),
                                            Offset(
                                                center.x + r * kotlin.math.cos(Math.toRadians(a.toDouble())).toFloat(),
                                                center.y + r * kotlin.math.sin(Math.toRadians(a.toDouble())).toFloat()
                                            ),
                                            strokeWidth = 3f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            Text(b.emoji, fontSize = 56.sp)
                            Text(b.name, fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center)
                            Text(b.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)

                            Spacer(Modifier.height(4.dp))

                            // HP bar
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("BOSS HP", fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp, color = hpColor)
                                    Text("$hp / ${b.hpMax}", fontSize = 12.sp, color = hpColor)
                                }
                                LinearProgressIndicator(
                                    progress = { hpFraction },
                                    modifier = Modifier.fillMaxWidth().height(12.dp),
                                    color = hpColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                item {
                    // Progress card
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Today's Damage", fontWeight = FontWeight.SemiBold)
                            Text(
                                "$qDone / $qTotal quests completed → ${b.hpMax - hp} damage dealt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Complete all quests every day this week to slay the boss.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    // Rewards card
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A2A1A)
                        )
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Victory Rewards", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text("🪙 ${b.reward} coins", color = Color.White, fontSize = 14.sp)
                            Text("⭐ ${b.xpReward} XP", color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Penalty if failed: −${b.penaltyCoins} coins",
                                color = Color(0xFFE57373), fontSize = 12.sp)
                        }
                    }
                }

                if (defeated) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "✅ Boss defeated this week! Come back next week for a new challenge.",
                                Modifier.padding(16.dp), textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VictoryDialog(boss: BossData, onClaim: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("⚔️ Boss Defeated!", fontWeight = FontWeight.Black, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(boss.emoji, fontSize = 48.sp)
                Text("You slayed ${boss.name}!", textAlign = TextAlign.Center)
                Text("Rewards: +${boss.reward} 🪙  +${boss.xpReward} ⭐",
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = onClaim) { Text("Claim Rewards") }
        }
    )
}
