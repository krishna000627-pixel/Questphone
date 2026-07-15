package neth.iecal.questphone.app.screens.game

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.core.core.utils.getCurrentDate
import nethical.questphone.data.MockTestScore
import nethical.questphone.data.StudyClass
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ── Data ──────────────────────────────────────────────────────────────────────

data class AscensionState(
    val battlePower: Int = 0,
    val grade: String = "?",
    val questScore: Int = 0,
    val studyScore: Int = 0,
    val screenScore: Int = 0,
    val streakScore: Int = 0,
    val bossName: String = "",
    val bossEmoji: String = "",
    val bossHpFraction: Float = 1f,
    val bossDefeated: Boolean = false,
    val rivalName: String = "",
    val yourQuestsToday: Int = 0,
    val rivalQuestsToday: Int = 0,
    val totalQuests: Int = 0,
    val activeChain: String = "",
    val activeChainStep: Int = 0,
    val activeChainTotal: Int = 0,
    val weeklyGrade: String = "?",
    val studyHoursToday: Float = 0f,
    val studyGoalHours: Float = 4f,
    val studyStreak: Int = 0,
    val daysToExam: Long? = null,
    val targetExam: String = "",
    val lastMockPct: Int = 0,
    val lastMockName: String = "",
    val todayClasses: List<StudyClass> = emptyList(),
    val leaderboard: List<LeaderboardEntry> = emptyList()
)

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val score: Int,
    val grade: String,
    val studyHours: Float,
    val isYou: Boolean = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AscensionHallViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AscensionState())
    val state = _state.asStateFlow()

    fun load(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val u = userRepository.userInfo
            val today = getCurrentDate()
            val allQuests = questRepository.getAllQuests().first()
            val active = allQuests.filter { !it.is_destroyed }
            val total = active.size
            val yourDone = active.count { it.last_completed_on == today }

            // ── Productivity Score ────────────────────────────────────────────
            val questPct = if (total > 0) yourDone.toFloat() / total else 0f
            val questScore = (questPct * 40).toInt()

            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val statsMap = usm?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startOfDay, System.currentTimeMillis()
            ) ?: emptyList()
            val studyAppMs = statsMap.filter { it.packageName in u.studyApps }
                .sumOf { it.totalTimeInForeground }
            val classHours = u.studyDailyLogHours[today] ?: 0f
            val screenStudyHours = studyAppMs / 3_600_000f
            val totalStudyHours = classHours + screenStudyHours
            val studyPct = if (u.dailyStudyQuotaHours > 0)
                (totalStudyHours / u.dailyStudyQuotaHours).coerceIn(0f, 1f) else 0f
            val studyScore = (studyPct * 30).toInt()

            val totalMs = statsMap.sumOf { it.totalTimeInForeground }
            val targetMs = TimeUnit.HOURS.toMillis(3)
            val screenFraction = (1f - totalMs.toFloat() / (targetMs * 2)).coerceIn(0f, 1f)
            val screenScore = (screenFraction * 20).toInt()

            val streakScore = minOf(u.streak.currentStreak, 10)
            val battlePower = questScore + studyScore + screenScore + streakScore

            val grade = when {
                battlePower >= 90 -> "S"; battlePower >= 80 -> "A"
                battlePower >= 70 -> "B"; battlePower >= 60 -> "C"
                battlePower >= 50 -> "D"; else -> "F"
            }

            // ── Boss ─────────────────────────────────────────────────────────
            val weekNum = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val bossList = listOf(
                "The Procrastinator" to "🐉", "Shadow of Distraction" to "👁",
                "The Sloth King" to "🦥", "Exam Demon" to "💀", "The Midnight Owl" to "🦉"
            )
            val boss = bossList[weekNum % bossList.size]
            val bossHpMax = 100
            val dmgPerQuest = if (total > 0) bossHpMax / total else 0
            val bossHp = (bossHpMax - yourDone * dmgPerQuest).coerceAtLeast(0)
            val bossDefeated = u.lastBossBattleWeek == "${LocalDate.now().get(IsoFields.WEEK_BASED_YEAR)}-W$weekNum"

            // ── Rival ─────────────────────────────────────────────────────────
            val seed = today.hashCode().toLong()
            val rivalDone = ((total * (0.6f + (seed % 100) / 333f)).toInt()).coerceIn(0, total)

            // ── Quest chains ──────────────────────────────────────────────────
            val chainId = u.activeQuestChainIds.firstOrNull() ?: ""
            val chainTemplates = listOf(
                Triple("morning_warrior", "Morning Warrior", 5),
                Triple("deep_focus", "Deep Focus Initiate", 5),
                Triple("strength_arc", "Strength Arc", 5)
            )
            val chain = chainTemplates.find { it.first == chainId }
            val chainStep = if (chain != null)
                active.count { it.title.isNotBlank() && it.last_completed_on != "0001-01-01" }
                    .coerceIn(0, chain.third) else 0

            // ── Study ─────────────────────────────────────────────────────────
            val daysToExam = runCatching {
                ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(u.studyTargetDate))
            }.getOrNull()
            val todayClasses = u.studyClasses.filter {
                LocalDate.now().dayOfWeek.value in it.daysOfWeek
            }
            val lastMock = u.studyMockScores.firstOrNull()
            val lastMockPct = lastMock?.let { it.obtainedMarks * 100 / it.totalMarks.coerceAtLeast(1) } ?: 0

            // ── Fake Leaderboard ──────────────────────────────────────────────
            val myEntry = LeaderboardEntry(0, u.username.ifBlank { "You" }, battlePower, grade, totalStudyHours, true)
            val aiStudents = generateLeaderboard(today, battlePower, totalStudyHours, u.studyStreak)
            val allEntries = (aiStudents + myEntry).sortedByDescending { it.score }
                .mapIndexed { i, e -> e.copy(rank = i + 1) }

            _state.value = AscensionState(
                battlePower = battlePower, grade = grade,
                questScore = questScore, studyScore = studyScore,
                screenScore = screenScore, streakScore = streakScore,
                bossName = boss.first, bossEmoji = boss.second,
                bossHpFraction = bossHp.toFloat() / bossHpMax, bossDefeated = bossDefeated,
                rivalName = u.rivalName, yourQuestsToday = yourDone,
                rivalQuestsToday = rivalDone, totalQuests = total,
                activeChain = chain?.second ?: "", activeChainStep = chainStep,
                activeChainTotal = chain?.third ?: 0, weeklyGrade = grade,
                studyHoursToday = totalStudyHours, studyGoalHours = u.dailyStudyQuotaHours,
                studyStreak = u.studyStreak, daysToExam = daysToExam,
                targetExam = u.studyTargetExam,
                lastMockPct = lastMockPct, lastMockName = lastMock?.examName ?: "",
                todayClasses = todayClasses, leaderboard = allEntries
            )

            // Save score
            u.lastProductivityScore = battlePower
            u.lastProductivityDate = today
            userRepository.saveUserInfo()
        }
    }

    fun markClassComplete(cls: StudyClass) {
        val today = LocalDate.now().toString()
        val info = userRepository.userInfo
        val label = cls.subject.ifBlank { cls.name }
        info.studyDailyLogHours[today] = (info.studyDailyLogHours[today] ?: 0f) + cls.durationHours
        info.studySubjectHours[label] = (info.studySubjectHours[label] ?: 0f) + cls.durationHours
        val key = "cls_done_${cls.id}_$today"
        info.studyDailyLogHours[key] = 1f
        val yesterday = LocalDate.now().minusDays(1).toString()
        info.studyStreak = when (info.studyLastLogDate) {
            today -> info.studyStreak; yesterday -> info.studyStreak + 1; else -> 1
        }
        info.studyLastLogDate = today
        userRepository.addCoins((cls.durationHours * 5).toInt().coerceAtLeast(1), "Class: ${cls.name}")
        userRepository.addXp((cls.durationHours * 10).toInt())
        userRepository.saveUserInfo()
        _needsReload.value = true
    }

    internal val _needsReload = MutableStateFlow(false)

    fun isClassDoneToday(cls: StudyClass): Boolean {
        val today = LocalDate.now().toString()
        return userRepository.userInfo.studyDailyLogHours.containsKey("cls_done_${cls.id}_$today")
    }

    fun addMockScore(score: MockTestScore) {
        userRepository.userInfo.studyMockScores.add(0, score)
        userRepository.saveUserInfo()
        _needsReload.value = true
    }

    fun removeMockScore(id: String) {
        userRepository.userInfo.studyMockScores.removeAll { it.id == id }
        userRepository.saveUserInfo()
        _needsReload.value = true
    }

    fun addClass(cls: StudyClass) {
        userRepository.userInfo.studyClasses.add(cls)
        userRepository.saveUserInfo()
        _needsReload.value = true
    }

    fun removeClass(id: String) {
        userRepository.userInfo.studyClasses.removeAll { it.id == id }
        userRepository.saveUserInfo()
        _needsReload.value = true
    }

    fun consumeReload() { _needsReload.value = false }

    fun estimateRank(exam: String, pct: Float): String = when (exam.uppercase()) {
        "JEE" -> when {
            pct >= 99f -> "Top 500 — IIT Bombay/Delhi 🏆"
            pct >= 97f -> "Top 2,000 — Good IIT 🎯"
            pct >= 93f -> "Top 10,000 — NIT+ 📈"
            pct >= 85f -> "Top 30,000 — NIT 📊"
            else -> "Below 30,000 — keep pushing 💪"
        }
        "NEET" -> when {
            pct >= 99f -> "Top 100 — AIIMS Delhi 🏆"
            pct >= 95f -> "Top 1,000 — AIIMS 🎯"
            pct >= 88f -> "Top 10,000 — Govt Medical 📈"
            else -> "Focus on NCERT 💪"
        }
        "UPSC" -> when {
            pct >= 75f -> "High chance Mains 🏆"
            pct >= 60f -> "Prelims likely 📈"
            else -> "Focus on basics 💪"
        }
        else -> if (pct >= 80f) "Looking strong! 🎯" else "Needs improvement 💪"
    }
}

private fun generateLeaderboard(today: String, myScore: Int, myStudyH: Float, myStreak: Int): List<LeaderboardEntry> {
    val names = listOf("Arjun S.", "Priya M.", "Rohan K.", "Ananya T.", "Dev P.",
        "Ishaan R.", "Sneha G.", "Karthik N.", "Meera V.", "Aditya L.",
        "Pooja S.", "Rahul B.", "Kavya M.", "Vikram R.", "Nisha P.")
    val seed = today.hashCode()
    return names.mapIndexed { i, name ->
        val rng = (seed + i * 7919).hashCode()
        val score = (35 + Math.abs(rng % 65)).coerceIn(0, 100)
        val studyH = (1f + Math.abs((rng / 100) % 80) / 10f).coerceIn(0f, 12f)
        val grade = when {
            score >= 90 -> "S"; score >= 80 -> "A"; score >= 70 -> "B"
            score >= 60 -> "C"; score >= 50 -> "D"; else -> "F"
        }
        LeaderboardEntry(0, name, score, grade, studyH)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AscensionHallScreen(
    navController: NavController,
    vm: AscensionHallViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val s by vm.state.collectAsState()
    val needsReload by vm._needsReload.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val tabs = listOf("⚔️ Realm", "📚 Study", "🏆 Arena", "📊 Stats")

    LaunchedEffect(Unit) { vm.load(ctx) }
    LaunchedEffect(needsReload) { if (needsReload) { vm.load(ctx); vm.consumeReload() } }

    if (showSettings) {
        AscensionSettingsScreen(vm, onBack = { showSettings = false; vm._needsReload.value = true })
        return
    }

    Scaffold(
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text("Ascension Hall", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                            if (s.targetExam.isNotBlank())
                                Text(s.targetExam + if (s.daysToExam != null) " · ${s.daysToExam}d left" else "",
                                    fontSize = 11.sp, color = Color(0xFF5C6BC0))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Battle power badge
                        Box(Modifier.clip(RoundedCornerShape(20.dp))
                            .background(ahGradeColor(s.grade).copy(alpha = 0.15f))
                            .border(1.dp, ahGradeColor(s.grade).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("${s.grade} · ${s.battlePower}", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = ahGradeColor(s.grade))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Settings", tint = Color.Gray)
                        }
                    }
                }
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent,
                    contentColor = Color.White) {
                    tabs.forEachIndexed { i, t ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                            text = { Text(t, fontSize = 12.sp, maxLines = 1) })
                    }
                }
            }
        },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> RealmTab(s, vm, navController)
                1 -> StudyTab(s, vm, navController)
                2 -> ArenaTab(s, navController)
                3 -> StatsTab(navController)
            }
        }
    }
}

// ── Realm Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun RealmTab(s: AscensionState, vm: AscensionHallViewModel, navController: NavController) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)) {

        // Battle Power card
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF0A0A2E), Color(0xFF1A1A4E))))
                .border(1.dp, ahGradeColor(s.grade).copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                .padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚔️ Battle Power", color = Color(0xFF888888), fontSize = 12.sp)
                        Text("${s.battlePower}", fontSize = 48.sp, fontWeight = FontWeight.Black,
                            color = ahGradeColor(s.grade))
                        Text("Grade ${s.grade}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = ahGradeColor(s.grade))
                        Text(gradeCommentary(s.grade), fontSize = 11.sp, color = Color(0xFF666666))
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScorePill("⚔️", "${s.questScore}/40", Color(0xFF5C6BC0))
                        ScorePill("📚", "${s.studyScore}/30", Color(0xFF4CAF50))
                        ScorePill("📱", "${s.screenScore}/20", Color(0xFF2196F3))
                        ScorePill("🔥", "${s.streakScore}/10", Color(0xFFFF6D00))
                    }
                }
            }
        }

        // Today's classes
        if (s.todayClasses.isNotEmpty()) {
            item { SectionLabel("📅 Today's Classes") }
            items(s.todayClasses, key = { it.id }) { cls ->
                val done = vm.isClassDoneToday(cls)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (done) Color(0xFF0A2E0A) else Color(0xFF1A1A2E))
                    .border(1.dp, if (done) Color(0xFF2E5E2E) else Color(0xFF2A2A4A), RoundedCornerShape(12.dp))
                    .padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cls.name, fontWeight = FontWeight.Medium, color = Color.White)
                        Text("${cls.subject} · ${cls.durationHours}h · ${cls.startTime}",
                            fontSize = 12.sp, color = Color.Gray)
                    }
                    if (done) Text("✅ Done", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
                    else FilledTonalButton(onClick = { vm.markClassComplete(cls) },
                        modifier = Modifier.height(34.dp), shape = RoundedCornerShape(8.dp)) {
                        Text("Mark Done", fontSize = 11.sp)
                    }
                }
            }
        }

        // Weekly Boss
        item {
            AHCard(onClick = { navController.navigate(RootRoute.BossBattle.route) }, bg = Color(0xFF1A0A00)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚔️ Weekly Boss", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text(s.bossName, color = Color(0xFFFF7043), fontWeight = FontWeight.SemiBold)
                        if (s.bossDefeated) Text("✅ Defeated this week", color = Color(0xFF4CAF50), fontSize = 11.sp)
                        else {
                            LinearProgressIndicator(progress = { s.bossHpFraction },
                                Modifier.fillMaxWidth().height(5.dp),
                                color = Color(0xFFE53935), strokeCap = StrokeCap.Round)
                            Text("${(s.bossHpFraction * 100).toInt()}% HP remaining", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Text(s.bossEmoji, fontSize = 36.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        // Quest Chains
        item {
            AHCard(onClick = { navController.navigate(RootRoute.QuestChains.route) }, bg = Color(0xFF0D0A1A)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔗 Quest Chains", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        if (s.activeChain.isNotBlank()) {
                            Text(s.activeChain, color = Color(0xFF5C6BC0), fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(
                                progress = { if (s.activeChainTotal > 0) s.activeChainStep.toFloat() / s.activeChainTotal else 0f },
                                Modifier.fillMaxWidth(0.7f).height(5.dp), strokeCap = StrokeCap.Round)
                            Text("Step ${s.activeChainStep} / ${s.activeChainTotal}", color = Color.Gray, fontSize = 11.sp)
                        } else Text("No active chain — start one", color = Color.Gray, fontSize = 12.sp)
                    }
                    Text("→", color = Color.Gray, fontSize = 20.sp)
                }
            }
        }

        // Store + Chains row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AHCard(onClick = { navController.navigate(RootRoute.Store.route) }, bg = Color(0xFF1A1200), modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🛒", fontSize = 24.sp)
                        Text("Store", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text("Buy items & boosters", color = Color.Gray, fontSize = 10.sp)
                    }
                }
                AHCard(onClick = { navController.navigate(RootRoute.QuestChains.route) }, bg = Color(0xFF121A00), modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🔗", fontSize = 24.sp)
                        Text("Chains", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text("Quest chain progress", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ── Study Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun StudyTab(s: AscensionState, vm: AscensionHallViewModel, navController: NavController) {
    val info = vm.userRepository.userInfo
    var showAddClass by remember { mutableStateOf(false) }
    var showAddMock by remember { mutableStateOf(false) }
    var className by remember { mutableStateOf("") }
    var classSubject by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("2") }
    var startTime by remember { mutableStateOf("09:00") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var isRecorded by remember { mutableStateOf(false) }
    var mockName by remember { mutableStateOf("") }
    var obtained by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var mockNotes by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)) {

        // Study progress
        item {
            val frac = (s.studyHoursToday / s.studyGoalHours).coerceIn(0f, 1f)
            AHCard(onClick = {}, bg = Color(0xFF0A1A0A)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("📚 Today's Study", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${String.format("%.1f", s.studyHoursToday)}h / ${s.studyGoalHours}h",
                            color = Color.Gray, fontSize = 13.sp)
                    }
                    LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().height(10.dp),
                        color = if (frac >= 1f) Color(0xFF4CAF50) else Color(0xFF5C6BC0),
                        trackColor = Color(0xFF222222), strokeCap = StrokeCap.Round)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("🔥 ${s.studyStreak}d streak", fontSize = 12.sp, color = Color.Gray)
                        if (s.daysToExam != null)
                            Text("🎯 ${s.daysToExam}d to ${s.targetExam}", fontSize = 12.sp, color = Color(0xFF5C6BC0))
                    }
                }
            }
        }

        // Last mock
        if (s.lastMockName.isNotBlank()) {
            item {
                AHCard(onClick = {}, bg = Color(0xFF1A0A1A)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📝 Last Mock: ${s.lastMockName}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        Text("${s.lastMockPct}%", fontSize = 24.sp, fontWeight = FontWeight.Black,
                            color = when { s.lastMockPct >= 80 -> Color(0xFF4CAF50); s.lastMockPct >= 60 -> Color(0xFFFFB300); else -> Color(0xFFF44336) })
                        Text(vm.estimateRank(s.targetExam, s.lastMockPct.toFloat()), fontSize = 12.sp, color = Color(0xFFFFB300))
                    }
                }
            }
        }

        // Subject breakdown
        val subjectHours = info.studySubjectHours
        if (subjectHours.isNotEmpty()) {
            item { SectionLabel("📖 Subject Hours") }
            items(subjectHours.entries.sortedByDescending { it.value }.toList()) { (subj, hrs) ->
                val maxH = subjectHours.values.maxOrNull() ?: 1f
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF111111)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(subj, fontWeight = FontWeight.Medium, color = Color.White, fontSize = 13.sp)
                        Text("${String.format("%.1f", hrs)}h", color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { hrs / maxH }, Modifier.fillMaxWidth().height(4.dp),
                        color = Color(0xFF5C6BC0), trackColor = Color(0xFF222222), strokeCap = StrokeCap.Round)
                }
            }
        }

        // Classes
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                SectionLabel("📅 All Classes")
                FilledTonalButton(onClick = { showAddClass = !showAddClass },
                    modifier = Modifier.height(32.dp)) {
                    Text("+ Add", fontSize = 12.sp)
                }
            }
        }

        if (showAddClass) {
            item {
                val timePicker = android.app.TimePickerDialog(ctx,
                    { _, h, m -> startTime = "%02d:%02d".format(h, m) }, 9, 0, true)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("New Class", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(className, { className = it }, label = { Text("Class name") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        if (info.studySubjects.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                info.studySubjects.forEach { subj ->
                                    FilterChip(selected = classSubject == subj, onClick = { classSubject = subj },
                                        label = { Text(subj, fontSize = 12.sp) })
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111)).padding(4.dp)) {
                            listOf(false to "🔴 Live", true to "📼 Recorded").forEach { (rec, label) ->
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                    .background(if (isRecorded == rec) Color(0xFF5C6BC0) else Color.Transparent)
                                    .clickable { isRecorded = rec }.padding(vertical = 8.dp), Alignment.Center) {
                                    Text(label, fontSize = 12.sp, color = if (isRecorded == rec) Color.White else Color.Gray)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1", "1.5", "2", "2.5", "3").forEach { h ->
                                FilterChip(selected = duration == h, onClick = { duration = h },
                                    label = { Text("${h}h", fontSize = 12.sp) })
                            }
                        }
                        if (!isRecorded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(startTime, {}, label = { Text("Time") },
                                    modifier = Modifier.weight(1f), singleLine = true, readOnly = true)
                                FilledTonalButton(onClick = { timePicker.show() }) { Text("Pick") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayNames.forEachIndexed { i, d ->
                                val day = i + 1; val sel = day in selectedDays
                                Box(Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (sel) Color(0xFF5C6BC0) else Color(0xFF1A1A1A))
                                    .border(1.dp, if (sel) Color(0xFF5C6BC0) else Color(0xFF333333), CircleShape)
                                    .clickable { selectedDays = if (sel) selectedDays - day else selectedDays + day },
                                    Alignment.Center) { Text(d, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Button(onClick = {
                            if (className.isNotBlank() && selectedDays.isNotEmpty()) {
                                vm.addClass(StudyClass(id = UUID.randomUUID().toString(),
                                    name = className, subject = classSubject,
                                    durationHours = duration.toFloatOrNull() ?: 2f,
                                    daysOfWeek = selectedDays.sorted(),
                                    startTime = if (isRecorded) "Recorded" else startTime))
                                className = ""; classSubject = ""; selectedDays = emptySet(); showAddClass = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add Class") }
                    }
                }
            }
        }

        if (info.studyClasses.isEmpty() && !showAddClass) {
            item { Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                Text("No classes yet. Tap Add.", color = Color.Gray, textAlign = TextAlign.Center) } }
        } else {
            items(info.studyClasses.toList(), key = { it.id }) { cls ->
                var confirmDelete by remember { mutableStateOf(false) }
                val days = cls.daysOfWeek.joinToString(" · ") {
                    listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").getOrElse(it - 1) { "?" }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(cls.name, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("${cls.subject} · ${cls.durationHours}h · ${if (cls.startTime == "Recorded") "📼" else cls.startTime}",
                                    fontSize = 12.sp, color = Color.Gray)
                                Text(days, fontSize = 12.sp, color = Color(0xFF5C6BC0))
                            }
                            IconButton(onClick = { confirmDelete = true }, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null,
                                    tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
                            }
                        }
                        if (confirmDelete) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                            Button(onClick = { vm.removeClass(cls.id); confirmDelete = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Text("Delete") }
                        }
                    }
                }
            }
        }

        // Mocks
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                SectionLabel("📝 Mock Tests")
                FilledTonalButton(onClick = { showAddMock = !showAddMock }, Modifier.height(32.dp)) {
                    Text("+ Add", fontSize = 12.sp)
                }
            }
        }

        if (showAddMock) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add Mock Score", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(mockName, { mockName = it }, label = { Text("Test name") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(obtained, { obtained = it }, label = { Text("Scored") },
                                modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(total, { total = it }, label = { Text("Total") },
                                modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedTextField(mockNotes, { mockNotes = it }, label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val obt = obtained.toIntOrNull() ?: return@Button
                            val tot = total.toIntOrNull() ?: return@Button
                            if (mockName.isBlank()) return@Button
                            vm.addMockScore(MockTestScore(id = UUID.randomUUID().toString(),
                                examName = mockName, date = LocalDate.now().toString(),
                                totalMarks = tot, obtainedMarks = obt, notes = mockNotes))
                            mockName = ""; obtained = ""; total = ""; mockNotes = ""; showAddMock = false
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                    }
                }
            }
        }

        val mocks = info.studyMockScores
        if (mocks.isEmpty() && !showAddMock) {
            item { Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                Text("No mocks yet.", color = Color.Gray) } }
        } else {
            items(mocks, key = { it.id }) { mock ->
                val pct = mock.obtainedMarks * 100f / mock.totalMarks.coerceAtLeast(1)
                var confirmDelete by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(mock.examName, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(mock.date, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { confirmDelete = true }, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null,
                                    tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
                            }
                        }
                        Text("${mock.obtainedMarks}/${mock.totalMarks} · ${pct.toInt()}%",
                            color = when { pct >= 80f -> Color(0xFF4CAF50); pct >= 60f -> Color(0xFFFFB300); else -> Color(0xFFF44336) },
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(vm.estimateRank(s.targetExam, pct), fontSize = 12.sp, color = Color(0xFFAAAAAA))
                        if (confirmDelete) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                            Button(onClick = { vm.removeMockScore(mock.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

// ── Arena Tab (Rival + Leaderboard) ──────────────────────────────────────────

@Composable
private fun ArenaTab(s: AscensionState, navController: NavController) {
    val isWinning = s.yourQuestsToday >= s.rivalQuestsToday
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)) {

        // Rival card
        item {
            AHCard(onClick = { navController.navigate(RootRoute.RivalScreen.route) },
                bg = if (isWinning) Color(0xFF0A1A0A) else Color(0xFF1A0A0A)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("👤 Shadow Rival: ${s.rivalName}", fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${s.yourQuestsToday}", fontSize = 28.sp, fontWeight = FontWeight.Black,
                                color = if (isWinning) Color(0xFF4CAF50) else Color.White)
                            Text("You", color = Color.Gray, fontSize = 11.sp)
                        }
                        Text("VS", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${s.rivalQuestsToday}", fontSize = 28.sp, fontWeight = FontWeight.Black,
                                color = if (!isWinning) Color(0xFFE53935) else Color.White)
                            Text("Rival", color = Color.Gray, fontSize = 11.sp)
                        }
                        Text("/ ${s.totalQuests} today", color = Color.Gray, fontSize = 12.sp)
                    }
                    Text(if (isWinning) "⚔️ You're ahead — maintain dominance" else "⚠️ Rival is winning — catch up now",
                        fontSize = 12.sp, color = if (isWinning) Color(0xFF4CAF50) else Color(0xFFFF5722))
                }
            }
        }

        // Leaderboard
        item { SectionLabel("🏆 Exam Leaderboard · Today") }
        item { Text("Battle Power ranking among simulated exam peers",
            fontSize = 11.sp, color = Color(0xFF555555), modifier = Modifier.padding(bottom = 4.dp)) }

        items(s.leaderboard) { entry ->
            val bg = when {
                entry.isYou -> Color(0xFF1A1A2E)
                entry.rank == 1 -> Color(0xFF1A1400)
                entry.rank == 2 -> Color(0xFF141414)
                entry.rank == 3 -> Color(0xFF1A0A00)
                else -> Color(0xFF0D0D0D)
            }
            val border = when {
                entry.isYou -> Color(0xFF5C6BC0)
                entry.rank == 1 -> Color(0xFFFFD700)
                entry.rank == 2 -> Color(0xFFB0BEC5)
                entry.rank == 3 -> Color(0xFFFF6D00)
                else -> Color(0xFF222222)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(bg).border(1.dp, border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (entry.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${entry.rank}" },
                        fontSize = if (entry.rank <= 3) 18.sp else 13.sp, color = border,
                        fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                    Column {
                        Text(if (entry.isYou) "⚡ ${entry.name}" else entry.name,
                            fontWeight = if (entry.isYou) FontWeight.Bold else FontWeight.Normal,
                            color = if (entry.isYou) Color(0xFF5C6BC0) else Color.White, fontSize = 14.sp)
                        Text("${String.format("%.1f", entry.studyHours)}h studied",
                            fontSize = 11.sp, color = Color.Gray)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${entry.score}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = ahGradeColor(entry.grade))
                    Text(entry.grade, fontSize = 11.sp, color = ahGradeColor(entry.grade))
                }
            }
        }

        item { Spacer(Modifier.height(8.dp))
            Text("⚠️ Leaderboard is simulated for motivation. Rankings reset daily.",
                fontSize = 10.sp, color = Color(0xFF333333), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()) }
    }
}

// ── Stats Tab ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsTab(navController: NavController) {
    val entries = listOf(
        Triple("📱", "Screen Time", RootRoute.ShowScreentimeStats.route),
        Triple("⏱", "Focus Sessions", RootRoute.FocusSessionHistory.route),
        Triple("📈", "Stat History", RootRoute.StatHistory.route),
        Triple("🪙", "Coin Log", RootRoute.CoinTransactionLog.route),
        Triple("💾", "Backup & Restore", RootRoute.BackupRestore.route),
    )
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 14.dp)) {
        items(entries) { (emoji, label, route) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111111))
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(14.dp))
                .clickable { navController.navigate(route) }
                .padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 22.sp)
                    Text(label, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Text("→", color = Color.Gray, fontSize = 18.sp)
            }
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

@Composable
private fun AscensionSettingsScreen(vm: AscensionHallViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val info = vm.userRepository.userInfo
    var exam by remember { mutableStateOf(info.studyTargetExam.ifBlank { "JEE" }) }
    var targetDate by remember { mutableStateOf(info.studyTargetDate) }
    var subjectsText by remember { mutableStateOf(info.studySubjects.joinToString(", ")) }
    var dailyGoal by remember { mutableStateOf(info.dailyStudyQuotaHours.toInt().toString()) }
    var saved by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var appRefresh by remember { mutableIntStateOf(0) }

    val cal = Calendar.getInstance()
    val datePicker = android.app.DatePickerDialog(ctx,
        { _, y, m, d -> targetDate = "%04d-%02d-%02d".format(y, m + 1, d) },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Ascension Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }
        HorizontalDivider()
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            item { Text("Target Exam", fontWeight = FontWeight.SemiBold, color = Color.White) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("JEE", "NEET", "UPSC", "Other").forEach { e ->
                    FilterChip(selected = exam == e, onClick = { exam = e }, label = { Text(e) }) }
            }}
            item { Text("Exam Date", fontWeight = FontWeight.SemiBold, color = Color.White) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = if (targetDate.isBlank()) "Not set" else targetDate, onValueChange = {},
                    label = { Text("Date") }, modifier = Modifier.weight(1f), singleLine = true, readOnly = true)
                FilledTonalButton(onClick = { datePicker.show() }) { Text("Pick") }
            }}
            item { OutlinedTextField(value = subjectsText, onValueChange = { subjectsText = it },
                label = { Text("Subjects (comma separated)") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("Daily Study Goal", fontWeight = FontWeight.SemiBold, color = Color.White) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4", "6", "8", "10", "12").forEach { h ->
                    FilterChip(selected = dailyGoal == h, onClick = { dailyGoal = h }, label = { Text("${h}h") }) }
            }}
            item { Button(onClick = {
                val subjects = subjectsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                info.studyTargetExam = exam; info.studyTargetDate = targetDate
                info.studySubjects = subjects.toMutableList()
                info.dailyStudyQuotaHours = dailyGoal.toFloatOrNull() ?: 4f
                vm.userRepository.saveUserInfo(); saved = true
            }, modifier = Modifier.fillMaxWidth()) { Text("Save Settings") }}
            if (saved) item { Text("✅ Saved!", color = Color(0xFF4CAF50)) }
            item { HorizontalDivider() }
            item { Text("📱 Study App Tracking", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp) }
            item { Text("Screen time from these apps counts as study hours", fontSize = 12.sp, color = Color.Gray) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    info.studyApps.toList().forEach { pkg ->
                        val name = runCatching { ctx.packageManager.getApplicationLabel(
                            ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF111111)).padding(horizontal = 12.dp, vertical = 10.dp),
                            Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(name, fontSize = 13.sp, color = Color.White)
                            IconButton(onClick = {
                                info.studyApps = info.studyApps.toMutableSet().apply { remove(pkg) }
                                vm.userRepository.saveUserInfo(); appRefresh++
                            }, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null,
                                    tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            item { FilledTonalButton(onClick = { showAppPicker = !showAppPicker }, Modifier.fillMaxWidth()) {
                Text(if (showAppPicker) "Close Picker" else "Add Study App") }}
            if (showAppPicker) {
                item { OutlinedTextField(appSearch, { appSearch = it }, label = { Text("Search app") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    val installed = remember(appRefresh) {
                        ctx.packageManager.getInstalledApplications(0)
                            .filter { it.packageName != ctx.packageName }
                            .map { it.packageName to ctx.packageManager.getApplicationLabel(it).toString() }
                            .sortedBy { it.second }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        installed.filter { it.second.contains(appSearch, true) }.take(12).forEach { (pkg, name) ->
                            val added = pkg in info.studyApps
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (added) Color(0xFF0A2E0A) else Color(0xFF111111))
                                .clickable { if (!added) { info.studyApps = info.studyApps.toMutableSet().apply { add(pkg) }
                                    vm.userRepository.saveUserInfo(); appRefresh++ } }.padding(12.dp),
                                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(name, fontSize = 13.sp, color = Color.White)
                                if (added) Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
}

@Composable
private fun ScorePill(emoji: String, value: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f))
        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 11.sp)
        Text(value, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AHCard(onClick: () -> Unit, bg: Color, modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

private fun ahGradeColor(grade: String) = when (grade) {
    "S" -> Color(0xFFFFD700); "A" -> Color(0xFF4CAF50); "B" -> Color(0xFF2196F3)
    "C" -> Color(0xFFFF9800); "D" -> Color(0xFFFF5722); else -> Color(0xFFE53935)
}

private fun gradeCommentary(grade: String) = when (grade) {
    "S" -> "Legendary. Top 1% today. 🏆"
    "A" -> "Excellent. Keep this momentum. ⚡"
    "B" -> "Solid. A bit more focus for S-rank."
    "C" -> "Average. You can do better."
    "D" -> "Struggling. One quest at a time."
    else -> "Not your day. But tomorrow can be."
}
