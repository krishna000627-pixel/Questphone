package neth.iecal.questphone.app.screens.etc

import androidx.compose.animation.AnimatedVisibility
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
import neth.iecal.questphone.core.rpg.AppRenameDefaults
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
    var appliedStyle by mutableStateOf(
        userRepository.userInfo.appRenames.let { renames ->
            AppRenameDefaults.STYLES.firstOrNull { style ->
                AppRenameDefaults.forStyle(style).any { (k, v) -> renames[k] == v }
            } ?: ""
        }
    )

    val termEntries = listOf(
        RpgTermEntry("Quest / Task", "Quest",
            { it.userInfo.rpgTermQuest }, { r, v -> r.userInfo.rpgTermQuest = v; r.saveUserInfo() }),
        RpgTermEntry("Coins / Currency", "Gold",
            { it.userInfo.rpgTermCoins }, { r, v -> r.userInfo.rpgTermCoins = v; r.saveUserInfo() }),
        RpgTermEntry("Level", "Power Level",
            { it.userInfo.rpgTermLevel }, { r, v -> r.userInfo.rpgTermLevel = v; r.saveUserInfo() }),
        RpgTermEntry("Streak", "Discipline",
            { it.userInfo.rpgTermStreak }, { r, v -> r.userInfo.rpgTermStreak = v; r.saveUserInfo() }),
        RpgTermEntry("XP", "Cultivation XP",
            { it.userInfo.rpgTermXp }, { r, v -> r.userInfo.rpgTermXp = v; r.saveUserInfo() }),
        RpgTermEntry("App Drawer", "Grimoire",
            { it.userInfo.rpgTermAppDrawer }, { r, v -> r.userInfo.rpgTermAppDrawer = v; r.saveUserInfo() }),
        RpgTermEntry("Settings", "Sanctum",
            { it.userInfo.rpgTermSettings }, { r, v -> r.userInfo.rpgTermSettings = v; r.saveUserInfo() }),
        RpgTermEntry("Store", "Emporium",
            { it.userInfo.rpgTermStore }, { r, v -> r.userInfo.rpgTermStore = v; r.saveUserInfo() }),
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

    fun applyRenameStyle(style: String, overwrite: Boolean = false) {
        AppRenameDefaults.applyStyle(style, userRepository, overwrite)
        appliedStyle = style
        appRenameEnabled = true
        userRepository.userInfo.rpgAppRenameEnabled = true
        userRepository.saveUserInfo()
    }

    fun clearRenameStyle(style: String) {
        AppRenameDefaults.clearStyle(style, userRepository)
        appliedStyle = ""
        userRepository.saveUserInfo()
    }

    fun resetTerms() {
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

            // ── Master toggle ─────────────────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("RPG Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Renames UI labels to custom RPG terms. Off by default.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = vm.rpgEnabled, onCheckedChange = { vm.toggleRpgEnabled(it) })
                        }
                    }
                }
            }

            // ── App Rename Style picker ───────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("App Rename Style", fontWeight = FontWeight.Bold)
                        Text(
                            "Rename apps across your entire launcher in one tap. Built from your device's app list — no AI needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Style cards
                        val styleInfo = mapOf(
                            "rpg" to Triple("⚔️ RPG", Color(0xFFFF9800),
                                "Chrome→Iron Browser, YouTube→Bard's Stage, Notes→Tome of Notes"),
                            "friendly" to Triple("😊 Friendly", Color(0xFF4CAF50),
                                "Clean short labels. Chrome→Chrome, YouTube→YouTube, Maps→Maps"),
                            "minimal" to Triple("◻ Minimal", Color(0xFF2196F3),
                                "Ultra short. Chrome→Browser, YouTube→Video, WhatsApp→Chat")
                        )

                        styleInfo.forEach { (style, info) ->
                            val (label, color, preview) = info
                            val isActive = vm.appliedStyle == style
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        if (isActive) 2.dp else 0.5.dp,
                                        if (isActive) color else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { if (!isActive) vm.applyRenameStyle(style, true) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isActive,
                                    onClick = { if (!isActive) vm.applyRenameStyle(style, true) })
                                Column(Modifier.weight(1f)) {
                                    Text(label, fontWeight = FontWeight.SemiBold, color = color)
                                    Text(preview, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isActive) {
                                    TextButton(onClick = { vm.clearRenameStyle(style) }) {
                                        Text("Clear", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        // No rename option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (vm.appliedStyle.isNotBlank()) vm.clearRenameStyle(vm.appliedStyle)
                                    vm.toggleAppRename(false)
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = vm.appliedStyle.isBlank(),
                                onClick = {
                                    if (vm.appliedStyle.isNotBlank()) vm.clearRenameStyle(vm.appliedStyle)
                                    vm.toggleAppRename(false)
                                })
                            Column {
                                Text("Real names", fontWeight = FontWeight.SemiBold)
                                Text("Use original app names from the OS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Text(
                            "💡 Long-press any app in the drawer to rename it individually.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── UI Term customization ─────────────────────────────────────
            item {
                AnimatedVisibility(visible = vm.rpgEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Custom UI Terms", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { vm.resetTerms() }) { Text("Reset") }
                        }
                        vm.termEntries.forEach { entry ->
                            var value by remember { mutableStateOf(entry.getter(vm.userRepository)) }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(entry.label, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                                        Text("was: ${entry.defaultTerm.lowercase()}", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.35f))
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

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
