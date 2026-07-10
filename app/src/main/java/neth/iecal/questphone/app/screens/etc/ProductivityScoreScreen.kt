package neth.iecal.questphone.app.screens.etc

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.utils.UsageStatsHelper
import nethical.questphone.core.core.utils.getCurrentDate
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ScoreBreakdown(
    val questScore: Int,       // 0-40: based on quest completion %
    val studyScore: Int,       // 0-30: based on study app time vs quota
    val screenTimeScore: Int,  // 0-20: lower screen time = higher score
    val streakScore: Int,      // 0-10: based on current streak
    val total: Int,
    val grade: String,
    val commentary: String
)

@HiltViewModel
class ProductivityScoreViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _score = MutableStateFlow<ScoreBreakdown?>(null)
    val score = _score.asStateFlow()

    fun load(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val u = userRepository.userInfo
            val today = getCurrentDate()
            val allQuests = questRepository.getAllQuests().first()
            val active = allQuests.filter { !it.is_destroyed }
            val completed = active.count { it.last_completed_on == today }
            val total = active.size

            // Quest score: 0-40
            val questPct = if (total > 0) completed.toFloat() / total else 0f
            val questScore = (questPct * 40).toInt()

            // Study score: 0-30
            val helper = UsageStatsHelper(context)
            val stats = helper.getForegroundStatsByDay(LocalDate.now())
            val studyMs = stats.filter { it.packageName in u.studyApps }.sumOf { it.totalTime }
            val quotaMs = (u.dailyStudyQuotaHours * 3_600_000).toLong()
            val studyPct = if (quotaMs > 0) (studyMs.toFloat() / quotaMs).coerceIn(0f, 1f) else 0f
            val studyScore = (studyPct * 30).toInt()

            // Screen time score: 0-20 (target: < 3h total = full score)
            val totalMs = stats.sumOf { it.totalTime }
            val targetMs = TimeUnit.HOURS.toMillis(3)
            val screenFraction = (1f - (totalMs.toFloat() / (targetMs * 2))).coerceIn(0f, 1f)
            val screenScore = (screenFraction * 20).toInt()

            // Streak score: 0-10
            val streakScore = minOf(u.streak.currentStreak, 10)

            val totalScore = questScore + studyScore + screenScore + streakScore
            val grade = when {
                totalScore >= 90 -> "S"
                totalScore >= 80 -> "A"
                totalScore >= 70 -> "B"
                totalScore >= 60 -> "C"
                totalScore >= 50 -> "D"
                else -> "F"
            }
            val commentary = when (grade) {
                "S" -> "Legendary. You're in the top 1% today. 🏆"
                "A" -> "Excellent performance. Keep this momentum. ⚡"
                "B" -> "Solid day. A bit more focus and you hit S-rank."
                "C" -> "Average. You know you can do better."
                "D" -> "Struggling today. One quest at a time."
                else -> "Today is not your day. But tomorrow can be. Start now."
            }

            // Save to UserInfo
            u.lastProductivityScore = totalScore
            u.lastProductivityDate = today
            userRepository.saveUserInfo()

            _score.value = ScoreBreakdown(questScore, studyScore, screenScore, streakScore, totalScore, grade, commentary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductivityScoreScreen(navController: NavController, vm: ProductivityScoreViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val score by vm.score.collectAsState()

    LaunchedEffect(Unit) { vm.load(context) }

    val gradeColor = when (score?.grade) {
        "S" -> Color(0xFFFFD700)
        "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF2196F3)
        "C" -> Color(0xFFFF9800)
        "D" -> Color(0xFFFF5722)
        else -> Color(0xFFE53935)
    }

    // Animate score from 0 to actual
    val animScore by animateIntAsState(
        targetValue = score?.total ?: 0,
        animationSpec = tween(1200, easing = EaseOutCubic),
        label = "score"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productivity Score", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (score == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }
            val s = score!!

            item {
                // Big score ring
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stroke = 18f
                                val sweep = (animScore / 100f) * 270f
                                // Track
                                drawArc(Color(0xFF333333), startAngle = 135f, sweepAngle = 270f,
                                    useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
                                // Fill
                                drawArc(gradeColor, startAngle = 135f, sweepAngle = sweep,
                                    useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$animScore", fontSize = 40.sp, fontWeight = FontWeight.Black, color = gradeColor)
                                Text("/100", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "Grade ${s.grade}",
                            fontSize = 28.sp, fontWeight = FontWeight.Black, color = gradeColor
                        )
                        Text(s.commentary, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                // Breakdown
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Score Breakdown", fontWeight = FontWeight.Bold)
                        ScoreBar("⚔️ Quests", s.questScore, 40, Color(0xFF2196F3))
                        ScoreBar("📚 Study Time", s.studyScore, 30, Color(0xFF4CAF50))
                        ScoreBar("📱 Screen Time", s.screenTimeScore, 20, Color(0xFFFF9800))
                        ScoreBar("🔥 Streak Bonus", s.streakScore, 10, Color(0xFFFFD700))
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("How it's calculated", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        listOf(
                            "⚔️ Quests (40pts) — completion % × 40",
                            "📚 Study (30pts) — study time vs daily quota",
                            "📱 Screen (20pts) — lower total screen time = more points",
                            "🔥 Streak (10pts) — 1pt per streak day, max 10"
                        ).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, value: Int, max: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$value / $max", style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, color = color)
        }
        LinearProgressIndicator(
            progress = { value.toFloat() / max },
            Modifier.fillMaxWidth().height(6.dp),
            color = color,
            strokeCap = StrokeCap.Round
        )
    }
}
