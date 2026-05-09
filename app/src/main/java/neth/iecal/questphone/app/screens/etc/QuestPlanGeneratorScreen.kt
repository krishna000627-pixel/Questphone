package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.ai.ChatMessage
import neth.iecal.questphone.core.ai.GemmaRepository
import javax.inject.Inject

@HiltViewModel
class QuestPlanGeneratorViewModel @Inject constructor(
    val userRepository: UserRepository,
    val gemmaRepository: GemmaRepository
) : ViewModel() {

    private val _plan = MutableStateFlow<String?>(null)
    val plan = _plan.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun generatePlan(goal: String, weeks: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _plan.value = null
            try {
                val u = userRepository.userInfo
                val sp = u.statPoints
                val prompt = buildString {
                    appendLine("You are Kai, an RPG-style productivity AI. The user wants to achieve:")
                    appendLine("Goal: \"$goal\"  |  Timeline: $weeks weeks")
                    appendLine("User: Level ${u.level}, streak ${u.streak.currentStreak}d")
                    appendLine("Stats: ${sp.name1}=${sp.value1}, ${sp.name2}=${sp.value2}, ${sp.name3}=${sp.value3}, ${sp.name4}=${sp.value4}")
                    appendLine()
                    appendLine("Generate a week-by-week quest plan. Each week: list 3-5 daily quests with title, time window, days, stat focus, and one-line rationale. Use week headings. Be specific and motivating.")
                }
                val result = gemmaRepository.chat(emptyList(), prompt)
                if (result.isSuccess) {
                    _plan.value = result.getOrNull()?.text ?: "No response."
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Unknown error"
                }
            } catch (e: Exception) {
                _error.value = "Kai couldn't generate a plan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestPlanGeneratorScreen(
    navController: NavController,
    vm: QuestPlanGeneratorViewModel = hiltViewModel()
) {
    val plan by vm.plan.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()

    var goal by remember { mutableStateOf("") }
    var weeks by remember { mutableStateOf(2) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quest Plan Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("What's your goal?", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { goal = it },
                        label = { Text("e.g. Prepare for JEE Mains in 3 weeks") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4
                    )
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Weeks:", style = MaterialTheme.typography.bodyMedium)
                        listOf(1, 2, 3, 4, 6, 8).forEach { w ->
                            FilterChip(selected = weeks == w, onClick = { weeks = w },
                                label = { Text("$w") })
                        }
                    }
                    Button(
                        onClick = { if (goal.isNotBlank()) vm.generatePlan(goal, weeks) },
                        enabled = goal.isNotBlank() && !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Kai is planning…")
                        } else {
                            Text("⚡ Generate Plan with Kai")
                        }
                    }
                }
            }

            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            plan?.let { planText ->
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Your Quest Plan", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                        Text(planText, style = MaterialTheme.typography.bodySmall,
                            lineHeight = TextUnit(20f, TextUnitType.Sp))
                    }
                }
            }
        }
    }
}
