package neth.iecal.questphone.app.screens.onboard.subscreens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class QuickQuest(
    val title: String,
    val emoji: String,
    val timeWindow: String,
    val stat: String
)

private val QUICK_QUESTS = listOf(
    QuickQuest("Morning Study Session", "📚", "07:00 – 09:00", "Intelligence"),
    QuickQuest("Evening Workout", "💪", "18:00 – 19:00", "Strength"),
    QuickQuest("Deep Work Block", "🎯", "10:00 – 12:00", "Focus"),
    QuickQuest("Daily Journaling", "📝", "21:00 – 21:30", "Discipline"),
    QuickQuest("Reading 30 mins", "📖", "22:00 – 22:30", "Intelligence"),
    QuickQuest("Meditation", "🧘", "06:30 – 07:00", "Focus"),
)

/**
 * Onboarding step: pick one starter quest template.
 * Returns the selected QuickQuest via [selected] state.
 */
@Composable
fun FirstQuestScreen(
    selected: MutableState<QuickQuest?>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("🗡️", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Your First Quest",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick one habit to start with. You can add more anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        QUICK_QUESTS.forEach { quest ->
            val isSelected = selected.value == quest
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                onClick = { selected.value = if (isSelected) null else quest }
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    Arrangement.spacedBy(12.dp), Alignment.CenterVertically
                ) {
                    Text(quest.emoji, fontSize = 28.sp)
                    Column(Modifier.weight(1f)) {
                        Text(quest.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "${quest.timeWindow}  •  +${quest.stat}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text("✓", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "You can skip this and create quests manually.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}
