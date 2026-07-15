package neth.iecal.questphone.app.screens.etc

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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

            // Study score: 0-30 — combines screen time + class completions
            val helper = UsageStatsHelper(context)
            val stats = helper.getForegroundStatsByDay(LocalDate.now())
            val studyMs = stats.filter { it.packageName in u.studyApps }.sumOf { it.totalTime }
            val classHours = u.studyDailyLogHours[today] ?: 0f
            val screenStudyHours = studyMs / 3_600_000f
            val totalStudyHours = classHours + screenStudyHours
            val quotaHours = u.dailyStudyQuotaHours
            val studyPct = if (quotaHours > 0) (totalStudyHours / quotaHours).coerceIn(0f, 1f) else 0f
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
                title = {
                    Column {
                        Text("Productivity Score", fontWeight = FontWeight.Black,
                            fontSize = 18.sp, color = Color.White)
                        Text("Today's performance", fontSize = 11.sp, color = Color(0xFF666666))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                windowInsets = WindowInsets(0)
            )
        },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0)
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
                // Big score ring — dark RPG card
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0A0A1E))
                        .border(1.dp, gradeColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        Modifier.padding(28.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val stroke = 20f
                                val sweep = (animScore / 100f) * 270f
                                drawArc(Color(0xFF1A1A2E), startAngle = 135f, sweepAngle = 270f,
                                    useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
                                drawArc(gradeColor, startAngle = 135f, sweepAngle = sweep,
                                    useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$animScore", fontSize = 48.sp,
                                    fontWeight = FontWeight.Black, color = gradeColor)
                                Text("/100", fontSize = 14.sp, color = Color(0xFF555555))
                            }
                        }
                        Text("Grade ${s.grade}", fontSize = 32.sp,
                            fontWeight = FontWeight.Black, color = gradeColor)
                        Text(s.commentary, textAlign = TextAlign.Center,
                            fontSize = 13.sp, color = Color(0xFF888888))
                    }
                }
            }

            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D0D1A))
                        .border(1.dp, Color(0xFF222244), RoundedCornerShape(16.dp))
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Score Breakdown", fontWeight = FontWeight.Bold,
                            color = Color.White, fontSize = 15.sp)
                        ScoreBar("⚔️ Quests", s.questScore, 40, Color(0xFF2196F3))
                        ScoreBar("📚 Study Time", s.studyScore, 30, Color(0xFF4CAF50))
                        ScoreBar("📱 Screen Time", s.screenTimeScore, 20, Color(0xFFFF9800))
                        ScoreBar("🔥 Streak Bonus", s.streakScore, 10, Color(0xFFFFD700))
                    }
                }
            }

            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How it's calculated", fontWeight = FontWeight.SemiBold,
                            color = Color.White, fontSize = 13.sp)
                        listOf(
                            "⚔️ Quests (40pts) — completion % × 40",
                            "📚 Study (30pts) — study time vs daily quota",
                            "📱 Screen (20pts) — lower total screen time = more points",
                            "🔥 Streak (10pts) — 1pt per streak day, max 10"
                        ).forEach {
                            Text(it, fontSize = 12.sp, color = Color(0xFF666666))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, value: Int, max: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = Color(0xFFCCCCCC))
            Text("$value / $max", fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = color)
        }
        LinearProgressIndicator(
            progress = { value.toFloat() / max },
            Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = Color(0xFF1A1A2A),
            strokeCap = StrokeCap.Round
        )
    }
}
