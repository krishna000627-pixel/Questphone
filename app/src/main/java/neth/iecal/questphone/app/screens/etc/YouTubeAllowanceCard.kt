package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neth.iecal.questphone.core.youtube.YouTubeAllowanceManager

/**
 * A compact card showing YouTube allowance status — embed on HomeScreen or ScreenTime page.
 * Only visible when youtubeAllowanceEnabled is true in UserInfo.
 */
@Composable
fun YouTubeAllowanceCard(
    studyPackages: Set<String>,
    ratio: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state = remember(studyPackages, ratio) {
        YouTubeAllowanceManager.getState(context, studyPackages, ratio)
    }

    val fraction = if (state.earnedMinutes > 0)
        (state.remainingMinutes.toFloat() / state.earnedMinutes.toFloat()).coerceIn(0f, 1f)
    else 0f

    val barColor = when {
        fraction > 0.5f -> Color(0xFF4CAF50)
        fraction > 0.2f -> Color(0xFFFF9800)
        else            -> Color(0xFFE53935)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "▶ YouTube Allowance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${state.remainingMinutes}m left",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Earned: ${state.earnedMinutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Used: ${state.usedMinutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Study ${(1f / ratio).toInt()}h → earn 1h YouTube",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
