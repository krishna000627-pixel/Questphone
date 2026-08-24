package neth.iecal.questphone.app.screens.etc

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Composable
fun WeeklyScreenTimeTrendCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var weekData by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        weekData = withContext(Dispatchers.IO) {
            getDailyScreenTime(context, days = 7)
        }
        isLoading = false
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "7-Day Screen Time",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            when {
                isLoading -> {
                    Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
                weekData.all { it.second == 0L } -> {
                    Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                        Text(
                            "No screen time data available.\nGrant Usage Access in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    val maxMs = weekData.maxOf { it.second }.coerceAtLeast(1L)
                    val lineColor = MaterialTheme.colorScheme.primary

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val step = w / (weekData.size - 1).coerceAtLeast(1)
                        val pts = weekData.mapIndexed { i, (_, ms) ->
                            Offset(
                                x = i * step,
                                y = h - (ms.toFloat() / maxMs) * h * 0.85f
                            )
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
                        drawPath(
                            linePath, lineColor,
                            style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // Dots — highlight today differently
                        pts.forEachIndexed { i, pt ->
                            val isToday = i == pts.lastIndex
                            drawCircle(lineColor, if (isToday) 7f else 5f, pt)
                            if (isToday) drawCircle(lineColor.copy(alpha = 0.25f), 14f, pt)
                        }
                    }

                    // Labels row
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        weekData.forEach { (label, ms) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    formatHm(ms),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Uses UsageStatsManager.queryUsageStats(INTERVAL_DAILY) which is the correct
 * API for historical per-day totals. queryEvents() is unreliable for past days
 * because Android only retains raw events for ~3 days.
 */
private fun getDailyScreenTime(context: Context, days: Int): List<Pair<String, Long>> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    // Query the full range once — much more efficient than N separate queries
    val rangeStart = today.minusDays(days.toLong() - 1)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val rangeEnd = today.plusDays(1)
        .atStartOfDay(zone).toInstant().toEpochMilli()

    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, rangeStart, rangeEnd)

    // Bucket each stat entry into its calendar day
    val dailyTotals = mutableMapOf<LocalDate, Long>()
    stats?.forEach { stat ->
        val date = java.time.Instant.ofEpochMilli(stat.lastTimeUsed)
            .atZone(zone).toLocalDate()
        if (!date.isBefore(rangeStart.toLocalDate(zone)) && !date.isAfter(today)) {
            dailyTotals[date] =
                (dailyTotals[date] ?: 0L) + stat.totalTimeInForeground
        }
    }

    // Build ordered list oldest → today
    return (days - 1 downTo 0).map { daysBack ->
        val date = today.minusDays(daysBack.toLong())
        val label = date.dayOfWeek.name.take(3).replaceFirstChar { it.uppercase() }
        label to (dailyTotals[date] ?: 0L)
    }
}

private fun Long.toLocalDate(zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private fun formatHm(ms: Long): String {
    if (ms <= 0L) return "0m"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) "${h}h${m}m" else "${m}m"
}
