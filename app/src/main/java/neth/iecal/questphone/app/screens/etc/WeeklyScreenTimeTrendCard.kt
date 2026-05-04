package neth.iecal.questphone.app.screens.etc

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import neth.iecal.questphone.core.utils.UsageStatsHelper
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 7-day line chart of total daily screen time — embed in ScreentimeStatsScreen.
 */
@Composable
fun WeeklyScreenTimeTrendCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var weekData by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    LaunchedEffect(Unit) {
        weekData = withContext(Dispatchers.IO) {
            val helper = UsageStatsHelper(context)
            val today = LocalDate.now()
            (6 downTo 0).map { daysBack ->
                val date = today.minusDays(daysBack.toLong())
                val label = date.dayOfWeek.name.take(3)
                    .replaceFirstChar { it.uppercase() }
                val total = helper.getForegroundStatsByDay(date).sumOf { it.totalTime }
                label to total
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("7-Day Screen Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (weekData.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            } else {
                val maxMs = weekData.maxOf { it.second }.coerceAtLeast(1L)
                val lineColor = MaterialTheme.colorScheme.primary
                val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val step = w / (weekData.size - 1).coerceAtLeast(1)
                    val pts = weekData.mapIndexed { i, (_, ms) ->
                        Offset(i * step, h - (ms.toFloat() / maxMs) * h * 0.85f)
                    }
                    // Fill area under curve
                    val fillPath = Path().apply {
                        moveTo(pts.first().x, h)
                        pts.forEach { lineTo(it.x, it.y) }
                        lineTo(pts.last().x, h)
                        close()
                    }
                    drawPath(fillPath, lineColor.copy(alpha = 0.12f))
                    // Line
                    val linePath = Path().apply {
                        pts.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }
                    drawPath(linePath, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Dots
                    pts.forEach { drawCircle(lineColor, 5f, it) }
                }
                // Day labels
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    weekData.forEach { (label, ms) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatHm(ms),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun formatHm(ms: Long): String {
    if (ms <= 0L) return "0m"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h${m}m" else "${m}m"
}
