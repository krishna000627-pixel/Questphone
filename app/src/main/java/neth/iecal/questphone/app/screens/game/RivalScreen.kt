package neth.iecal.questphone.app.screens.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.core.core.utils.getCurrentDate
import javax.inject.Inject
import kotlin.random.Random

private val RIVAL_NAMES = listOf(
    "Shadow Krishna", "Dark Mirror", "The Other You",
    "Ghost Rival", "Phantom", "Alt Self", "The Shadow"
)

@HiltViewModel
class RivalViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _rivalName = MutableStateFlow("")
    val rivalName = _rivalName.asStateFlow()

    private val _rivalStreak = MutableStateFlow(0)
    val rivalStreak = _rivalStreak.asStateFlow()

    private val _rivalLevel = MutableStateFlow(1)
    val rivalLevel = _rivalLevel.asStateFlow()

    private val _rivalQuestsToday = MutableStateFlow(0)
    val rivalQuestsToday = _rivalQuestsToday.asStateFlow()

    private val _yourQuestsToday = MutableStateFlow(0)
    val yourQuestsToday = _yourQuestsToday.asStateFlow()

    private val _totalQuests = MutableStateFlow(0)
    val totalQuests = _totalQuests.asStateFlow()

    private val _isWinning = MutableStateFlow(false)
    val isWinning = _isWinning.asStateFlow()

    private val _taunt = MutableStateFlow("")
    val taunt = _taunt.asStateFlow()

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val u = userRepository.userInfo
        val today = getCurrentDate()

        // Initialize rival if first time
        if (u.rivalName == "Shadow") {
            u.rivalName = RIVAL_NAMES.random()
        }

        // Update rival progress daily
        if (u.lastRivalUpdate != today) {
            val allQuests = questRepository.getAllQuests().first()
            val total = allQuests.count { !it.is_destroyed }
            // Rival completes 60–90% of quests — just slightly worse than a diligent user
            val rivalCompletion = Random.nextFloat() * 0.3f + 0.6f
            val rivalCompleted = (total * rivalCompletion).toInt()

            val yourCompleted = allQuests.count { it.last_completed_on == today && !it.is_destroyed }
            val youWon = yourCompleted > rivalCompleted

            if (youWon) {
                u.rivalStreak = maxOf(0, u.rivalStreak - 1)
            } else {
                u.rivalStreak++
                u.rivalLevel = (u.rivalStreak / 5) + 1
            }
            u.lastRivalUpdate = today
            userRepository.saveUserInfo()
        }

        val allQuests = questRepository.getAllQuests().first()
        val total = allQuests.count { !it.is_destroyed }
        val yourCompleted = allQuests.count { it.last_completed_on == today && !it.is_destroyed }
        val seed = today.hashCode().toLong()
        val rivalCompleted = ((total * (0.6f + (seed % 100) / 333f)).toInt()).coerceIn(0, total)

        _rivalName.value = u.rivalName
        _rivalStreak.value = u.rivalStreak
        _rivalLevel.value = u.rivalLevel
        _rivalQuestsToday.value = rivalCompleted
        _yourQuestsToday.value = yourCompleted
        _totalQuests.value = total
        _isWinning.value = yourCompleted >= rivalCompleted

        _taunt.value = when {
            yourCompleted > rivalCompleted -> "${u.rivalName} is falling behind... keep it up! 🔥"
            yourCompleted == rivalCompleted -> "${u.rivalName} is matching you perfectly. Who blinks first?"
            else -> "${u.rivalName} is ahead by ${rivalCompleted - yourCompleted} quests today! 💀"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RivalScreen(navController: NavController, vm: RivalViewModel = hiltViewModel()) {
    val rivalName by vm.rivalName.collectAsState()
    val rivalStreak by vm.rivalStreak.collectAsState()
    val rivalLevel by vm.rivalLevel.collectAsState()
    val rivalQ by vm.rivalQuestsToday.collectAsState()
    val yourQ by vm.yourQuestsToday.collectAsState()
    val total by vm.totalQuests.collectAsState()
    val winning by vm.isWinning.collectAsState()
    val taunt by vm.taunt.collectAsState()
    val u = vm.userRepository.userInfo

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        1f, 1.08f, label = "s",
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Rival", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // VS card
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (winning) Color(0xFF0D2010) else Color(0xFF200D0D)
                    )
                ) {
                    Row(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        Arrangement.SpaceEvenly, Alignment.CenterVertically
                    ) {
                        // You
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚔️", fontSize = 36.sp)
                            Text(u.getFirstName().ifBlank { "You" },
                                fontWeight = FontWeight.Black, color = Color.White)
                            Text("Lv.${u.level}", color = Color(0xFF81C784), fontSize = 12.sp)
                            Text("Streak: ${u.streak.currentStreak}🔥",
                                color = Color(0xFFFFCC02), fontSize = 12.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("VS", fontSize = 24.sp, fontWeight = FontWeight.Black,
                                color = if (winning) Color(0xFF4CAF50) else Color(0xFFE53935))
                        }

                        // Rival
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👤", fontSize = 36.sp)
                            Text(rivalName, fontWeight = FontWeight.Black, color = Color(0xFFCF6679))
                            Text("Lv.$rivalLevel", color = Color(0xFFEF9A9A), fontSize = 12.sp)
                            Text("Streak: $rivalStreak🔥",
                                color = Color(0xFFFFCC02), fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                // Today's battle
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Today's Battle", fontWeight = FontWeight.Bold)

                        // Your progress
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("You", fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                                Text("$yourQ / $total quests")
                            }
                            LinearProgressIndicator(
                                progress = { if (total > 0) yourQ.toFloat() / total else 0f },
                                Modifier.fillMaxWidth().height(8.dp),
                                color = Color(0xFF4CAF50),
                                strokeCap = StrokeCap.Round
                            )
                        }

                        // Rival progress
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(rivalName, fontWeight = FontWeight.SemiBold, color = Color(0xFFCF6679))
                                Text("$rivalQ / $total quests")
                            }
                            LinearProgressIndicator(
                                progress = { if (total > 0) rivalQ.toFloat() / total else 0f },
                                Modifier.fillMaxWidth().height(8.dp),
                                color = Color(0xFFCF6679),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            item {
                // Taunt card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "\"$taunt\"",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = if (winning) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How the Rival Works", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Your rival is a simulated version of yourself at 60–90% efficiency. " +
                                    "They complete slightly fewer quests than a perfect day. " +
                                    "Beat them consistently to keep their streak from growing. " +
                                    "If they streak more than you, they level up — making the challenge harder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
