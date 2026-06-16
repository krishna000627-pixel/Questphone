package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

@HiltViewModel
class LockdownSettingsViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    var escalationEnabled by mutableStateOf(userRepository.userInfo.lockdownEscalationEnabled)

    fun setEscalation(v: Boolean) {
        escalationEnabled = v
        userRepository.userInfo.lockdownEscalationEnabled = v
        userRepository.saveUserInfo()
    }

    fun resetMissedDays() {
        userRepository.userInfo.missedQuestDays = 0
        userRepository.userInfo.panicButtonCooldownMs = 0L
        userRepository.saveUserInfo()
    }

    val missedDays get() = userRepository.userInfo.missedQuestDays
    val panicCooldownRemaining get() = LockdownEscalationManager.panicCooldownSeconds(userRepository)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownSettingsScreen(navController: NavController, vm: LockdownSettingsViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lockdown Escalation", fontWeight = FontWeight.Bold) },
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🔒 Lockdown Escalation", fontWeight = FontWeight.Bold)
                        Text(
                            "If you miss quests 3 days in a row, Stranger Mode automatically activates for 24 hours. " +
                            "The panic button (unlock app) will have a 60-second cooldown. " +
                            "This creates real consequences for repeated failures.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween, Alignment.CenterVertically
                        ) {
                            Text("Enable Escalation", fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = vm.escalationEnabled,
                                onCheckedChange = { vm.setEscalation(it) }
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current Status", fontWeight = FontWeight.SemiBold)
                        Text("Consecutive missed days: ${vm.missedDays} / 3",
                            style = MaterialTheme.typography.bodySmall)
                        val cooldown = vm.panicCooldownRemaining
                        if (cooldown > 0) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "⏳ Panic button locked for ${cooldown / 60}m ${cooldown % 60}s",
                                    Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        } else {
                            Surface(color = Color(0x1F4CAF50), shape = RoundedCornerShape(8.dp)) {
                                Text("✅ No active lockdown",
                                    Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF388E3C))
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.resetMissedDays() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reset missed day counter") }
                    }
                }
            }
        }
    }
}
