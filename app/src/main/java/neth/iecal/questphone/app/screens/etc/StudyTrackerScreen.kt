package neth.iecal.questphone.app.screens.etc

import android.app.DatePickerDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.MockTestScore
import nethical.questphone.data.StudyClass
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class StudyTrackerViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    private val _refreshKey = MutableStateFlow(0)
    val refreshKey = _refreshKey.asStateFlow()

    fun refresh() { _refreshKey.value++ }

    fun logStudyHours(hours: Float, subject: String) {
        val today = LocalDate.now().toString()
        val info = userRepository.userInfo
        info.studyDailyLogHours[today] = (info.studyDailyLogHours[today] ?: 0f) + hours
        info.studySubjectHours[subject] = (info.studySubjectHours[subject] ?: 0f) + hours
        val yesterday = LocalDate.now().minusDays(1).toString()
        info.studyStreak = when (info.studyLastLogDate) {
            today     -> info.studyStreak
            yesterday -> info.studyStreak + 1
            else      -> 1
        }
        info.studyLastLogDate = today
        val coinsEarned = (hours * 5).toInt().coerceAtLeast(1)
        userRepository.addCoins(coinsEarned, "Study: $subject")
        userRepository.saveUserInfo()
        refresh()
    }

    fun addMockScore(score: MockTestScore) {
        userRepository.userInfo.studyMockScores.add(0, score)
        userRepository.saveUserInfo()
        refresh()
    }

    fun addClass(cls: StudyClass) {
        userRepository.userInfo.studyClasses.add(cls)
        userRepository.saveUserInfo()
        refresh()
    }

    fun removeClass(id: String) {
        userRepository.userInfo.studyClasses.removeAll { it.id == id }
        userRepository.saveUserInfo()
        refresh()
    }

    fun removeMockScore(id: String) {
        userRepository.userInfo.studyMockScores.removeAll { it.id == id }
        userRepository.saveUserInfo()
        refresh()
    }

    fun saveMeta(exam: String, targetDate: String, subjects: List<String>, dailyGoal: Float) {
        userRepository.userInfo.studyTargetExam = exam
        userRepository.userInfo.studyTargetDate = targetDate
        userRepository.userInfo.studySubjects = subjects.toMutableList()
        userRepository.userInfo.dailyStudyQuotaHours = dailyGoal
        userRepository.saveUserInfo()
        refresh()
    }

    fun estimateRank(exam: String, pct: Float): String = when (exam.uppercase()) {
        "JEE"  -> when {
            pct >= 99f -> "Top 500 — IIT Bombay/Delhi likely 🏆"
            pct >= 97f -> "Top 2,000 — Good IIT 🎯"
            pct >= 93f -> "Top 10,000 — NIT+ 📈"
            pct >= 85f -> "Top 30,000 — NIT 📊"
            else       -> "Below 30,000 — keep pushing 💪"
        }
        "NEET" -> when {
            pct >= 99f -> "Top 100 — AIIMS Delhi likely 🏆"
            pct >= 95f -> "Top 1,000 — AIIMS 🎯"
            pct >= 88f -> "Top 10,000 — Govt Medical 📈"
            pct >= 75f -> "Top 50,000 — State college 📊"
            else       -> "Below 50,000 — focus on NCERT 💪"
        }
        "UPSC" -> when {
            pct >= 75f -> "High chance of clearing Mains 🏆"
            pct >= 60f -> "Prelims likely, Mains needs work 📈"
            pct >= 50f -> "Prelims borderline 📊"
            else       -> "Focus on basics first 💪"
        }
        else -> if (pct >= 80f) "Looking strong! 🎯" else "Needs improvement 💪"
    }

    /** Gets today's usage for tracked study apps via UsageStatsManager */
    fun getStudyAppUsageToday(ctx: Context): Map<String, Long> {
        val trackedApps = userRepository.userInfo.studyApps
        if (trackedApps.isEmpty()) return emptyMap()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        return stats
            .filter { it.packageName in trackedApps && it.totalTimeInForeground > 0 }
            .associate { it.packageName to it.totalTimeInForeground }
    }
}

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTrackerScreen(
    navController: NavController,
    vm: StudyTrackerViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val refreshKey by vm.refreshKey.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val tabs = listOf("Home", "Log", "Mocks", "Classes")

    if (showSettings) {
        StudySettingsScreen(vm, onBack = { showSettings = false })
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Study Tracker", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val exam = vm.userRepository.userInfo.studyTargetExam
                            if (exam.isNotBlank())
                                Text(exam, fontSize = 12.sp, color = Color(0xFF5C6BC0))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    },
                    windowInsets = WindowInsets(0)
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent
                ) {
                    tabs.forEachIndexed { i, t ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = { Text(t, fontSize = 13.sp, maxLines = 1) }
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DashboardTab(vm, refreshKey)
                1 -> LogTab(vm, refreshKey)
                2 -> MocksTab(vm, refreshKey)
                3 -> ClassesTab(vm, refreshKey)
            }
        }
    }
}

// ── Dashboard tab ─────────────────────────────────────────────────────────────

@Composable
private fun DashboardTab(vm: StudyTrackerViewModel, refreshKey: Int) {
    val ctx = LocalContext.current
    val info = vm.userRepository.userInfo
    val today = LocalDate.now().toString()
    val todayHours = info.studyDailyLogHours[today] ?: 0f
    val quotaHours = info.dailyStudyQuotaHours
    val fraction = (todayHours / quotaHours).coerceIn(0f, 1f)
    val lastMock = info.studyMockScores.firstOrNull()
    val lastPct = lastMock?.let { it.obtainedMarks * 100f / it.totalMarks.coerceAtLeast(1) }

    val daysLeft = remember(refreshKey) {
        runCatching {
            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(info.studyTargetDate))
        }.getOrNull()
    }

    val appUsage = remember(refreshKey) { vm.getStudyAppUsageToday(ctx) }
    val totalAppMs = appUsage.values.sumOf { it }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Exam countdown ──
        item {
            if (info.studyTargetExam.isBlank()) {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🎯", fontSize = 28.sp)
                        Column {
                            Text("Set up your exam", fontWeight = FontWeight.Bold)
                            Text("Tap ⚙️ to configure your target exam",
                                fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                    Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("🎯 ${info.studyTargetExam}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (daysLeft != null)
                                Text("$daysLeft days remaining", fontSize = 13.sp, color = Color(0xFF5C6BC0))
                            else
                                Text("Set exam date in ⚙️", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (daysLeft != null) {
                            Box(Modifier.size(56.dp).clip(CircleShape)
                                .background(Color(0xFF5C6BC0).copy(alpha = 0.2f)),
                                Alignment.Center) {
                                Text("$daysLeft", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5C6BC0))
                            }
                        }
                    }
                }
            }
        }

        // ── Today's progress ──
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Today's Progress", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = if (fraction >= 1f) Color(0xFF4CAF50) else Color(0xFF5C6BC0),
                        trackColor = Color(0xFF2A2A2A),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("${todayHours}h logged", fontWeight = FontWeight.SemiBold)
                        Text("Goal: ${quotaHours}h", color = Color.Gray, fontSize = 13.sp)
                    }
                    if (fraction >= 1f)
                        Text("✅ Goal hit! +${(todayHours * 5).toInt()} coins earned",
                            color = Color(0xFF4CAF50), fontSize = 13.sp)
                }
            }
        }

        // ── App screen time ──
        if (totalAppMs > 0) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📱 Study App Time Today", fontWeight = FontWeight.Bold)
                        Text(formatMs(totalAppMs), fontSize = 20.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFF5C6BC0))
                        appUsage.entries.sortedByDescending { it.value }.forEach { (pkg, ms) ->
                            val name = runCatching {
                                ctx.packageManager.getApplicationLabel(
                                    ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
                            }.getOrDefault(pkg)
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(name, fontSize = 13.sp, color = Color.Gray)
                                Text(formatMs(ms), fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        } else if (info.studyApps.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📱", fontSize = 22.sp)
                        Text("No study app time recorded today yet",
                            fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
        }

        // ── Streak + coins ──
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStatCard("🔥", "${info.studyStreak}", "Day streak", Modifier.weight(1f))
                MiniStatCard("🪙", "${info.coins}", "Coins", Modifier.weight(1f))
                MiniStatCard("📅", "${info.studyDailyLogHours.size}", "Days tracked", Modifier.weight(1f))
            }
        }

        // ── Rank estimator ──
        if (lastMock != null && lastPct != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📊 Rank Estimator", fontWeight = FontWeight.Bold)
                        Text("Based on: ${lastMock.examName} · ${lastMock.date}",
                            fontSize = 12.sp, color = Color.Gray)
                        Text("${lastMock.obtainedMarks}/${lastMock.totalMarks} (${lastPct.toInt()}%)",
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF5C6BC0),
                            fontSize = 16.sp)
                        Text(vm.estimateRank(info.studyTargetExam, lastPct),
                            fontSize = 13.sp, color = Color(0xFFFFB300))
                    }
                }
            }
        }

        // ── Subject breakdown ──
        if (info.studySubjectHours.isNotEmpty()) {
            item { Text("Subject Hours", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(info.studySubjectHours.entries.sortedByDescending { it.value }.toList()) { (subj, hrs) ->
                val maxHrs = info.studySubjectHours.values.maxOrNull() ?: 1f
                Column(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF111111))
                    .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(subj, fontWeight = FontWeight.Medium)
                        Text("${hrs}h", color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { hrs / maxHrs },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color(0xFF5C6BC0),
                        trackColor = Color(0xFF222222),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }

        // ── Today's classes ──
        val todayClasses = info.studyClasses.filter {
            LocalDate.now().dayOfWeek.value in it.daysOfWeek
        }
        if (todayClasses.isNotEmpty()) {
            item { Text("Today's Classes", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(todayClasses) { cls ->
                Row(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1A2E))
                    .padding(12.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text(cls.name, fontWeight = FontWeight.Medium)
                        Text("${cls.subject} · ${cls.durationHours}h · ${cls.startTime}",
                            fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("📚", fontSize = 20.sp)
                }
            }
        }

        // ── Last 7 days ──
        val recent = info.studyDailyLogHours.entries.sortedByDescending { it.key }.take(7)
        if (recent.isNotEmpty()) {
            item { Text("Last 7 Days", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val maxH = recent.maxOf { it.value }
                        recent.forEach { (date, hrs) ->
                            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp),
                                Alignment.CenterVertically) {
                                Text(date.takeLast(5), fontSize = 11.sp, color = Color.Gray,
                                    modifier = Modifier.width(44.dp))
                                LinearProgressIndicator(
                                    progress = { hrs / maxH.coerceAtLeast(1f) },
                                    modifier = Modifier.weight(1f).height(8.dp),
                                    color = Color(0xFF5C6BC0),
                                    trackColor = Color(0xFF222222),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Text("${hrs}h", fontSize = 11.sp, color = Color.White,
                                    modifier = Modifier.width(36.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Log tab ───────────────────────────────────────────────────────────────────

@Composable
private fun LogTab(vm: StudyTrackerViewModel, refreshKey: Int) {
    val info = vm.userRepository.userInfo
    var selectedSubject by remember { mutableStateOf(info.studySubjects.firstOrNull() ?: "") }
    var hoursInput by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 8.dp)) {

        item {
            Text("Log Study Session", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Earn 5 coins per hour 🪙", fontSize = 13.sp, color = Color.Gray)
        }

        if (info.studySubjects.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙️", fontSize = 22.sp)
                        Text("Add subjects in Settings first", color = Color.Gray)
                    }
                }
            }
        } else {
            item {
                Text("Subject", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.studySubjects.forEach { subj ->
                        FilterChip(selected = selectedSubject == subj,
                            onClick = { selectedSubject = subj },
                            label = { Text(subj, fontSize = 12.sp) })
                    }
                }
            }
        }

        item {
            Text("Hours studied", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("0.5", "1", "1.5", "2", "2.5", "3").forEach { h ->
                    FilterChip(selected = hoursInput == h, onClick = { hoursInput = h },
                        label = { Text("${h}h", fontSize = 12.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = hoursInput, onValueChange = { hoursInput = it },
                label = { Text("Custom hours") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }

        item {
            Button(
                onClick = {
                    val hrs = hoursInput.toFloatOrNull()
                    if (hrs != null && hrs > 0 && selectedSubject.isNotBlank()) {
                        vm.logStudyHours(hrs, selectedSubject)
                        hoursInput = ""
                        showSuccess = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = info.studySubjects.isNotEmpty()
            ) { Text("Log Session ✓", fontWeight = FontWeight.Bold) }
            if (showSuccess) {
                Spacer(Modifier.height(8.dp))
                Text("✅ Logged! Coins awarded.", color = Color(0xFF4CAF50), fontSize = 13.sp)
            }
        }
    }
}

// ── Mocks tab ─────────────────────────────────────────────────────────────────

@Composable
private fun MocksTab(vm: StudyTrackerViewModel, refreshKey: Int) {
    val info = vm.userRepository.userInfo
    var showAdd by remember { mutableStateOf(false) }
    var examName by remember { mutableStateOf("") }
    var obtained by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Full") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)) {

        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Mock Tests", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                FilledTonalButton(onClick = { showAdd = !showAdd }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (showAdd) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add Mock Score", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(examName, { examName = it },
                            label = { Text("Test name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(obtained, { obtained = it },
                                label = { Text("Scored") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(total, { total = it },
                                label = { Text("Total") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        OutlinedTextField(notes, { notes = it },
                            label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                val obt = obtained.toIntOrNull() ?: return@Button
                                val tot = total.toIntOrNull() ?: return@Button
                                if (examName.isBlank()) return@Button
                                vm.addMockScore(MockTestScore(
                                    id = UUID.randomUUID().toString(),
                                    examName = examName, date = LocalDate.now().toString(),
                                    totalMarks = tot, obtainedMarks = obt,
                                    subject = subject, notes = notes
                                ))
                                examName = ""; obtained = ""; total = ""; notes = ""
                                showAdd = false
                            }, modifier = Modifier.fillMaxWidth()
                        ) { Text("Save") }
                    }
                }
            }
        }

        if (info.studyMockScores.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📝", fontSize = 40.sp)
                        Text("No mock scores yet", color = Color.Gray)
                    }
                }
            }
        } else {
            items(info.studyMockScores, key = { it.id }) { score ->
                val pct = score.obtainedMarks * 100f / score.totalMarks.coerceAtLeast(1)
                var showDelete by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(score.examName, fontWeight = FontWeight.Bold)
                                Text(score.date, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { showDelete = true }, Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null,
                                    tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                            }
                        }
                        Text("${score.obtainedMarks}/${score.totalMarks} · ${pct.toInt()}%",
                            color = when {
                                pct >= 80f -> Color(0xFF4CAF50)
                                pct >= 60f -> Color(0xFFFFB300)
                                else       -> Color(0xFFF44336)
                            }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(vm.estimateRank(info.studyTargetExam, pct),
                            fontSize = 12.sp, color = Color(0xFFAAAAAA))
                        if (score.notes.isNotBlank())
                            Text(score.notes, fontSize = 12.sp, color = Color.Gray)
                        if (showDelete) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
                                Button(onClick = { vm.removeMockScore(score.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Classes tab ───────────────────────────────────────────────────────────────

@Composable
private fun ClassesTab(vm: StudyTrackerViewModel, refreshKey: Int) {
    val info = vm.userRepository.userInfo
    var showAdd by remember { mutableStateOf(false) }
    var className by remember { mutableStateOf("") }
    var classSubject by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("2") }
    var startTime by remember { mutableStateOf("09:00") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")

    LazyColumn(Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)) {

        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Classes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${info.studyClasses.size} classes scheduled",
                        fontSize = 12.sp, color = Color.Gray)
                }
                FilledTonalButton(onClick = { showAdd = !showAdd }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (showAdd) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("New Class", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(className, { className = it },
                            label = { Text("Class name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(classSubject, { classSubject = it },
                            label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(duration, { duration = it },
                                label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(startTime, { startTime = it },
                                label = { Text("Start (HH:mm)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Text("Repeat on", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayNames.forEachIndexed { i, d ->
                                val day = i + 1
                                val sel = day in selectedDays
                                Box(Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (sel) Color(0xFF5C6BC0) else Color(0xFF1A1A1A))
                                    .border(1.dp, if (sel) Color(0xFF5C6BC0) else Color(0xFF333333), CircleShape)
                                    .clickable { selectedDays = if (sel) selectedDays - day else selectedDays + day },
                                    Alignment.Center) {
                                    Text(d, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Button(
                            onClick = {
                                if (className.isNotBlank() && selectedDays.isNotEmpty()) {
                                    vm.addClass(StudyClass(
                                        id = UUID.randomUUID().toString(),
                                        name = className, subject = classSubject,
                                        durationHours = duration.toFloatOrNull() ?: 2f,
                                        daysOfWeek = selectedDays.sorted(), startTime = startTime
                                    ))
                                    className = ""; classSubject = ""; selectedDays = emptySet(); showAdd = false
                                }
                            }, modifier = Modifier.fillMaxWidth()
                        ) { Text("Add Class") }
                    }
                }
            }
        }

        if (info.studyClasses.isEmpty() && !showAdd) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📅", fontSize = 40.sp)
                        Text("No classes yet", color = Color.Gray)
                        Text("Tap Add to schedule your coaching classes",
                            fontSize = 12.sp, color = Color(0xFF555555))
                    }
                }
            }
        } else {
            items(info.studyClasses.toList(), key = { it.id }) { cls ->
                val days = cls.daysOfWeek.joinToString(" ") {
                    listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").getOrElse(it - 1) { "?" }
                }
                var confirmDelete by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(cls.name, fontWeight = FontWeight.SemiBold)
                                Text("${cls.subject} · ${cls.durationHours}h · ${cls.startTime}",
                                    fontSize = 12.sp, color = Color.Gray)
                                Text(days, fontSize = 12.sp, color = Color(0xFF5C6BC0))
                            }
                            IconButton(onClick = { confirmDelete = true }, Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null,
                                    tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                            }
                        }
                        if (confirmDelete) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                                Button(
                                    onClick = { vm.removeClass(cls.id); confirmDelete = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Settings screen ───────────────────────────────────────────────────────────

@Composable
private fun StudySettingsScreen(vm: StudyTrackerViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val info = vm.userRepository.userInfo
    var exam by remember { mutableStateOf(info.studyTargetExam.ifBlank { "JEE" }) }
    var targetDate by remember { mutableStateOf(info.studyTargetDate) }
    var subjectsText by remember { mutableStateOf(info.studySubjects.joinToString(", ")) }
    var dailyGoal by remember { mutableStateOf(info.dailyStudyQuotaHours.toString()) }
    var saved by remember { mutableStateOf(false) }

    // Date picker
    val cal = Calendar.getInstance()
    val datePicker = DatePickerDialog(
        ctx,
        { _, y, m, d -> targetDate = "%04d-%02d-%02d".format(y, m + 1, d) },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
    )

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(16.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Study Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        HorizontalDivider()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Exam
            Text("Target Exam", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("JEE", "NEET", "UPSC", "Other").forEach { e ->
                    FilterChip(selected = exam == e, onClick = { exam = e }, label = { Text(e) })
                }
            }

            // Exam date with picker
            Text("Exam Date", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = targetDate, onValueChange = { targetDate = it },
                    label = { Text("yyyy-MM-dd") }, modifier = Modifier.weight(1f),
                    singleLine = true, readOnly = true
                )
                FilledTonalButton(onClick = { datePicker.show() }) { Text("Pick") }
            }

            // Subjects
            Text("Subjects", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = subjectsText, onValueChange = { subjectsText = it },
                label = { Text("e.g. Physics, Chemistry, Maths") },
                modifier = Modifier.fillMaxWidth())

            // Daily goal
            Text("Daily Study Goal (hours)", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4", "6", "8", "10", "12").forEach { h ->
                    FilterChip(selected = dailyGoal == h, onClick = { dailyGoal = h },
                        label = { Text("${h}h") })
                }
            }

            // Save
            Button(
                onClick = {
                    val subjects = subjectsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    vm.saveMeta(exam, targetDate, subjects, dailyGoal.toFloatOrNull() ?: 4f)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Save Settings", fontWeight = FontWeight.Bold) }

            if (saved)
                Text("✅ Saved!", color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)

            HorizontalDivider()

            // Study app tracking section
            Text("Study App Time Tracking", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Add apps whose screen time counts as study time (e.g. Unacademy, YouTube, Chrome)",
                fontSize = 12.sp, color = Color.Gray)

            val trackedApps = info.studyApps.toList()
            if (trackedApps.isEmpty()) {
                Text("No apps tracked yet", color = Color(0xFF555555), fontSize = 13.sp)
            } else {
                trackedApps.forEach { pkg ->
                    val name = runCatching {
                        ctx.packageManager.getApplicationLabel(
                            ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    Row(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111111))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(name, fontSize = 13.sp)
                        IconButton(onClick = {
                            val updated = info.studyApps.toMutableSet().apply { remove(pkg) }
                            info.studyApps = updated
                            vm.userRepository.saveUserInfo()
                            vm.refresh()
                        }, Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null,
                                tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Add study app from installed list
            var showAppPicker by remember { mutableStateOf(false) }
            FilledTonalButton(onClick = { showAppPicker = !showAppPicker },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Study App")
            }

            if (showAppPicker) {
                val installedApps = remember {
                    ctx.packageManager.getInstalledApplications(0)
                        .filter { it.packageName != ctx.packageName }
                        .map { it.packageName to ctx.packageManager.getApplicationLabel(it).toString() }
                        .sortedBy { it.second }
                }
                var appSearch by remember { mutableStateOf("") }
                OutlinedTextField(appSearch, { appSearch = it },
                    label = { Text("Search app") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                val filtered = installedApps.filter {
                    it.second.contains(appSearch, true) || it.first.contains(appSearch, true)
                }.take(10)
                filtered.forEach { (pkg, name) ->
                    val alreadyAdded = pkg in info.studyApps
                    Row(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (alreadyAdded) Color(0xFF1A2E1A) else Color(0xFF111111))
                        .clickable {
                            if (!alreadyAdded) {
                                val updated = info.studyApps.toMutableSet().apply { add(pkg) }
                                info.studyApps = updated
                                vm.userRepository.saveUserInfo()
                                vm.refresh()
                            }
                        }
                        .padding(12.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(name, fontSize = 13.sp)
                        if (alreadyAdded)
                            Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun MiniStatCard(emoji: String, value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 20.sp)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

private fun formatMs(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
