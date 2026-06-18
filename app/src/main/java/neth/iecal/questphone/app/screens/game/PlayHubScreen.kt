package neth.iecal.questphone.app.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.core.core.utils.getCurrentDate
import java.time.LocalDate
import java.time.temporal.IsoFields
import javax.inject.Inject

data class PlayHubState(
    val bossName: String = "",
    val bossEmoji: String = "",
    val bossHpFraction: Float = 1f,
    val bossDefeated: Boolean = false,
    val rivalName: String = "",
    val yourQuestsToday: Int = 0,
    val rivalQuestsToday: Int = 0,
    val totalQuests: Int = 0,
    val isWinningRival: Boolean = false,
    val productivityScore: Int = 0,
    val productivityGrade: String = "?",
    val activeChain: String = "",
    val activeChainStep: Int = 0,
    val activeChainTotal: Int = 0,
    val weeklyGrade: String = "?"
)

@HiltViewModel
class PlayHubViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlayHubState())
    val state = _state.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val u = userRepository.userInfo
        val today = getCurrentDate()
        val allQuests = questRepository.getAllQuests().first()
        val active = allQuests.filter { !it.is_destroyed }
        val total = active.size
        val yourDone = active.count { it.last_completed_on == today }

        // Boss
        val weekNum = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val bossList = listOf(
            "The Procrastinator" to "🐉", "Shadow of Distraction" to "👁",
            "The Sloth King" to "🦥", "Exam Demon" to "💀", "The Midnight Owl" to "🦉"
        )
        val boss = bossList[weekNum % bossList.size]
        val bossHpMax = 100
        val dmgPerQuest = if (total > 0) bossHpMax / total else 0
        val bossHp = (bossHpMax - yourDone * dmgPerQuest).coerceAtLeast(0)
        val bossDefeated = u.lastBossBattleWeek == run {
            val year = LocalDate.now().get(IsoFields.WEEK_BASED_YEAR)
            "$year-W$weekNum"
        }

        // Rival
        val seed = today.hashCode().toLong()
        val rivalDone = ((total * (0.6f + (seed % 100) / 333f)).toInt()).coerceIn(0, total)

        // Score (quick calculation)
        val questPct = if (total > 0) yourDone.toFloat() / total else 0f
        val score = (questPct * 40 + minOf(u.streak.currentStreak, 10)).toInt()
        val grade = when {
            score >= 90 -> "S"; score >= 80 -> "A"; score >= 65 -> "B"
            score >= 50 -> "C"; score >= 35 -> "D"; else -> "F"
        }

        // Active chain
        val chainId = u.activeQuestChainIds.firstOrNull() ?: ""
        val chainTemplates = listOf(
            Triple("morning_warrior", "Morning Warrior", 5),
            Triple("deep_focus", "Deep Focus Initiate", 5),
            Triple("strength_arc", "Strength Arc", 5)
        )
        val chain = chainTemplates.find { it.first == chainId }
        val chainStep = if (chain != null) {
            active.count { q -> q.title.isNotBlank() && q.last_completed_on != "0001-01-01" }
                .coerceIn(0, chain.third)
        } else 0

        _state.value = PlayHubState(
            bossName = boss.first, bossEmoji = boss.second,
            bossHpFraction = bossHp.toFloat() / bossHpMax,
            bossDefeated = bossDefeated,
            rivalName = u.rivalName,
            yourQuestsToday = yourDone, rivalQuestsToday = rivalDone, totalQuests = total,
            isWinningRival = yourDone >= rivalDone,
            productivityScore = score, productivityGrade = grade,
            activeChain = chain?.second ?: "",
            activeChainStep = chainStep,
            activeChainTotal = chain?.third ?: 0,
            weeklyGrade = grade // reuse for now
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayHubScreen(navController: NavController, vm: PlayHubViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎮 Play", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Boss Battle
            item {
                PlayCard(
                    onClick = { navController.navigate(RootRoute.BossBattle.route) },
                    color = Color(0xFF1A0A00)
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("⚔️ Weekly Boss", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text(s.bossName, color = Color(0xFFFF7043), fontWeight = FontWeight.SemiBold)
                            if (s.bossDefeated) {
                                Text("✅ Defeated this week", color = Color(0xFF4CAF50), fontSize = 11.sp)
                            } else {
                                LinearProgressIndicator(
                                    progress = { s.bossHpFraction },
                                    Modifier.fillMaxWidth().height(5.dp),
                                    color = Color(0xFFE53935),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Text("${(s.bossHpFraction * 100).toInt()}% HP remaining", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        Text(s.bossEmoji, fontSize = 36.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            // Rival
            item {
                PlayCard(
                    onClick = { navController.navigate(RootRoute.RivalScreen.route) },
                    color = if (s.isWinningRival) Color(0xFF0A1A0A) else Color(0xFF1A0A0A)
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("👤 Rival: ${s.rivalName}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${s.yourQuestsToday}", fontSize = 20.sp, fontWeight = FontWeight.Black,
                                        color = if (s.isWinningRival) Color(0xFF4CAF50) else Color.White)
                                    Text("You", color = Color.Gray, fontSize = 10.sp)
                                }
                                Text("vs", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterVertically))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${s.rivalQuestsToday}", fontSize = 20.sp, fontWeight = FontWeight.Black,
                                        color = if (!s.isWinningRival) Color(0xFFE53935) else Color.White)
                                    Text("Rival", color = Color.Gray, fontSize = 10.sp)
                                }
                                Text("/ ${s.totalQuests} quests today", color = Color.Gray, fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                    }
                }
            }

            // Productivity Score + Weekly Report side by side
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayCard(
                        onClick = { navController.navigate(RootRoute.ProductivityScore.route) },
                        color = Color(0xFF0A0A1A), modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("📊", fontSize = 24.sp)
                            Text("${s.productivityScore}", fontSize = 28.sp, fontWeight = FontWeight.Black,
                                color = gradeColor(s.productivityGrade))
                            Text("Today's Score", color = Color.Gray, fontSize = 10.sp)
                            Text("Grade ${s.productivityGrade}", fontWeight = FontWeight.Bold,
                                color = gradeColor(s.productivityGrade), fontSize = 13.sp)
                        }
                    }
                    PlayCard(
                        onClick = { navController.navigate(RootRoute.WeeklyReport.route) },
                        color = Color(0xFF0A1A1A), modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("📋", fontSize = 24.sp)
                            Text(s.weeklyGrade, fontSize = 28.sp, fontWeight = FontWeight.Black,
                                color = gradeColor(s.weeklyGrade))
                            Text("Weekly Grade", color = Color.Gray, fontSize = 10.sp)
                            Text("Tap for report", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Quest Chains
            item {
                PlayCard(
                    onClick = { navController.navigate(RootRoute.QuestChains.route) },
                    color = Color(0xFF0D0A1A)
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🔗 Quest Chains", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            if (s.activeChain.isNotBlank()) {
                                Text(s.activeChain, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                LinearProgressIndicator(
                                    progress = { if (s.activeChainTotal > 0) s.activeChainStep.toFloat() / s.activeChainTotal else 0f },
                                    Modifier.fillMaxWidth(0.7f).height(5.dp),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Text("Step ${s.activeChainStep} / ${s.activeChainTotal}", color = Color.Gray, fontSize = 11.sp)
                            } else {
                                Text("No active chain — start one", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Text("→", color = Color.Gray, fontSize = 20.sp)
                    }
                }
            }

            // Store + Inventory row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayCard(
                        onClick = { navController.navigate(RootRoute.Store.route) },
                        color = Color(0xFF1A1200), modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("🛒", fontSize = 24.sp)
                            Text("Store", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Buy themes & items", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                    PlayCard(
                        onClick = { navController.navigate(RootRoute.QuestChains.route) },
                        color = Color(0xFF121A00), modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("🔗", fontSize = 24.sp)
                            Text("Chains", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Quest chain progress", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun gradeColor(grade: String) = when (grade) {
    "S" -> Color(0xFFFFD700); "A" -> Color(0xFF4CAF50); "B" -> Color(0xFF2196F3)
    "C" -> Color(0xFFFF9800); "D" -> Color(0xFFFF5722); else -> Color(0xFFE53935)
}

@Composable
private fun PlayCard(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}
