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

    /** Called when user marks a coaching class as done — logs its duration */
    fun markClassComplete(cls: StudyClass) {
        val today = LocalDate.now().toString()
        val info = userRepository.userInfo
        val label = cls.subject.ifBlank { cls.name }
        info.studyDailyLogHours[today] = (info.studyDailyLogHours[today] ?: 0f) + cls.durationHours
        info.studySubjectHours[label] = (info.studySubjectHours[label] ?: 0f) + cls.durationHours
        // Mark this class as completed today so we don't double count
        val key = "cls_done_${cls.id}_$today"
        info.studyDailyLogHours[key] = 1f   // sentinel value
        updateStreak(today)
        val coins = (cls.durationHours * 5).toInt().coerceAtLeast(1)
        userRepository.addCoins(coins, "Class: ${cls.name}")
        // XP — 10 XP per hour studied
        userRepository.addXp((cls.durationHours * 10).toInt())
        // Bonus if this class pushed us over the daily study goal
        val totalAfter = (info.studyDailyLogHours[today] ?: 0f) + (info.studyDailyLogHours["screen_$today"] ?: 0f)
        val totalBefore = totalAfter - cls.durationHours
        if (totalAfter >= info.dailyStudyQuotaHours && totalBefore < info.dailyStudyQuotaHours) {
            userRepository.addXp(100)
            userRepository.addCoins(25, "Daily study goal reached! 🎯")
        }
        userRepository.saveUserInfo()
        refresh()
    }

    fun isClassDoneToday(cls: StudyClass): Boolean {
        val today = LocalDate.now().toString()
        return userRepository.userInfo.studyDailyLogHours.containsKey("cls_done_${cls.id}_$today")
    }

    /** Syncs today's screen time from tracked study apps into the daily log */
    fun syncScreenTimeToday(ctx: Context) {
        val info = userRepository.userInfo
        val trackedApps = info.studyApps
        if (trackedApps.isEmpty()) return
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        val totalMs = stats.filter { it.packageName in trackedApps }
            .sumOf { it.totalTimeInForeground }
        val totalHours = totalMs / 3_600_000f
        val today = LocalDate.now().toString()
        // Store screen time separately so it doesn't overwrite class completions
        val prevScreenTime = info.studyDailyLogHours["screen_$today"] ?: 0f
        val diff = totalHours - prevScreenTime
        if (diff > 0.01f) {
            info.studyDailyLogHours["screen_$today"] = totalHours
            val subject = "Screen Time (Apps)"
            info.studySubjectHours[subject] = (info.studySubjectHours[subject] ?: 0f) + diff
            updateStreak(today)
            userRepository.saveUserInfo()
        }
    }

    fun getTodayScreenTimeMs(ctx: Context): Map<String, Long> {
        val trackedApps = userRepository.userInfo.studyApps
        if (trackedApps.isEmpty()) return emptyMap()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            .filter { it.packageName in trackedApps && it.totalTimeInForeground > 0 }
            .associate { it.packageName to it.totalTimeInForeground }
    }

    fun getTodayTotalHours(): Float {
        val today = LocalDate.now().toString()
        val info = userRepository.userInfo
        // Sum class completions + screen time, ignore sentinel keys
        val classHours = info.studyDailyLogHours
            .filter { it.key == today }.values.sumOf { it.toDouble() }.toFloat()
        val screenHours = info.studyDailyLogHours["screen_$today"] ?: 0f
        return classHours + screenHours
    }

    private fun updateStreak(today: String) {
        val info = userRepository.userInfo
        val yesterday = LocalDate.now().minusDays(1).toString()
        info.studyStreak = when (info.studyLastLogDate) {
            today     -> info.studyStreak
            yesterday -> info.studyStreak + 1
            else      -> 1
        }
        info.studyLastLogDate = today
    }

    fun addMockScore(score: MockTestScore) {
        userRepository.userInfo.studyMockScores.add(0, score)
        userRepository.saveUserInfo()
        refresh()
    }

    fun removeMockScore(id: String) {
        userRepository.userInfo.studyMockScores.removeAll { it.id == id }
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
    val tabs = listOf("Home", "Mocks", "Classes")

    // Auto-sync screen time on open
    LaunchedEffect(Unit) { vm.syncScreenTimeToday(ctx) }

    if (showSettings) {
        StudySettingsScreen(vm, onBack = { showSettings = false })
        return
    }

    Scaffold(
        topBar = {
            Column {
                // ── Custom header with proper spacing ─────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Study Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        val exam = vm.userRepository.userInfo.studyTargetExam
                        if (exam.isNotBlank())
                            Text(exam, fontSize = 12.sp, color = Color(0xFF5C6BC0))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                // ─────────────────────────────────────────────────────────
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    tabs.forEachIndexed { i, t ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                            text = { Text(t, fontSize = 13.sp) })
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> HomeTab(vm, refreshKey, ctx)
                1 -> MocksTab(vm, refreshKey)
                2 -> ClassesTab(vm, refreshKey)
            }
        }
    }
}

// ── Home tab ──────────────────────────────────────────────────────────────────

@Composable
private fun HomeTab(vm: StudyTrackerViewModel, refreshKey: Int, ctx: Context) {
    val info = vm.userRepository.userInfo
    val today = LocalDate.now().toString()
    val todayHours = vm.getTodayTotalHours()
    val quotaHours = info.dailyStudyQuotaHours
    val fraction = (todayHours / quotaHours).coerceIn(0f, 1f)
    val lastMock = info.studyMockScores.firstOrNull()
    val lastPct = lastMock?.let { it.obtainedMarks * 100f / it.totalMarks.coerceAtLeast(1) }
    val appUsage = remember(refreshKey) { vm.getTodayScreenTimeMs(ctx) }
    val totalAppMs = appUsage.values.sumOf { it }
    val classHours = info.studyDailyLogHours[today] ?: 0f
    val screenHours = info.studyDailyLogHours["screen_$today"] ?: 0f

    val daysLeft = remember(refreshKey) {
        runCatching {
            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(info.studyTargetDate))
        }.getOrNull()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Exam countdown
        item {
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
                Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        if (info.studyTargetExam.isBlank()) {
                            Text("🎯 Set up your exam", fontWeight = FontWeight.Bold)
                            Text("Tap ⚙️ to configure", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            Text("🎯 ${info.studyTargetExam}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (daysLeft != null)
                                Text("$daysLeft days remaining", fontSize = 13.sp, color = Color(0xFF5C6BC0))
                            else
                                Text("Set exam date in ⚙️", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    if (daysLeft != null) {
                        Box(Modifier.size(52.dp).clip(CircleShape)
                            .background(Color(0xFF5C6BC0).copy(alpha = 0.2f)), Alignment.Center) {
                            Text("$daysLeft", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF5C6BC0))
                        }
                    }
                }
            }
        }

        // Today's progress
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
                        Text("${String.format("%.1f", todayHours)}h total", fontWeight = FontWeight.SemiBold)
                        Text("Goal: ${quotaHours}h", color = Color.Gray, fontSize = 13.sp)
                    }
                    // Breakdown
                    if (classHours > 0 || screenHours > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (classHours > 0)
                                Text("📚 ${String.format("%.1f", classHours)}h classes",
                                    fontSize = 12.sp, color = Color.Gray)
                            if (screenHours > 0)
                                Text("📱 ${String.format("%.1f", screenHours)}h apps",
                                    fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    if (fraction >= 1f)
                        Text("✅ Daily goal hit! +${(todayHours * 5).toInt()} coins earned",
                            color = Color(0xFF4CAF50), fontSize = 13.sp)
                }
            }
        }

        // Study app time
        if (totalAppMs > 0) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📱 Study Apps Today", fontWeight = FontWeight.Bold)
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
                        Text("No study app time yet today", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💡", fontSize = 22.sp)
                        Column {
                            Text("Track study apps", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text("Add apps like Unacademy in ⚙️ Settings",
                                fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // Mini stats
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStatCard("🔥", "${info.studyStreak}", "Streak", Modifier.weight(1f))
                MiniStatCard("🪙", "${info.coins}", "Coins", Modifier.weight(1f))
                MiniStatCard("📅", "${info.studyDailyLogHours.keys.count { !it.contains("_") }}", "Days", Modifier.weight(1f))
            }
        }

        // Rank estimator
        if (lastMock != null && lastPct != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📊 Rank Estimator", fontWeight = FontWeight.Bold)
                        Text("Based on: ${lastMock.examName}",
                            fontSize = 12.sp, color = Color.Gray)
                        Text("${lastMock.obtainedMarks}/${lastMock.totalMarks} (${lastPct.toInt()}%)",
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF5C6BC0), fontSize = 16.sp)
                        Text(vm.estimateRank(info.studyTargetExam, lastPct),
                            fontSize = 13.sp, color = Color(0xFFFFB300))
                    }
                }
            }
        }

        // Subject breakdown
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
                        Text(subj, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${String.format("%.1f", hrs)}h", color = Color(0xFF5C6BC0),
                            fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { hrs / maxHrs },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color(0xFF5C6BC0), trackColor = Color(0xFF222222),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }

        // Today's classes with tick
        val todayClasses = info.studyClasses.filter {
            LocalDate.now().dayOfWeek.value in it.daysOfWeek
        }
        if (todayClasses.isNotEmpty()) {
            item { Text("Today's Classes", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(todayClasses, key = { it.id }) { cls ->
                val done = vm.isClassDoneToday(cls)
                Row(Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (done) Color(0xFF0A2E0A) else Color(0xFF1A1A2E))
                    .border(1.dp, if (done) Color(0xFF2E5E2E) else Color(0xFF2A2A4A),
                        RoundedCornerShape(12.dp))
                    .padding(12.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cls.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${cls.subject} · ${cls.durationHours}h · " +
                            if (cls.startTime == "Recorded") "📼 Recorded" else cls.startTime,
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                    if (done) {
                        Text("✅ Done", fontSize = 13.sp, color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.SemiBold)
                    } else {
                        FilledTonalButton(
                            onClick = { vm.markClassComplete(cls); },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Mark Done", fontSize = 12.sp) }
                    }
                }
            }
        }

        // Last 7 days chart
        val recent = info.studyDailyLogHours.entries
            .filter { !it.key.contains("_") }
            .sortedByDescending { it.key }.take(7)
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
                                    color = Color(0xFF5C6BC0), trackColor = Color(0xFF222222),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Text("${String.format("%.1f", hrs)}h", fontSize = 11.sp, color = Color.White,
                                    modifier = Modifier.width(38.dp))
                            }
                        }
                    }
                }
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
                        Button(onClick = {
                            val obt = obtained.toIntOrNull() ?: return@Button
                            val tot = total.toIntOrNull() ?: return@Button
                            if (examName.isBlank()) return@Button
                            vm.addMockScore(MockTestScore(
                                id = UUID.randomUUID().toString(),
                                examName = examName, date = LocalDate.now().toString(),
                                totalMarks = tot, obtainedMarks = obt, notes = notes
                            ))
                            examName = ""; obtained = ""; total = ""; notes = ""
                            showAdd = false
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
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
                var confirmDelete by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(score.examName, fontWeight = FontWeight.Bold)
                                Text(score.date, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { confirmDelete = true }, Modifier.size(28.dp)) {
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
                        if (confirmDelete) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
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
                    Text("${info.studyClasses.size} scheduled", fontSize = 12.sp, color = Color.Gray)
                }
                FilledTonalButton(onClick = { showAdd = !showAdd }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Class")
                }
            }
        }

        if (showAdd) {
            item {
                val ctx = LocalContext.current
                var isRecorded by remember { mutableStateOf(false) }
                val timePicker = android.app.TimePickerDialog(ctx,
                    { _, h, m -> startTime = "%02d:%02d".format(h, m) },
                    9, 0, true)

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("New Class", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                        // Name
                        OutlinedTextField(className, { className = it },
                            label = { Text("Class name e.g. Physics Batch A") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)

                        // Subject — chips only from saved subjects
                        Text("Subject", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (info.studySubjects.isEmpty()) {
                            Text("Add subjects in ⚙️ Settings first",
                                fontSize = 12.sp, color = Color(0xFF888888))
                        } else {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                info.studySubjects.forEach { subj ->
                                    FilterChip(
                                        selected = classSubject == subj,
                                        onClick = { classSubject = subj },
                                        label = { Text(subj, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        // Live vs Recorded toggle
                        Text("Class type", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF111111))
                            .padding(4.dp)) {
                            listOf(false to "🔴 Live", true to "📼 Recorded").forEach { (rec, label) ->
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isRecorded == rec) Color(0xFF5C6BC0) else Color.Transparent)
                                        .clickable { isRecorded = rec }
                                        .padding(vertical = 10.dp),
                                    Alignment.Center
                                ) {
                                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (isRecorded == rec) Color.White else Color.Gray)
                                }
                            }
                        }

                        // Duration
                        Text("Duration", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1", "1.5", "2", "2.5", "3").forEach { h ->
                                FilterChip(selected = duration == h, onClick = { duration = h },
                                    label = { Text("${h}h", fontSize = 12.sp) })
                            }
                        }

                        // Start time — only for Live classes
                        if (!isRecorded) {
                            Text("Start time", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = startTime, onValueChange = {},
                                    label = { Text("HH:mm") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true, readOnly = true
                                )
                                FilledTonalButton(onClick = { timePicker.show() }) { Text("Pick") }
                            }
                        }

                        // Repeat days
                        Text("Repeat on", fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

                        Button(onClick = {
                            if (className.isNotBlank() && selectedDays.isNotEmpty()) {
                                vm.addClass(StudyClass(
                                    id = UUID.randomUUID().toString(),
                                    name = className,
                                    subject = classSubject,
                                    durationHours = duration.toFloatOrNull() ?: 2f,
                                    daysOfWeek = selectedDays.sorted(),
                                    startTime = if (isRecorded) "Recorded" else startTime
                                ))
                                className = ""; classSubject = ""; selectedDays = emptySet()
                                showAdd = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Add Class") }
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
                val days = cls.daysOfWeek.joinToString(" · ") {
                    listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").getOrElse(it - 1) { "?" }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(cls.name, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (cls.subject.isNotBlank())
                                Text(cls.subject, fontSize = 12.sp, color = Color.Gray)
                            Text("·", fontSize = 12.sp, color = Color.Gray)
                            Text("${cls.durationHours}h", fontSize = 12.sp, color = Color.Gray)
                            Text("·", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                if (cls.startTime == "Recorded") "📼 Recorded" else cls.startTime,
                                fontSize = 12.sp,
                                color = if (cls.startTime == "Recorded") Color(0xFF9C7BC0) else Color.Gray
                            )
                        }
                        Text(days, fontSize = 12.sp, color = Color(0xFF5C6BC0))
                    }
                }
            }
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

@Composable
private fun StudySettingsScreen(vm: StudyTrackerViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val info = vm.userRepository.userInfo
    var exam by remember { mutableStateOf(info.studyTargetExam.ifBlank { "JEE" }) }
    var targetDate by remember { mutableStateOf(info.studyTargetDate) }
    var subjectsText by remember { mutableStateOf(info.studySubjects.joinToString(", ")) }
    var dailyGoal by remember { mutableStateOf(info.dailyStudyQuotaHours.toInt().toString()) }
    var saved by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var appPickerRefresh by remember { mutableIntStateOf(0) }

    val cal = Calendar.getInstance()
    val datePicker = DatePickerDialog(ctx,
        { _, y, m, d -> targetDate = "%04d-%02d-%02d".format(y, m + 1, d) },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Study Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        HorizontalDivider()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text("Target Exam", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("JEE", "NEET", "UPSC", "Other").forEach { e ->
                    FilterChip(selected = exam == e, onClick = { exam = e }, label = { Text(e) })
                }
            }

            Text("Exam Date", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = if (targetDate.isBlank()) "Not set" else targetDate,
                    onValueChange = {}, label = { Text("Exam date") },
                    modifier = Modifier.weight(1f), singleLine = true, readOnly = true)
                FilledTonalButton(onClick = { datePicker.show() }) { Text("Pick") }
            }

            Text("Subjects", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = subjectsText, onValueChange = { subjectsText = it },
                label = { Text("e.g. Physics, Chemistry, Maths") }, modifier = Modifier.fillMaxWidth())

            Text("Daily Study Goal", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("4", "6", "8", "10", "12").forEach { h ->
                    FilterChip(selected = dailyGoal == h, onClick = { dailyGoal = h },
                        label = { Text("${h}h") })
                }
            }

            Button(onClick = {
                val subjects = subjectsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                vm.saveMeta(exam, targetDate, subjects, dailyGoal.toFloatOrNull() ?: 4f)
                saved = true
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("Save Settings", fontWeight = FontWeight.Bold) }

            if (saved) Text("✅ Saved!", color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)

            HorizontalDivider()

            // Manage Classes
            Text("📅 Manage Classes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Delete coaching classes here", fontSize = 12.sp, color = Color.Gray)

            val classes = info.studyClasses.toList()
            if (classes.isEmpty()) {
                Text("No classes added yet", color = Color(0xFF555555), fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    classes.forEach { cls ->
                        var confirmDelete by remember { mutableStateOf(false) }
                        val days = cls.daysOfWeek.joinToString(" · ") {
                            listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").getOrElse(it - 1) { "?" }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cls.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("${cls.subject} · ${cls.durationHours}h · ${cls.startTime}",
                                            fontSize = 12.sp, color = Color.Gray)
                                        Text(days, fontSize = 11.sp, color = Color(0xFF5C6BC0))
                                    }
                                    IconButton(onClick = { confirmDelete = true }, Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, null,
                                            tint = Color(0xFF884444), modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (confirmDelete) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                                        Button(
                                            onClick = { vm.removeClass(cls.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                                        ) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("📱 Study App Tracking", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Screen time from these apps is automatically logged as study hours",
                fontSize = 12.sp, color = Color.Gray)

            val trackedApps = info.studyApps.toList()
            if (trackedApps.isEmpty()) {
                Text("No apps added yet", color = Color(0xFF555555), fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                info.studyApps = info.studyApps.toMutableSet().apply { remove(pkg) }
                                vm.userRepository.saveUserInfo()
                                vm.refresh()
                                appPickerRefresh++
                            }, Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null,
                                    tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            FilledTonalButton(onClick = { showAppPicker = !showAppPicker },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showAppPicker) "Close App Picker" else "Add Study App")
            }

            if (showAppPicker) {
                val installedApps = remember(appPickerRefresh) {
                    ctx.packageManager.getInstalledApplications(0)
                        .filter { it.packageName != ctx.packageName }
                        .map { it.packageName to ctx.packageManager.getApplicationLabel(it).toString() }
                        .sortedBy { it.second }
                }
                OutlinedTextField(appSearch, { appSearch = it },
                    label = { Text("Search app") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                val filtered = installedApps.filter {
                    it.second.contains(appSearch, true) || it.first.contains(appSearch, true)
                }.take(12)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filtered.forEach { (pkg, name) ->
                        val added = pkg in info.studyApps
                        Row(Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (added) Color(0xFF0A2E0A) else Color(0xFF111111))
                            .clickable {
                                if (!added) {
                                    info.studyApps = info.studyApps.toMutableSet().apply { add(pkg) }
                                    vm.userRepository.saveUserInfo()
                                    vm.refresh()
                                    appPickerRefresh++
                                }
                            }
                            .padding(12.dp),
                            Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(name, fontSize = 13.sp)
                            if (added) Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
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
