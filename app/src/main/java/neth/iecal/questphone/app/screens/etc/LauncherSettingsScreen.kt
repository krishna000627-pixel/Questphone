package neth.iecal.questphone.app.screens.etc

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.focus.*
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.core.sync.SyncState
import neth.iecal.questphone.core.sync.SyncViewModel
import javax.inject.Inject

@HiltViewModel
class LauncherSettingsViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    fun getHiddenPackages(): Set<String> = userRepository.getHiddenPackages()
    fun unhidePackage(pkg: String) {
        // Fix #2: unhide only — don't auto-modify blocked list; AppListViewModel.loadApps() will re-read
        userRepository.unhidePackage(pkg)
    }
    fun getSidePanelHidden(): Set<String> = userRepository.getSidePanelHidden()
    fun setPanelItemHidden(key: String, hidden: Boolean) = userRepository.setPanelItemHidden(key, hidden)

    // Stranger mode whitelist
    fun getStrangerWhitelist(context: android.content.Context): Set<String> =
        context.getStrangerWhitelist()
    fun saveStrangerWhitelist(context: android.content.Context, set: Set<String>) {
        context.saveStrangerWhitelist(set)
        userRepository.updateStrangerWhitelistSaved(set)
    }

    // Custom voice actions
    fun getCustomVoiceActions() = userRepository.getCustomVoiceActions()
    fun addVoiceAction(action: nethical.questphone.data.CustomVoiceAction) =
        userRepository.addCustomVoiceAction(action)
    fun removeVoiceAction(phrase: String) = userRepository.removeCustomVoiceAction(phrase)

    // Study quota
    fun getPrimeStudyPackage() = userRepository.getPrimeStudyPackage()
    fun setPrimeStudyPackage(pkg: String) = userRepository.setPrimeStudyPackage(pkg)
    fun getDailyStudyQuotaHours() = userRepository.getDailyStudyQuotaHours()
    fun setDailyStudyQuotaHours(hours: Float) = userRepository.setDailyStudyQuotaHours(hours)
    fun getStudyApps() = userRepository.getStudyApps()
}

// All possible panel item keys — must match HomeScreen allSidePanelItems contentDesc
val ALL_PANEL_ITEMS = listOf(
    "Customize", "Store", "Add Quest",
    "Profile", "Quest Analytics",
    "Screentime", "Settings"
)
// Settings must always be visible (can't hide the settings button)
val PANEL_ALWAYS_VISIBLE = setOf("Settings")

@OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LauncherSettingsScreen(
        navController: NavController,
        settingsVm: LauncherSettingsViewModel = hiltViewModel(),
        syncVm: SyncViewModel = hiltViewModel()
    ) {
        val context = LocalContext.current
        val micPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // permission granted
            }
        }
    val serverRunning by syncVm.serverRunning.collectAsState()
    val syncState by syncVm.syncState.collectAsState()
    val deviceIp by syncVm.deviceIp.collectAsState()
    var targetIp by remember { mutableStateOf("") }

    var hiddenPackages = remember { mutableStateOf(settingsVm.getHiddenPackages()) }

    var focusEnabled by remember { mutableStateOf<Boolean>(context.isFocusSchedulingEnabled()) }
    var strangerMode by remember { mutableStateOf<Boolean>(context.isStrangerMode()) }
    var jarvisActive by remember { mutableStateOf<Boolean>(false) }

    // Panel item visibility — true = shown
    var panelVisibility by remember {
        mutableStateOf(
            ALL_PANEL_ITEMS.associateWith { key ->
                key !in settingsVm.getSidePanelHidden()
            }.toMutableMap()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Launcher Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Side Panel Customisation ──────────────────────────────────
            item {
                SettingCard(
                    title = "📌 Side Panel Items",
                    subtitle = "Choose which icons appear in the home panel"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ALL_PANEL_ITEMS.forEach { key ->
                            val locked = key in PANEL_ALWAYS_VISIBLE
                            val visible = panelVisibility[key] ?: true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (visible) Color(0xFF0F1A0F) else Color(0xFF111111)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    key,
                                    fontSize = 14.sp,
                                    color = if (locked) Color.Gray else Color.White,
                                    fontWeight = if (visible) FontWeight.Medium else FontWeight.Normal
                                )
                                if (locked) {
                                    Text("always on", fontSize = 10.sp, color = Color(0xFF555555))
                                } else {
                                    Switch(
                                        checked = visible,
                                        onCheckedChange = { on ->
                                            panelVisibility = (panelVisibility + (key to on)).toMutableMap()
                                            settingsVm.setPanelItemHidden(key, !on)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF4CAF50),
                                            checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.3f),
                                            uncheckedThumbColor = Color(0xFF555555),
                                            uncheckedTrackColor = Color(0xFF222222)
                                        ),
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Focus Gatekeeper ──────────────────────────────────────────
            item {
                SettingCard(
                    title = "⚔️ Focus Gatekeeper",
                    subtitle = "Mon–Fri blocks non-study apps during focus window"
                ) {
                    Row(Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Auto-schedule Focus", fontSize = 14.sp,
                                color = Color.White, fontWeight = FontWeight.Medium)
                            Text("PhysicsWallah always allowed", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = focusEnabled,
                            onCheckedChange = { focusEnabled = it; context.setFocusSchedulingEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE040FB),
                                checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.3f))
                        )
                    }
                }
            }

            // ── Stranger Mode ─────────────────────────────────────────────
            item {
                SettingCard(
                    title = "🕵️ Stranger Mode",
                    subtitle = "Hides all apps except whitelisted ones in drawer"
                ) {
                    Row(Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Stranger Mode", fontSize = 14.sp,
                                color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Toggle on/off — configure whitelist below", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = strangerMode,
                            onCheckedChange = { on ->
                                strangerMode = on
                                context.setStrangerMode(on)
                                // Immediately reload app list via broadcast so no force-stop needed
                                context.sendBroadcast(android.content.Intent("neth.iecal.questphone.RELOAD_APP_LIST"))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00BCD4),
                                checkedTrackColor = Color(0xFF00BCD4).copy(alpha = 0.3f))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { navController.navigate(RootRoute.StrangerModeSettings.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A1A1F))
                    ) { Text("⚙️ Configure Whitelist →", color = Color(0xFF00BCD4)) }
                }
            }

            // ── Jarvis Voice ──────────────────────────────────────────────
            item {
                SettingCard(
                    title = "🤖 Jarvis Voice",
                    subtitle = "Wake word activates Google Assistant"
                ) {
                    Row(Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Jarvis Listener", fontSize = 14.sp,
                                color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Wake word: \"${context.getSharedPreferences("onboard", 0)
                                .getString("jarvis_word","jarvis")}\"",
                                fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = jarvisActive,
                            onCheckedChange = { on ->
                                if (on) {
                                    val hasMic = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMic) {
                                        jarvisActive = true
                                        context.startService(Intent(context, JarvisListenerService::class.java).apply { action = ACTION_START })
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    jarvisActive = false
                                    context.startService(Intent(context, JarvisListenerService::class.java).apply { action = ACTION_STOP })
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.3f))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { navController.navigate(RootRoute.CustomVoiceActionsSettings.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A140A))
                    ) { Text("🎙️ Custom Voice Commands →", color = Color(0xFF4CAF50)) }
                }
            }

            // ── Study Quota ───────────────────────────────────────────────
            item {
                SettingCard(
                    title = "📚 Study Quota",
                    subtitle = "Block all apps next day if study target not met"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.StudyQuotaSettings.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1000))
                    ) { Text("Configure Study Quota →", color = Color(0xFFFFAB40)) }
                }
            }

            // ── Hidden Apps ───────────────────────────────────────────────
            item {
                SettingCard(
                    title = "🙈 Hidden Apps",
                    subtitle = "Manage apps hidden from the launcher drawer"
                ) {
                    val count = hiddenPackages.value.size
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(
                            if (count == 0) "No hidden apps" else "$count app${if (count == 1) "" else "s"} hidden",
                            fontSize = 13.sp, color = if (count == 0) Color.Gray else Color.White
                        )
                        Button(
                            onClick = { navController.navigate(RootRoute.HiddenAppsSettings.route) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                        ) { Text("Manage →", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }

            // ── JSON Quest Converter ──────────────────────────────────────
            item {
                SettingCard(
                    title = "🗂️ JSON Quest Converter",
                    subtitle = "Convert quest images/JSON into QuestPhone quests"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.JsonQuestConverter.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D0D1A))
                    ) { Text("Open Quest Converter →", color = Color(0xFF7986CB)) }
                }
            }

            // ── GitHub Sync ───────────────────────────────────────────
            item {
                SettingCard(
                    title = "☁️ GitHub Sync",
                    subtitle = "Auto-sync quests and progress to your GitHub repo"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.GitHubSync.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
                    ) { Text("Configure GitHub Sync →", color = Color.White) }
                }
            }

            // ── WiFi Sync ─────────────────────────────────────────────────
            item {
                SettingCard(
                    title = "📡 WiFi Sync",
                    subtitle = "Sync data between devices on the same network"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(
                                if (serverRunning) "● Running · $deviceIp:45678"
                                else "○ Server Stopped",
                                color = if (serverRunning) Color(0xFF4CAF50) else Color.Gray,
                                fontSize = 13.sp
                            )
                            TextButton(onClick = {
                                if (serverRunning) syncVm.stopServer() else syncVm.startServer()
                            }) { Text(if (serverRunning) "Stop" else "Start") }
                        }
                        OutlinedTextField(
                            value = targetIp, onValueChange = { targetIp = it },
                            label = { Text("Target Device IP") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { syncVm.syncFrom(targetIp.trim()) },
                                enabled = targetIp.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("⬇ Pull") }
                            Button(
                                onClick = { syncVm.pushTo(targetIp.trim()) },
                                enabled = targetIp.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("⬆ Push") }
                        }
                        val msg = when (val s = syncState) {
                            is SyncState.Success -> s.message
                            is SyncState.Error -> s.message
                            is SyncState.Syncing -> "Syncing…"
                            else -> ""
                        }
                        if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF666666))
        }
        content()
    }
}
