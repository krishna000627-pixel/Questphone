package neth.iecal.questphone.app.screens.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Predefined categories + colors. "Custom" uses a user-supplied hex. */
val QUEST_CATEGORIES = listOf(
    "Study"    to 0xFF2196F3L,
    "Health"   to 0xFF4CAF50L,
    "Personal" to 0xFFFF9800L,
    "Work"     to 0xFF9C27B0L,
    "Other"    to 0xFF607D8BL,
)

@Composable
fun QuestCategoryBadge(
    category: String,
    colorArgb: Long,
    modifier: Modifier = Modifier
) {
    if (category.isBlank()) return
    val bg = Color(colorArgb)
    val isDark = (colorArgb shr 16 and 0xFF) * 0.299 +
            (colorArgb shr 8 and 0xFF) * 0.587 +
            (colorArgb and 0xFF) * 0.114 < 128
    val textColor = if (isDark) Color.White else Color.Black

    Row(
        modifier = modifier
            .background(bg.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(bg, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = category,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = bg,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun QuestStreakBadge(streak: Int, modifier: Modifier = Modifier) {
    if (streak <= 0) return
    val color = when {
        streak >= 30 -> Color(0xFFFF6F00)
        streak >= 7  -> Color(0xFFFF9800)
        else         -> Color(0xFFFFC107)
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "🔥$streak",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun OverdueBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0x1FE53935), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "⚠ Overdue",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE53935)
        )
    }
}
