package neth.iecal.questphone.app.screens.etc

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
import androidx.compose.ui.graphics.Color
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
import neth.iecal.questphone.core.ai.ChatMessage
import neth.iecal.questphone.core.ai.GemmaRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeeklyReportData(
    val questsCompleted: Int,
    val questsTotal: Int,
    val completionPct: Int,
    val bestDay: String,
    val worstDay: String,
    val statGains: Map<String, Int>,
    val coinsEarned: Int,
    val grade: String,
    val kaiCommentary: String
)

@HiltViewModel
class WeeklyReportViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository,
    val gemmaRepository: GemmaRepository
) : ViewModel() {

    private val _report = MutableStateFlow<WeeklyReportData?>(null)
    val report = _report.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init { viewModelScope.launch { generate() } }

    private suspend fun generate() {
        _isLoading.value = true
        val u = userRepository.userInfo
        val sp = u.statPoints
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val allQuests = questRepository.getAllQuests().first()
        val active = allQuests.filter { !it.is_destroyed }

        // Count completions per day this week
        val dayCompletions = (0..6).associate { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val label = date.dayOfWeek.name.take(3)
            val count = active.count { it.last_completed_on == date.format(fmt) }
            label to count
        }

        val totalCompleted = dayCompletions.values.sum()
        val totalPossible = active.size * 7
        val pct = if (totalPossible > 0) (totalCompleted * 100 / totalPossible) else 0

        val bestDay = dayCompletions.maxByOrNull { it.value }?.key ?: "—"
        val worstDay = dayCompletions.minByOrNull { it.value }?.key ?: "—"

        val grade = when {
            pct >= 90 -> "S"; pct >= 80 -> "A"; pct >= 65 -> "B"
            pct >= 50 -> "C"; pct >= 35 -> "D"; else -> "F"
        }

        // Generate Kai commentary based on personality
        val prompt = buildString {
            appendLine("Write a 3-sentence weekly productivity report for a student.")
            appendLine("Quests: $totalCompleted/$totalPossible completed ($pct%).")
            appendLine("Best day: $bestDay. Worst: $worstDay. Grade: $grade.")
            appendLine("Streak: ${u.streak.currentStreak} days.")
            appendLine("Personality: ${u.kaiPersonality}. Be in character.")
        }
        val commentary = try {
            gemmaRepository.chat(emptyList(), prompt).getOrNull()?.text ?: defaultCommentary(grade)
        } catch (_: Exception) { defaultCommentary(grade) }

        _report.value = WeeklyReportData(
            questsCompleted = totalCompleted,
            questsTotal = totalPossible,
            completionPct = pct,
            bestDay = bestDay,
            worstDay = worstDay,
            statGains = mapOf(
                sp.name1 to sp.value1,
                sp.name2 to sp.value2,
                sp.name3 to sp.value3,
                sp.name4 to sp.value4
            ),
            coinsEarned = u.coins,
            grade = grade,
            kaiCommentary = commentary
        )
        _isLoading.value = false
    }

    private fun defaultCommentary(grade: String) = when (grade) {
        "S", "A" -> "Outstanding week. You're building momentum that compounds every day."
        "B", "C" -> "Decent week. Identify which days you fell short and fix that pattern."
        else -> "Tough week. Reset, refocus. Every champion has off weeks — what matters is the response."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(navController: NavController, vm: WeeklyReportViewModel = hiltViewModel()) {
    val report by vm.report.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    val gradeColor = when (report?.grade) {
        "S" -> Color(0xFFFFD700); "A" -> Color(0xFF4CAF50); "B" -> Color(0xFF2196F3)
        "C" -> Color(0xFFFF9800); "D" -> Color(0xFFFF5722); else -> Color(0xFFE53935)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Kai is reviewing your week…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }
        report?.let { r ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Grade card
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = gradeColor.copy(alpha = 0.1f))
                    ) {
                        Column(
                            Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("This Week's Grade", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.grade, fontSize = 64.sp, fontWeight = FontWeight.Black, color = gradeColor)
                            Text("${r.completionPct}% completion  •  ${r.questsCompleted}/${r.questsTotal} quests",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    // Kai commentary
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Kai says:", fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary)
                            Text("\"${r.kaiCommentary}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                }
                item {
                    // Best/Worst day
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                        DayCard("🏆 Best Day", r.bestDay, Color(0xFF4CAF50), Modifier.weight(1f))
                        DayCard("⚠ Worst Day", r.worstDay, Color(0xFFE53935), Modifier.weight(1f))
                    }
                }
                item {
                    // Stat totals
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Current Stats", fontWeight = FontWeight.SemiBold)
                            r.statGains.forEach { (name, value) ->
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text(name, style = MaterialTheme.typography.bodySmall)
                                    Text("$value pts", fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DayCard(label: String, day: String, color: Color, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(day, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
        }
    }
}
