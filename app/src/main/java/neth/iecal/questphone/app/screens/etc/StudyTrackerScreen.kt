package neth.iecal.questphone.app.screens.etc

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.MockTestScore
import nethical.questphone.data.StudyClass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ── ViewModel ────────────────────────────────────────────────────────────────

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
        // Streak logic
        val yesterday = LocalDate.now().minusDays(1).toString()
        info.studyStreak = when (info.studyLastLogDate) {
            today     -> info.studyStreak   // already logged today
            yesterday -> info.studyStreak + 1
            else      -> 1
        }
        info.studyLastLogDate = today
        // Coin reward: 5 coins per hour studied
        val coinsEarned = (hours * 5).toInt().coerceAtLeast(1)
        userRepository.addCoins(coinsEarned, "Study session: $subject")
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

    fun saveMeta(exam: String, targetDate: String, subjects: List<String>) {
        userRepository.userInfo.studyTargetExam = exam
        userRepository.userInfo.studyTargetDate = targetDate
        userRepository.userInfo.studySubjects = subjects.toMutableList()
        userRepository.saveUserInfo()
        refresh()
    }

    // Rank estimator: very rough based on mock score %
    fun estimateRank(exam: String, pct: Float): String {
        return when (exam.uppercase()) {
            "JEE"  -> when {
                pct >= 99f -> "Top 500 (IIT Bombay/Delhi likely)"
                pct >= 97f -> "Top 2,000 (Good IIT)"
                pct >= 93f -> "Top 10,000 (NIT+)"
                pct >= 85f -> "Top 30,000 (NIT)"
                else       -> "Below 30,000 — keep pushing!"
            }
            "NEET" -> when {
                pct >= 99f -> "Top 100 (AIIMS Delhi likely)"
                pct >= 95f -> "Top 1,000 (AIIMS)"
                pct >= 88f -> "Top 10,000 (Govt. Medical)"
                pct >= 75f -> "Top 50,000 (State college)"
                else       -> "Below 50,000 — focus on NCERT"
            }
            "UPSC" -> when {
                pct >= 75f -> "High chance of clearing Mains"
                pct >= 60f -> "Prelims likely, Mains needs work"
                pct >= 50f -> "Prelims borderline"
                else       -> "Focus on basics first"
            }
            else -> if (pct >= 80f) "Looking strong!" else "Needs improvement"
        }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTrackerScreen(
    navController: NavController,
    vm: StudyTrackerViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val refreshKey by vm.refreshKey.collectAsState()
    val info = vm.userRepository.userInfo

    // Tabs
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dashboard", "Log", "Mocks", "Classes")

    // Setup state
    var showSetup by remember { mutableStateOf(info.studyTargetExam.isBlank()) }

    if (showSetup) {
        StudySetupScreen(vm) { showSetup = false }
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Study Tracker", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (info.studyTargetExam.isNotBlank())
                                Text(info.studyTargetExam, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { showSetup = true }) { Text("Setup") }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { i, t ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                            text = { Text(t, fontSize = 12.sp) })
                    }
                }
            }
        }
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
    val info = vm.userRepository.userInfo
    val today = LocalDate.now().toString()
    val todayHours = info.studyDailyLogHours[today] ?: 0f
    val quotaHours = info.dailyStudyQuotaHours
    val fraction = (todayHours / quotaHours).coerceIn(0f, 1f)

    // Days until exam
    val daysLeft = remember(refreshKey) {
        runCatching {
            ChronoUnit.DAYS.between(LocalDate.now(),
                LocalDate.parse(info.studyTargetDate))
        }.getOrNull()
    }

    // Last mock score
    val lastMock = info.studyMockScores.firstOrNull()
    val lastPct = lastMock?.let { it.obtainedMarks * 100f / it.totalMarks.coerceAtLeast(1) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Countdown card
        item {
            StatCard(
                emoji = "🎯",
                title = if (daysLeft != null) "$daysLeft days to ${info.studyTargetExam}" else "Set your exam date",
                subtitle = if (info.studyTargetDate.isNotBlank()) info.studyTargetDate else "Tap Setup to set date",
                accentColor = Color(0xFF5C6BC0)
            )
        }

        // Today's progress
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Today's Progress", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = if (fraction >= 1f) Color(0xFF4CAF50) else Color(0xFF5C6BC0),
                        trackColor = Color(0xFF2A2A2A),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("${todayHours}h studied", fontWeight = FontWeight.SemiBold)
                        Text("Goal: ${quotaHours}h", color = Color.Gray, fontSize = 13.sp)
                    }
                    if (fraction >= 1f) {
                        Text("✅ Daily goal hit! +${(todayHours * 5).toInt()} coins earned",
                            color = Color(0xFF4CAF50), fontSize = 13.sp)
                    }
                }
            }
        }

        // Streak + coins
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStatCard("🔥", "${info.studyStreak}", "Day streak", Modifier.weight(1f))
                MiniStatCard("🪙", "${info.coins}", "Total coins", Modifier.weight(1f))
            }
        }

        // Rank estimator from last mock
        if (lastMock != null && lastPct != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📊 Rank Estimator", fontWeight = FontWeight.Bold)
                        Text("Based on last mock: ${lastMock.examName}",
                            fontSize = 12.sp, color = Color.Gray)
                        Text("Score: ${lastMock.obtainedMarks}/${lastMock.totalMarks} (${lastPct.toInt()}%)",
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF5C6BC0))
                        Text(vm.estimateRank(info.studyTargetExam, lastPct),
                            fontSize = 13.sp, color = Color(0xFFFFB300))
                    }
                }
            }
        }

        // Subject-wise total
        if (info.studySubjectHours.isNotEmpty()) {
            item { Text("Subject Breakdown", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(info.studySubjectHours.entries.sortedByDescending { it.value }) { (subj, hrs) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111111))
                        .padding(12.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Text(subj, fontWeight = FontWeight.Medium)
                    Text("${hrs}h", color = Color(0xFF5C6BC0), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Today's classes
        val todayClasses = info.studyClasses.filter {
            val dow = LocalDate.now().dayOfWeek.value  // 1=Mon
            dow in it.daysOfWeek
        }
        if (todayClasses.isNotEmpty()) {
            item { Text("Today's Classes", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            items(todayClasses) { cls ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1A2E))
                        .padding(12.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Column {
                        Text(cls.name, fontWeight = FontWeight.Medium)
                        Text("${cls.subject} · ${cls.durationHours}h · ${cls.startTime}",
                            fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("📚", fontSize = 20.sp)
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

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Log Study Session", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text("Earn 5 coins per hour logged 🪙", fontSize = 13.sp, color = Color.Gray)
        }

        // Subject selector
        item {
            Text("Subject", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            if (info.studySubjects.isEmpty()) {
                Text("Add subjects in Setup first", color = Color.Gray, fontSize = 13.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.studySubjects.forEach { subj ->
                        FilterChip(
                            selected = selectedSubject == subj,
                            onClick = { selectedSubject = subj },
                            label = { Text(subj, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // Hours input
        item {
            Text("Hours studied", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("0.5", "1", "1.5", "2", "2.5", "3").forEach { h ->
                    FilterChip(
                        selected = hoursInput == h,
                        onClick = { hoursInput = h },
                        label = { Text("${h}h", fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = hoursInput,
                onValueChange = { hoursInput = it },
                label = { Text("Or enter custom hours") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
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
                shape = RoundedCornerShape(12.dp)
            ) { Text("Log Session ✓", fontWeight = FontWeight.Bold) }

            if (showSuccess) {
                Spacer(Modifier.height(8.dp))
                Text("✅ Session logged! Coins awarded.", color = Color(0xFF4CAF50), fontSize = 13.sp)
            }
        }

        // Last 7 days log
        val recent = info.studyDailyLogHours.entries
            .sortedByDescending { it.key }.take(7)
        if (recent.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Last 7 Days", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(recent) { (date, hrs) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111111))
                        .padding(12.dp),
                    Arrangement.SpaceBetween
                ) {
                    Text(date, color = Color.Gray, fontSize = 13.sp)
                    Text("${hrs}h", fontWeight = FontWeight.Bold, color = Color(0xFF5C6BC0))
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
    var subject by remember { mutableStateOf("Full") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Mock Tests", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { showAdd = !showAdd }) {
                    Icon(Icons.Default.Add, "Add mock")
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
                        OutlinedTextField(subject, { subject = it },
                            label = { Text("Subject (or Full)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(notes, { notes = it },
                            label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                val obt = obtained.toIntOrNull() ?: return@Button
                                val tot = total.toIntOrNull() ?: return@Button
                                vm.addMockScore(MockTestScore(
                                    id = UUID.randomUUID().toString(),
                                    examName = examName,
                                    date = LocalDate.now().toString(),
                                    totalMarks = tot,
                                    obtainedMarks = obt,
                                    subject = subject,
                                    notes = notes
                                ))
                                examName = ""; obtained = ""; total = ""; notes = ""
                                showAdd = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save") }
                    }
                }
            }
        }

        if (info.studyMockScores.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📝", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No mock scores yet", color = Color.Gray)
                    }
                }
            }
        } else {
            items(info.studyMockScores) { score ->
                val pct = score.obtainedMarks * 100f / score.totalMarks.coerceAtLeast(1)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(score.examName, fontWeight = FontWeight.Bold)
                            Text(score.date, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("${score.obtainedMarks}/${score.totalMarks} · ${pct.toInt()}%",
                            color = when {
                                pct >= 80f -> Color(0xFF4CAF50)
                                pct >= 60f -> Color(0xFFFFB300)
                                else -> Color(0xFFF44336)
                            }, fontWeight = FontWeight.SemiBold)
                        Text(vm.estimateRank(info.studyTargetExam, pct),
                            fontSize = 12.sp, color = Color(0xFFAAAAAA))
                        if (score.notes.isNotBlank())
                            Text(score.notes, fontSize = 12.sp, color = Color.Gray)
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

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Classes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { showAdd = !showAdd }) {
                    Icon(Icons.Default.Add, "Add class")
                }
            }
            Text("Track your coaching/school classes", fontSize = 13.sp, color = Color.Gray)
        }

        if (showAdd) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add Class", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(className, { className = it },
                            label = { Text("Class name (e.g. Physics Batch A)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(classSubject, { classSubject = it },
                            label = { Text("Subject") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(duration, { duration = it },
                                label = { Text("Duration (hrs)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(startTime, { startTime = it },
                                label = { Text("Start (HH:mm)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Text("Repeat on:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayNames.forEachIndexed { i, d ->
                                val day = i + 1 // 1=Mon
                                val sel = day in selectedDays
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(if (sel) Color(0xFF5C6BC0) else Color(0xFF1A1A1A))
                                        .border(1.dp, if (sel) Color(0xFF5C6BC0) else Color(0xFF333333), CircleShape)
                                        .clickable {
                                            selectedDays = if (sel) selectedDays - day else selectedDays + day
                                        },
                                    Alignment.Center
                                ) { Text(d, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Button(
                            onClick = {
                                if (className.isNotBlank() && selectedDays.isNotEmpty()) {
                                    vm.addClass(StudyClass(
                                        id = UUID.randomUUID().toString(),
                                        name = className,
                                        subject = classSubject,
                                        durationHours = duration.toFloatOrNull() ?: 2f,
                                        daysOfWeek = selectedDays.sorted(),
                                        startTime = startTime
                                    ))
                                    className = ""; classSubject = ""; selectedDays = emptySet()
                                    showAdd = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add Class") }
                    }
                }
            }
        }

        if (info.studyClasses.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📅", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No classes added yet", color = Color.Gray)
                    }
                }
            }
        } else {
            items(info.studyClasses) { cls ->
                val days = cls.daysOfWeek.joinToString(" ") { d ->
                    listOf("M","T","W","T","F","S","S").getOrElse(d - 1) { "?" }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cls.name, fontWeight = FontWeight.SemiBold)
                        Text("${cls.subject} · ${cls.durationHours}h · ${cls.startTime}",
                            fontSize = 12.sp, color = Color.Gray)
                        Text(days, fontSize = 12.sp, color = Color(0xFF5C6BC0))
                    }
                    Text("✕", color = Color(0xFF555555), fontSize = 18.sp,
                        modifier = Modifier.clickable { vm.removeClass(cls.id) }.padding(8.dp))
                }
            }
        }
    }
}

// ── Setup screen ──────────────────────────────────────────────────────────────

@Composable
private fun StudySetupScreen(vm: StudyTrackerViewModel, onDone: () -> Unit) {
    val info = vm.userRepository.userInfo
    var exam by remember { mutableStateOf(info.studyTargetExam) }
    var targetDate by remember { mutableStateOf(info.studyTargetDate) }
    var subjectsText by remember {
        mutableStateOf(info.studySubjects.joinToString(", "))
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Setup Study Tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Configure your exam prep", fontSize = 14.sp, color = Color.Gray)
        }
        item {
            Text("Target Exam", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("JEE", "NEET", "UPSC", "Other").forEach { e ->
                    FilterChip(selected = exam == e, onClick = { exam = e },
                        label = { Text(e) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = targetDate,
                onValueChange = { targetDate = it },
                label = { Text("Exam Date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("e.g. 2026-01-15") }
            )
        }
        item {
            OutlinedTextField(
                value = subjectsText,
                onValueChange = { subjectsText = it },
                label = { Text("Subjects (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Physics, Chemistry, Maths") }
            )
        }
        item {
            Button(
                onClick = {
                    val subjects = subjectsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    vm.saveMeta(exam, targetDate, subjects)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = exam.isNotBlank()
            ) { Text("Save & Start Tracking", fontWeight = FontWeight.Bold) }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(emoji: String, title: String, subtitle: String, accentColor: Color) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.15f)),
                Alignment.Center) { Text(emoji, fontSize = 22.sp) }
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun MiniStatCard(emoji: String, value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 24.sp)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

private fun formatStudyTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
