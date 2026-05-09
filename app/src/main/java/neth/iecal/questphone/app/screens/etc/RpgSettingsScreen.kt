package neth.iecal.questphone.app.screens.etc

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

data class RpgTermEntry(
    val label: String,
    val defaultTerm: String,
    val getter: (UserRepository) -> String,
    val setter: (UserRepository, String) -> Unit
)

@HiltViewModel
class RpgSettingsViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    var rpgEnabled by mutableStateOf(userRepository.userInfo.rpgModeEnabled)
    var appRenameEnabled by mutableStateOf(userRepository.userInfo.rpgAppRenameEnabled)

    val termEntries = listOf(
        RpgTermEntry("Quest / Task", "Quest",
            { it.userInfo.rpgTermQuest },
            { r, v -> r.userInfo.rpgTermQuest = v; r.saveUserInfo() }),
        RpgTermEntry("Coins / Currency", "Gold",
            { it.userInfo.rpgTermCoins },
            { r, v -> r.userInfo.rpgTermCoins = v; r.saveUserInfo() }),
        RpgTermEntry("Level", "Power Level",
            { it.userInfo.rpgTermLevel },
            { r, v -> r.userInfo.rpgTermLevel = v; r.saveUserInfo() }),
        RpgTermEntry("Streak", "Discipline",
            { it.userInfo.rpgTermStreak },
            { r, v -> r.userInfo.rpgTermStreak = v; r.saveUserInfo() }),
        RpgTermEntry("XP", "Cultivation XP",
            { it.userInfo.rpgTermXp },
            { r, v -> r.userInfo.rpgTermXp = v; r.saveUserInfo() }),
        RpgTermEntry("App Drawer", "Grimoire",
            { it.userInfo.rpgTermAppDrawer },
            { r, v -> r.userInfo.rpgTermAppDrawer = v; r.saveUserInfo() }),
        RpgTermEntry("Settings", "Sanctum",
            { it.userInfo.rpgTermSettings },
            { r, v -> r.userInfo.rpgTermSettings = v; r.saveUserInfo() }),
        RpgTermEntry("Store", "Emporium",
            { it.userInfo.rpgTermStore },
            { r, v -> r.userInfo.rpgTermStore = v; r.saveUserInfo() }),
    )

    fun toggleRpgEnabled(v: Boolean) {
        rpgEnabled = v
        userRepository.userInfo.rpgModeEnabled = v
        userRepository.saveUserInfo()
    }

    fun toggleAppRename(v: Boolean) {
        appRenameEnabled = v
        userRepository.userInfo.rpgAppRenameEnabled = v
        userRepository.saveUserInfo()
    }

    fun resetToDefaults() {
        userRepository.userInfo.apply {
            rpgTermQuest = "Mission"; rpgTermCoins = "Gold"; rpgTermLevel = "Power Level"
            rpgTermStreak = "Discipline"; rpgTermXp = "Cultivation XP"
            rpgTermAppDrawer = "Grimoire"; rpgTermSettings = "Sanctum"; rpgTermStore = "Emporium"
        }
        userRepository.saveUserInfo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpgSettingsScreen(navController: NavController, vm: RpgSettingsViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚔️ RPG Mode", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Master toggle
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("RPG Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "Renames UI labels to custom RPG terms across the whole app. Off by default.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = vm.rpgEnabled, onCheckedChange = { vm.toggleRpgEnabled(it) })
                        }
                        if (vm.rpgEnabled) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "✅ RPG Mode active — UI labels are now using your custom terms.",
                                    Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // App rename toggle
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        Arrangement.SpaceBetween, Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("RPG App Rename", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Show renamed apps everywhere (drawer, blocker, dialogs). Long-press any app in drawer to rename.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = vm.appRenameEnabled,
                            onCheckedChange = { vm.toggleAppRename(it) }
                        )
                    }
                }
            }

            // Term customization
            item {
                AnimatedVisibility(visible = vm.rpgEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Custom Terms", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { vm.resetToDefaults() }) { Text("Reset to defaults") }
                        }
                        vm.termEntries.forEach { entry ->
                            var value by remember {
                                mutableStateOf(entry.getter(vm.userRepository))
                            }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(entry.label, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                                        Text(
                                            "was: ${entry.defaultTerm.replaceFirstChar { it.lowercase() }}",
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.35f)
                                        )
                                        OutlinedTextField(
                                            value = value,
                                            onValueChange = {
                                                value = it
                                                if (it.isNotBlank()) entry.setter(vm.userRepository, it)
                                            },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.65f),
                                            placeholder = { Text(entry.defaultTerm) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("How it works", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        Text(
                            "• Toggle RPG Mode on → all UI labels switch to your terms\n" +
                            "• Toggle off → everything returns to normal instantly\n" +
                            "• App rename is independent — you can rename apps without enabling RPG Mode\n" +
                            "• Terms are saved to your profile and sync across devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
