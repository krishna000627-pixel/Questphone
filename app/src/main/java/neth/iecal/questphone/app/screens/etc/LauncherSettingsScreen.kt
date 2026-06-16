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
import nethical.questphone.core.core.utils.managers.isSetToDefaultLauncher
import nethical.questphone.core.core.utils.managers.openDefaultLauncherSettings
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableIntStateOf
import javax.inject.Inject
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.launch

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
    fun getSidePanelOrder(): List<String> = userRepository.getSidePanelOrder()
    fun saveSidePanelOrder(order: List<String>) = userRepository.saveSidePanelOrder(order)

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
    fun setStudyQuotaBlockDate(date: String) = userRepository.setStudyQuotaBlockDate(date)
    fun getStudyApps() = userRepository.getStudyApps()
}

// All possible panel item keys — must match HomeScreen allSidePanelItems contentDesc
val ALL_PANEL_ITEMS = listOf(
    "Customize", "Play", "Stats", "Store", "Add Quest",
    "Profile", "Quest Analytics",
    "AI Chat", "Settings"
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

    // Panel order — hoisted here so reordering survives LazyColumn recomposition
    var panelOrder by remember {
        val saved = settingsVm.getSidePanelOrder()
        val order = if (saved.isEmpty()) ALL_PANEL_ITEMS.toMutableList()
                    else (saved + ALL_PANEL_ITEMS.filter { it !in saved }).toMutableList()
        mutableStateOf(order)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Launcher Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { try { navController.popBackStack() } catch (_: Exception) {} }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        var searchQuery by remember { mutableStateOf("") }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search settings...", color = Color(0xFF444444)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00BCD4), unfocusedBorderColor = Color(0xFF222222)
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("×", color = Color(0xFF888888), fontSize = 16.sp)
                        }
                    }
                }
            )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                Text(
                    text = "LAUNCHER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF444444),
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                )
            }
            item {
                val isDefault = remember { isSetToDefaultLauncher(context) }
                SettingCard(
                    title = "Default Launcher",
                    subtitle = if (isDefault) "QuestPhone is your default launcher"
                               else "QuestPhone is NOT set as default launcher"
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(
                            if (isDefault) "Active" else "Not set",
                            fontSize = 13.sp,
                            color = if (isDefault) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Medium
                        )
                        if (!isDefault) {
                            Button(
                                onClick = { openDefaultLauncherSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A1A))
                            ) { Text("Set as Default", color = Color(0xFF4CAF50)) }
                        }
                    }
                }
            }

            // -- Side Panel Customisation ----------------------------------
            item {
                SettingCard(
                    title = "Side Panel Items",
                    subtitle = "Toggle visibility. Hold and drag = reorder"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        panelOrder.forEachIndexed { index, key ->
                            val locked = key in PANEL_ALWAYS_VISIBLE
                            val visible = panelVisibility[key] ?: true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (visible) Color(0xFF0F1A0F) else Color(0xFF111111))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Up/Down reorder buttons
                                Column(
                                    modifier = Modifier.padding(end = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (index > 0) {
                                        TextButton(
                                            onClick = {
                                                val newOrder = panelOrder.toMutableList()
                                                val tmp = newOrder[index]
                                                newOrder[index] = newOrder[index - 1]
                                                newOrder[index - 1] = tmp
                                                panelOrder = newOrder
                                                settingsVm.saveSidePanelOrder(newOrder)
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("^", fontSize = 12.sp, color = Color(0xFF888888))
                                        }
                                    }
                                    if (index < panelOrder.size - 1) {
                                        TextButton(
                                            onClick = {
                                                val newOrder = panelOrder.toMutableList()
                                                val tmp = newOrder[index]
                                                newOrder[index] = newOrder[index + 1]
                                                newOrder[index + 1] = tmp
                                                panelOrder = newOrder
                                                settingsVm.saveSidePanelOrder(newOrder)
                                            },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("v", fontSize = 12.sp, color = Color(0xFF888888))
                                        }
                                    }
                                }
                                Text(
                                    key,
                                    fontSize = 14.sp,
                                    color = if (locked) Color.Gray else Color.White,
                                    fontWeight = if (visible) FontWeight.Medium else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
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

            // -- Focus Gatekeeper ------------------------------------------
            item {
                Text(
                    text = "FOCUS & BLOCK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF444444),
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                )
            }
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

            // -- Stranger Mode ---------------------------------------------
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
                        onClick = { navController.navigate(RootRoute.StrangerModeSettings.route) { launchSingleTop = true } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A1A1F))
                    ) { Text("⚙️ Configure Whitelist →", color = Color(0xFF00BCD4)) }
                }
            }

            // -- Jarvis Voice ----------------------------------------------
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

            // -- Study Quota -----------------------------------------------
            item {
                SettingCard(
                    title = "📚 Study Quota",
                    subtitle = "Block all apps next day if study target not met"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.StudyQuotaSettings.route) { launchSingleTop = true } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1000))
                    ) { Text("Configure Study Quota →", color = Color(0xFFFFAB40)) }
                }
            }

            // -- Hidden Apps -----------------------------------------------
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
                            onClick = { navController.navigate(RootRoute.HiddenAppsSettings.route) { launchSingleTop = true } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                        ) { Text("Manage →", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }

            // -- App Locker ------------------------------------------------
            item {
                SettingCard(
                    title = "🔒 App Locker",
                    subtitle = "Lock apps behind a PIN — blocks access from anywhere"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.AppLockerSettings.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                    ) { Text("Manage Locked Apps →", color = Color.White, fontSize = 12.sp) }
                }
            }

            // -- App Vault -------------------------------------------------
            item {
                SettingCard(
                    title = "🔐 App Vault",
                    subtitle = "Hide apps inside a fake Calculator. Enter secret code + = to access"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.AppVaultSettings.route) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                    ) { Text("Manage Vault →", color = Color.White, fontSize = 12.sp) }
                }
            }

            // -- JSON Quest Converter --------------------------------------
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



            // -- WiFi Sync -------------------------------------------------
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
                            color = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (serverRunning) syncVm.stopServer()
                                    else syncVm.startServer()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (serverRunning) Color(0xFF3E0000) else Color(0xFF0A1F0A)
                                )
                            ) {
                                Text(
                                    if (serverRunning) "Stop Server" else "Start Server",
                                    color = if (serverRunning) Color(0xFFEF9A9A) else Color(0xFF4CAF50)
                                )
                            }
                            Button(
                                onClick = { navController.navigate(RootRoute.WifiSync.route) { launchSingleTop = true } },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A140A))
                            ) { Text("Configure →", color = Color(0xFF4CAF50)) }
                        }
                    }
                }
            }

            // -- GitHub Sync -------------------------------------------
            item {
                SettingCard(
                    title = "☁️ GitHub Sync",
                    subtitle = "Auto-sync quests and progress to your GitHub repo"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.GitHubSync.route) { launchSingleTop = true } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
                    ) { Text("Configure GitHub Sync →", color = Color.White) }
                }
            }

            // -- Auto-Sync (Render) ------------------------------------
            item {
                SettingCard(
                    title = "🔄 Auto-Sync",
                    subtitle = "Sync your data to the cloud server"
                ) {
                    Button(
                        onClick = { navController.navigate(RootRoute.RenderSync.route) { launchSingleTop = true } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                    ) { Text("Configure Auto-Sync →", color = Color(0xFF64B5F6)) }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
        } // close Column
    }
}

@Composable
fun SettingCard(
    title: String,
    subtitle: String = "",
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (subtitle.isNotBlank())
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF666666))
        }
        content()
    }
}
