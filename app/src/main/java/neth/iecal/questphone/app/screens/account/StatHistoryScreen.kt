package neth.iecal.questphone.app.screens.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

data class StatSeries(val name: String, val color: Color, val values: List<Pair<String, Int>>)

@HiltViewModel
class StatHistoryViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _series = MutableStateFlow<List<StatSeries>>(emptyList())
    val series = _series.asStateFlow()

    init {
        viewModelScope.launch {
            val sp = userRepository.userInfo.statPoints
            val statColors = listOf(
                Color(0xFFEF5350), Color(0xFF9C27B0),
                Color(0xFF00BCD4), Color(0xFF4CAF50)
            )
            val statNames = listOf(sp.name1, sp.name2, sp.name3, sp.name4)

            // Build growth chart from quest completion history.
            // Each completed quest has statReward1-4. We look up quests completed
            // in the last 30 days and accumulate rewards to show cumulative growth.
            val allQuests = questRepository.getAllQuests().first()
            val today = java.time.LocalDate.now()

            // Build 30 days of labels
            val dayLabels = (29 downTo 0).map { d ->
                val date = today.minusDays(d.toLong())
                "${date.monthValue}/${date.dayOfMonth}" to date.toString()
            }

            val cumulatives = IntArray(4) { 0 }
            val pointsPerStat = Array(4) { mutableListOf<Pair<String, Int>>() }

            dayLabels.forEach { (label, dateStr) ->
                allQuests.filter { it.last_completed_on == dateStr }.forEach { q ->
                    cumulatives[0] += q.statReward1
                    cumulatives[1] += q.statReward2
                    cumulatives[2] += q.statReward3
                    cumulatives[3] += q.statReward4
                }
                for (i in 0..3) pointsPerStat[i].add(label to cumulatives[i])
            }

            _series.value = (0..3).map { i ->
                StatSeries(
                    name = statNames[i],
                    color = statColors[i],
                    values = pointsPerStat[i].takeLast(30)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatHistoryScreen(
    navController: NavController,
    vm: StatHistoryViewModel = hiltViewModel()
) {
    val series by vm.series.collectAsState()
    var selectedStat by remember { mutableStateOf(0) }
    val sp = vm.userRepository.userInfo.statPoints
    val statNames = listOf(sp.name1, sp.name2, sp.name3, sp.name4)
    val statColors = listOf(Color(0xFFEF5350), Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFF4CAF50))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stat History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Stat selector tabs — LazyRow so long names don't wrap
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    itemsIndexed(statNames) { i, name ->
                        FilterChip(
                            selected = selectedStat == i,
                            onClick = { selectedStat = i },
                            label = { Text(name, fontSize = 12.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = statColors[i].copy(alpha = 0.2f),
                                selectedLabelColor = statColors[i]
                            )
                        )
                    }
                }
            }

            // Chart card
            item {
                val currentSeries = series.getOrNull(selectedStat)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${statNames.getOrElse(selectedStat) { "Stat" }} — 30-Day Growth",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        val hasAnyData = currentSeries != null && currentSeries.values.any { it.second > 0 }
                        if (!hasAnyData) {
                            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📈", style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.height(8.dp))
                                    Text("No data yet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("Complete quests with ${statNames.getOrElse(selectedStat) { "stat" }} rewards to build history.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        } else {
                            StatLineChart(
                                points = currentSeries.values,
                                color = statColors.getOrElse(selectedStat) { MaterialTheme.colorScheme.primary },
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                            )
                            // X-axis labels (first, mid, last)
                            val pts = currentSeries.values
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(pts.firstOrNull()?.first ?: "", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(pts.getOrNull(pts.size / 2)?.first ?: "", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(pts.lastOrNull()?.first ?: "", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Current values summary
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Current Stats", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        listOf(
                            sp.name1 to sp.value1, sp.name2 to sp.value2,
                            sp.name3 to sp.value3, sp.name4 to sp.value4
                        ).forEachIndexed { i, (name, value) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = statColors[i].copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(name, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 12.sp, color = statColors[i],
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text("$value pts", fontWeight = FontWeight.Bold,
                                    color = statColors[i])
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatLineChart(
    points: List<Pair<String, Int>>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return
    val maxVal = points.maxOf { it.second }.coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1).coerceAtLeast(1)
        val pts = points.mapIndexed { i, (_, v) ->
            Offset(i * stepX, h - (v.toFloat() / maxVal) * h * 0.9f)
        }
        // Fill
        val fill = Path().apply {
            moveTo(pts.first().x, h)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, h)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.12f))
        // Line
        val line = Path().apply {
            pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
        }
        drawPath(line, color, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEach { drawCircle(color, 5f, it) }
    }
}
