package neth.iecal.questphone.app.screens.etc

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.utils.UsageStatsHelper
import nethical.questphone.data.ScreentimeStat
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class StudySessionSummary(
    val totalStudyMs: Long,
    val perApp: List<ScreentimeStat>,
    val blockScheduleActive: Boolean,
    val blockRemainingMs: Long
)

@HiltViewModel
class StudyTrackerViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    private val _summary = MutableStateFlow<StudySessionSummary?>(null)
    val summary = _summary.asStateFlow()

    fun load(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val helper = UsageStatsHelper(context)
            val today = LocalDate.now()
            val all = helper.getForegroundStatsByDay(today)
            val studyPkgs = userRepository.userInfo.studyApps
            val studyStats = all.filter { it.packageName in studyPkgs }
            val totalMs = studyStats.sumOf { it.totalTime }
            val quotaMs = (userRepository.userInfo.dailyStudyQuotaHours * 3_600_000).toLong()
            val remaining = (quotaMs - totalMs).coerceAtLeast(0)
            _summary.value = StudySessionSummary(
                totalStudyMs = totalMs,
                perApp = studyStats,
                blockScheduleActive = remaining > 0,
                blockRemainingMs = remaining
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTrackerScreen(
    navController: NavController,
    vm: StudyTrackerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val summary by vm.summary.collectAsState()

    LaunchedEffect(Unit) { vm.load(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Tracker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (summary == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val s = summary!!
        val quotaHours = vm.userRepository.userInfo.dailyStudyQuotaHours
        val quotaMs = (quotaHours * 3_600_000).toLong()
        val fraction = if (quotaMs > 0) (s.totalStudyMs.toFloat() / quotaMs).coerceIn(0f, 1f) else 0f

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Today's Study Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = if (fraction >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(formatStudyTime(s.totalStudyMs) + " studied", fontWeight = FontWeight.SemiBold)
                            Text("Goal: ${formatStudyTime(quotaMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        if (s.blockScheduleActive && fraction < 1f) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.outline_lock_24), null, tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        "Block schedule active · ${formatStudyTime(s.blockRemainingMs)} remaining",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else if (fraction >= 1f) {
                            Surface(
                                color = Color(0x1F4CAF50),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "✅ Daily quota reached! All app blocks lifted.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF388E3C)
                                )
                            }
                        }
                    }
                }
            }
            // Per-app breakdown
            item {
                Text("Per-App Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (s.perApp.isEmpty()) {
                item {
                    Text("No study app usage today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(s.perApp) { stat ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(stat.packageName.substringAfterLast('.'), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(formatStudyTime(stat.totalTime), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatStudyTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
