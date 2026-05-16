package neth.iecal.questphone.app.screens.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nethical.questphone.data.UserInfo
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Compact status strip shown above the side panel icons on HomeScreen.
 * Shows boss HP, rival status, and today's score at a glance.
 * Entirely read from UserInfo — no network calls.
 */
@Composable
fun HomeStatusBar(
    userInfo: UserInfo,
    onTapScore: () -> Unit,
    onTapBoss: () -> Unit,
    onTapRival: () -> Unit
) {
    val score = userInfo.lastProductivityScore
    val grade = when {
        score >= 90 -> "S"; score >= 80 -> "A"; score >= 65 -> "B"
        score >= 50 -> "C"; score >= 35 -> "D"; else -> "?"
    }
    val gradeColor = when (grade) {
        "S" -> Color(0xFFFFD700); "A" -> Color(0xFF4CAF50); "B" -> Color(0xFF2196F3)
        "C" -> Color(0xFFFF9800); "D" -> Color(0xFFFF5722); else -> Color(0xFF666666)
    }

    // Boss
    val weekNum = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val year = LocalDate.now().get(IsoFields.WEEK_BASED_YEAR)
    val thisWeek = "$year-W$weekNum"
    val bossDefeated = userInfo.lastBossBattleWeek == thisWeek
    val bossEmojis = listOf("🐉","👁","🦥","💀","🦉")
    val bossEmoji = bossEmojis[weekNum % bossEmojis.size]

    // Rival
    val rivalWinning = userInfo.rivalStreak > userInfo.streak.currentStreak

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0D0D0D))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Score chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(gradeColor.copy(alpha = 0.15f))
                .clickable(onClick = onTapScore)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(grade, fontSize = 12.sp, fontWeight = FontWeight.Black, color = gradeColor)
            Text("$score", fontSize = 11.sp, color = gradeColor)
        }

        // Boss chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (bossDefeated) Color(0x224CAF50) else Color(0x22E53935))
                .clickable(onClick = onTapBoss)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(bossEmoji, fontSize = 11.sp)
            Text(if (bossDefeated) "✓" else "!", fontSize = 11.sp,
                color = if (bossDefeated) Color(0xFF4CAF50) else Color(0xFFE53935),
                fontWeight = FontWeight.Bold)
        }

        // Rival chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (!rivalWinning) Color(0x224CAF50) else Color(0x22E53935))
                .clickable(onClick = onTapRival)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("👤", fontSize = 11.sp)
            Text(if (!rivalWinning) "W" else "L", fontSize = 11.sp,
                color = if (!rivalWinning) Color(0xFF4CAF50) else Color(0xFFE53935),
                fontWeight = FontWeight.Bold)
        }
    }
}
