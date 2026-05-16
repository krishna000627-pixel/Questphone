package neth.iecal.questphone.app.screens.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.core.core.utils.getCurrentDate
import javax.inject.Inject

/** A preset chain of quest titles in order */
data class QuestChainTemplate(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val steps: List<ChainStep>
)

data class ChainStep(
    val title: String,
    val instructions: String,
    val rewardCoins: Int,
    val statIndex: Int,   // 0–3 which stat to boost
    val statReward: Int
)

private val CHAIN_TEMPLATES = listOf(
    QuestChainTemplate(
        id = "morning_warrior",
        name = "Morning Warrior",
        emoji = "🌅",
        description = "Build the ultimate morning routine step by step over 5 days.",
        steps = listOf(
            ChainStep("Wake Up at 6 AM", "Get out of bed by 6:00 AM.", 8, 3, 2),
            ChainStep("5-Minute Stretch", "Stretch your body for 5 minutes after waking.", 8, 0, 2),
            ChainStep("Cold Shower", "Take a cold shower first thing in the morning.", 10, 0, 3),
            ChainStep("Journaling", "Write 3 things you're grateful for.", 8, 1, 3),
            ChainStep("Morning Study Block", "Study for 1 hour before 9 AM.", 15, 1, 5),
        )
    ),
    QuestChainTemplate(
        id = "deep_focus",
        name = "Deep Focus Initiate",
        emoji = "🎯",
        description = "Train your focus muscle progressively from 25 to 120 minutes.",
        steps = listOf(
            ChainStep("Focus 25 Minutes", "Study with no phone for 25 minutes.", 8, 2, 3),
            ChainStep("Focus 45 Minutes", "One Pomodoro + extension. No distractions.", 10, 2, 4),
            ChainStep("Focus 60 Minutes", "One full hour of uninterrupted deep work.", 12, 2, 5),
            ChainStep("Focus 90 Minutes", "Elite focus session. Phone in another room.", 15, 2, 6),
            ChainStep("Focus 120 Minutes", "2-hour deep work block. The pinnacle.", 20, 2, 8),
        )
    ),
    QuestChainTemplate(
        id = "strength_arc",
        name = "Strength Arc",
        emoji = "💪",
        description = "A progressive overload training chain.",
        steps = listOf(
            ChainStep("10 Push-ups", "Complete 10 proper push-ups.", 6, 0, 2),
            ChainStep("20 Push-ups", "Complete 20 push-ups in one session.", 8, 0, 3),
            ChainStep("30 Push-ups + Squats", "30 push-ups + 30 squats.", 10, 0, 4),
            ChainStep("Full Body HIIT", "20-minute full body HIIT session.", 12, 0, 5),
            ChainStep("1-Hour Gym", "Complete a full gym session.", 15, 0, 7),
        )
    ),
)

@HiltViewModel
class QuestChainsViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _chains = MutableStateFlow(CHAIN_TEMPLATES)
    val chains = _chains.asStateFlow()

    private val _activeChainId = MutableStateFlow(userRepository.userInfo.activeQuestChainIds.firstOrNull())
    val activeChainId = _activeChainId.asStateFlow()

    private val _activeStepIndex = MutableStateFlow(0)
    val activeStepIndex = _activeStepIndex.asStateFlow()

    private val _questsForChain = MutableStateFlow<List<CommonQuestInfo>>(emptyList())
    val questsForChain = _questsForChain.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { viewModelScope.launch { loadActiveChain() } }

    private suspend fun loadActiveChain() {
        val chainId = _activeChainId.value ?: return
        val chain = CHAIN_TEMPLATES.find { it.id == chainId } ?: return
        val all = questRepository.getAllQuests().first()
        val today = getCurrentDate()

        // Find which steps have been completed (exist as quests and were completed today or earlier)
        val completedSteps = chain.steps.indices.count { i ->
            val stepTitle = chain.steps[i].title
            all.any { it.title == stepTitle && it.last_completed_on != "0001-01-01" && it.last_completed_on.isNotBlank() }
        }
        _activeStepIndex.value = completedSteps.coerceAtMost(chain.steps.size - 1)
        _questsForChain.value = all.filter { q -> chain.steps.any { it.title == q.title } }
    }

    fun startChain(chain: QuestChainTemplate) {
        viewModelScope.launch {
            val u = userRepository.userInfo
            if (u.activeQuestChainIds.contains(chain.id)) {
                _message.value = "Chain already active!"
                return@launch
            }
            // Create the first step as a real quest
            val step = chain.steps[0]
            val quest = CommonQuestInfo(
                title = step.title,
                instructions = step.instructions,
                reward = step.rewardCoins,
                selected_days = setOf(nethical.questphone.data.DayOfWeek.MON, nethical.questphone.data.DayOfWeek.TUE, nethical.questphone.data.DayOfWeek.WED, nethical.questphone.data.DayOfWeek.THU, nethical.questphone.data.DayOfWeek.FRI, nethical.questphone.data.DayOfWeek.SAT, nethical.questphone.data.DayOfWeek.SUN),
                time_range = listOf(6, 22)
            ).apply {
                when (step.statIndex) {
                    0 -> statReward1 = step.statReward
                    1 -> statReward2 = step.statReward
                    2 -> statReward3 = step.statReward
                    3 -> statReward4 = step.statReward
                }
            }
            questRepository.upsertQuest(quest)
            u.activeQuestChainIds.add(chain.id)
            userRepository.saveUserInfo()
            _activeChainId.value = chain.id
            _activeStepIndex.value = 0
            _message.value = "Chain started! '${step.title}' added to your quests."
        }
    }

    fun advanceChain(chain: QuestChainTemplate) {
        viewModelScope.launch {
            val nextIndex = _activeStepIndex.value + 1
            if (nextIndex >= chain.steps.size) {
                // Chain complete
                userRepository.userInfo.activeQuestChainIds.remove(chain.id)
                userRepository.addCoins(50, "Quest Chain completed: ${chain.name}")
                userRepository.saveUserInfo()
                _message.value = "🏆 Chain '${chain.name}' complete! +50 bonus coins!"
                _activeChainId.value = null
                return@launch
            }
            val step = chain.steps[nextIndex]
            val quest = CommonQuestInfo(
                title = step.title,
                instructions = step.instructions,
                reward = step.rewardCoins,
                selected_days = setOf(nethical.questphone.data.DayOfWeek.MON, nethical.questphone.data.DayOfWeek.TUE, nethical.questphone.data.DayOfWeek.WED, nethical.questphone.data.DayOfWeek.THU, nethical.questphone.data.DayOfWeek.FRI, nethical.questphone.data.DayOfWeek.SAT, nethical.questphone.data.DayOfWeek.SUN),
                time_range = listOf(6, 22)
            ).apply {
                when (step.statIndex) {
                    0 -> statReward1 = step.statReward
                    1 -> statReward2 = step.statReward
                    2 -> statReward3 = step.statReward
                    3 -> statReward4 = step.statReward
                }
            }
            questRepository.upsertQuest(quest)
            _activeStepIndex.value = nextIndex
            _message.value = "Step unlocked: '${step.title}'"
        }
    }

    fun clearMessage() { _message.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestChainsScreen(navController: NavController, vm: QuestChainsViewModel = hiltViewModel()) {
    val chains by vm.chains.collectAsState()
    val activeChainId by vm.activeChainId.collectAsState()
    val activeStep by vm.activeStepIndex.collectAsState()
    val message by vm.message.collectAsState()
    val snackHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackHost.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quest Chains", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackHost) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Multi-step quests that unlock one stage at a time. Complete each stage to reveal the next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(chains.size) { idx ->
                val chain = chains[idx]
                val isActive = activeChainId == chain.id
                ChainCard(
                    chain = chain,
                    isActive = isActive,
                    currentStep = if (isActive) activeStep else -1,
                    onStart = { vm.startChain(chain) },
                    onAdvance = { vm.advanceChain(chain) }
                )
            }
        }
    }
}

@Composable
private fun ChainCard(
    chain: QuestChainTemplate,
    isActive: Boolean,
    currentStep: Int,
    onStart: () -> Unit,
    onAdvance: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(chain.emoji, fontSize = 28.sp)
                Column {
                    Text(chain.name, fontWeight = FontWeight.Bold)
                    Text(chain.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Step progress track
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chain.steps.forEachIndexed { i, step ->
                    val done = isActive && i < currentStep
                    val current = isActive && i == currentStep
                    val locked = !isActive || i > currentStep

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> Color(0xFF4CAF50)
                                    current -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (done) "✓" else "${i + 1}",
                            color = if (done || current) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    if (i < chain.steps.size - 1) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(
                                    if (done) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }

            if (isActive) {
                Text(
                    "Current: ${chain.steps.getOrNull(currentStep)?.title ?: "Done"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAdvance) { Text("Mark done & unlock next") }
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Chain")
                }
            }
        }
    }
}
