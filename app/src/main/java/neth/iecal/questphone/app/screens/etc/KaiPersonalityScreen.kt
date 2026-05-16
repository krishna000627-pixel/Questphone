package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

data class PersonalityOption(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val sampleQuote: String,
    val color: Color
)

private val PERSONALITIES = listOf(
    PersonalityOption(
        id = "friendly",
        name = "Friendly Coach",
        emoji = "😊",
        description = "Encouraging, warm, celebrates wins, gentle with failures. Default.",
        sampleQuote = "\"Great job today! You're building something amazing. Keep it up! 🌟\"",
        color = Color(0xFF4CAF50)
    ),
    PersonalityOption(
        id = "strict",
        name = "Strict Sensei",
        emoji = "🥋",
        description = "Blunt, no excuses, demands excellence. Won't sugarcoat failures.",
        sampleQuote = "\"You missed 2 quests. Unacceptable. Tomorrow: zero tolerance. Prepare.\"",
        color = Color(0xFFE53935)
    ),
    PersonalityOption(
        id = "rival",
        name = "Competitive Rival",
        emoji = "😤",
        description = "Constantly compares you to your shadow rival. Trash-talks and taunts.",
        sampleQuote = "\"Your shadow rival already finished 4 quests. You've done 1. Embarrassing.\"",
        color = Color(0xFFFF9800)
    ),
    PersonalityOption(
        id = "philosopher",
        name = "Stoic Philosopher",
        emoji = "🧘",
        description = "Calm, reflective, quotes Marcus Aurelius. Views every failure as a lesson.",
        sampleQuote = "\"You didn't complete the quest. Ask: what in your control? Only the next action.\"",
        color = Color(0xFF9C27B0)
    ),
    PersonalityOption(
        id = "anime",
        name = "Anime Mentor",
        emoji = "⚡",
        description = "Dramatic, believes in you fiercely, treats every quest like a battle arc.",
        sampleQuote = "\"This is your moment. Your power is limitless — SURPASS YOUR LIMITS!!! ⚡\"",
        color = Color(0xFF00BCD4)
    ),
)

@HiltViewModel
class KaiPersonalityViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    var selected by mutableStateOf(userRepository.userInfo.kaiPersonality)

    fun select(id: String) {
        selected = id
        userRepository.userInfo.kaiPersonality = id
        userRepository.saveUserInfo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaiPersonalityScreen(navController: NavController, vm: KaiPersonalityViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kai Personality", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Choose Kai's communication style. This shapes how he delivers messages, reacts to failures, and motivates you. You can change anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(PERSONALITIES.size) { idx ->
                val p = PERSONALITIES[idx]
                val isSelected = vm.selected == p.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.border(2.dp, p.color, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.select(p.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) p.color.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween, Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(p.emoji, fontSize = 28.sp)
                                Column {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(p.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            RadioButton(selected = isSelected, onClick = { vm.select(p.id) })
                        }
                        // Sample quote
                        Surface(
                            color = p.color.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                p.sampleQuote,
                                Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = p.color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (isSelected) {
                            Text("✓ Active",
                                color = p.color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
