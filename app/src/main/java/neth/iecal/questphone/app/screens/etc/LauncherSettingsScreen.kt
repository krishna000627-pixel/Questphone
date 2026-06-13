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

            // -- Default Launcher ------------------------------------------
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

            item {
                val kaiModels = neth.iecal.questphone.core.ai.KAI_MODELS
                var selectedModel by remember { mutableStateOf(settingsVm.userRepository.userInfo.aiModel) }
                var selectedAvatar by remember { mutableIntStateOf(settingsVm.userRepository.userInfo.aiAvatarIndex) }
                var coinCost by remember { mutableIntStateOf(settingsVm.userRepository.userInfo.aiCoinCostPerMin) }
                var assistantPkg by remember { mutableStateOf(settingsVm.userRepository.userInfo.aiAssistantPackage) }

                SettingCard(
                    title = "Kai — AI Companion",
                    subtitle = "Active: ${kaiModels.firstOrNull { it.first == selectedModel }?.second ?: selectedModel}"
                ) {
                    // Open chat button
                    Button(
                        onClick = { try { navController.navigate(RootRoute.GemmaChat.route) { launchSingleTop = true } } catch (_: Exception) {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001A1F))
                    ) { Text("Open Kai Chat →", color = Color(0xFF00BCD4)) }

                    Spacer(Modifier.height(4.dp))

                    // AI Memory Trainer button
                    Button(
                        onClick = { try { navController.navigate(RootRoute.AiMemoryTrainer.route) { launchSingleTop = true } } catch (_: Exception) {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001A10))
                    ) { Text("🧠 AI Memory Trainer →", color = Color(0xFF4CAF50)) }


                    Spacer(Modifier.height(8.dp))
                    // Quick nav: Quest Plan Generator (the model selector is shown inline below)
                    TextButton(
                        onClick = { navController.navigate(RootRoute.QuestPlanGenerator.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("⚡ Quest Plan Generator →", color = Color(0xFF00BCD4)) }

                    Spacer(Modifier.height(12.dp))

                    // Avatar picker
                    Text("Avatar", fontSize = 12.sp, color = Color(0xFF888888), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    val kaiAvatarNames = neth.iecal.questphone.app.screens.etc.KAI_AVATAR_NAMES
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(kaiAvatarNames) { idx, name ->
                            val isSelected = selectedAvatar == idx
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF001A1F) else Color(0xFF111111))
                                    .border(1.dp, if (isSelected) Color(0xFF00BCD4) else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedAvatar = idx
                                        settingsVm.userRepository.userInfo.aiAvatarIndex = idx
                                        settingsVm.userRepository.saveUserInfo()
                                    }
                                    .padding(8.dp)
                            ) {
                                neth.iecal.questphone.app.screens.etc.KaiPixelAvatar(
                                    avatarIndex = idx,
                                    size = 32.dp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(name, fontSize = 8.sp, color = if (isSelected) Color(0xFF00BCD4) else Color(0xFF555555))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Model selector
                    Text("AI Model", fontSize = 12.sp, color = Color(0xFF888888), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    kaiModels.forEach { (modelId, modelName) ->
                        val isActive = selectedModel == modelId
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) Color(0xFF001A1F) else Color.Transparent)
                                .clickable {
                                    selectedModel = modelId
                                    settingsVm.userRepository.userInfo.aiModel = modelId
                                    settingsVm.userRepository.saveUserInfo()
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            Arrangement.SpaceBetween, Alignment.CenterVertically
                        ) {
                            Text(modelName, fontSize = 13.sp, color = if (isActive) Color.White else Color(0xFF666666))
                            if (isActive) Text("Active", fontSize = 10.sp, color = Color(0xFF00BCD4))
                        }
                    }

                    // Custom model ID input — shown when model not in list OR always accessible
                    Spacer(Modifier.height(8.dp))
                    var showCustomModelInput by remember { mutableStateOf(
                        neth.iecal.questphone.core.ai.KAI_MODELS.none { it.first == selectedModel }
                    ) }
                    var customModelId by remember { mutableStateOf(
                        if (neth.iecal.questphone.core.ai.KAI_MODELS.none { it.first == selectedModel }) selectedModel else ""
                    ) }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showCustomModelInput) Color(0xFF1A0D00) else Color.Transparent)
                            .clickable { showCustomModelInput = !showCustomModelInput }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically
                    ) {
                        Text("Custom Model ID", fontSize = 13.sp,
                            color = if (showCustomModelInput) Color(0xFFFF9800) else Color(0xFF666666))
                        Text(if (showCustomModelInput) "▲" else "▼", fontSize = 10.sp, color = Color(0xFF666666))
                    }
                    if (showCustomModelInput) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customModelId,
                            onValueChange = { customModelId = it },
                            label = { Text("Model ID", fontSize = 12.sp) },
                            placeholder = { Text("e.g. gemini-2.0-flash", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                val id = customModelId.trim()
                                if (id.isNotBlank()) {
                                    selectedModel = id
                                    settingsVm.userRepository.userInfo.aiModel = id
                                    settingsVm.userRepository.saveUserInfo()
                                }
                            },
                            enabled = customModelId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) { Text("Use This Model", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Find model IDs at aistudio.google.com → select a model → API reference tab.",
                            fontSize = 10.sp, color = Color(0xFF666666)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Coin cost per message
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Coins per message", fontSize = 13.sp, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { if (coinCost > 0) { coinCost--; settingsVm.userRepository.userInfo.aiCoinCostPerMin = coinCost; settingsVm.userRepository.saveUserInfo() } }) {
                                Text("−", color = Color(0xFF00BCD4))
                            }
                            Text("$coinCost 🪙", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { coinCost++; settingsVm.userRepository.userInfo.aiCoinCostPerMin = coinCost; settingsVm.userRepository.saveUserInfo() }) {
                                Text("+", color = Color(0xFF00BCD4))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // API Key field
                    var apiKeyInput by remember { mutableStateOf(neth.iecal.questphone.core.ai.KaiPrefs.getApiKey(context)) }
                    var showKey by remember { mutableStateOf(false) }
                    Text("API Key", fontSize = 12.sp, color = Color(0xFF888888), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Google AI Studio Key") },
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        visualTransformation = if (showKey)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                androidx.compose.material3.TextButton(onClick = { showKey = !showKey }) {
                                    Text(if (showKey) "Hide" else "Show", fontSize = 10.sp, color = Color(0xFF00BCD4))
                                }
                                androidx.compose.material3.TextButton(onClick = {
                                    neth.iecal.questphone.core.ai.KaiPrefs.saveApiKey(context, apiKeyInput.trim())
                                }) {
                                    Text("Save", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                }
                            }
                        }
                    )
                    Text(
                        "⚠️ Key is saved locally. Do NOT sync if using GitHub sync — it will be visible in your repo. Get a key at aistudio.google.com (free).",
                        fontSize = 10.sp, color = Color(0xFF664400),
                        modifier = Modifier.padding(top = 3.dp)
                    )

                    Spacer(Modifier.height(6.dp))

                    // Assistant package override
                    OutlinedTextField(
                        value = assistantPkg,
                        onValueChange = { assistantPkg = it },
                        label = { Text("Assistant App Package (optional)") },
                        placeholder = { Text("e.g. com.google.android.apps.bard") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            TextButton(onClick = {
                                settingsVm.userRepository.userInfo.aiAssistantPackage = assistantPkg.trim()
                                settingsVm.userRepository.saveUserInfo()
                            }) { Text("Save", fontSize = 11.sp, color = Color(0xFF00BCD4)) }
                        }
                    )
                    Text("Leave blank to use Kai built-in. Set to Gemini/ChatGPT package to override wake word.",
                        fontSize = 10.sp, color = Color(0xFF444444), modifier = Modifier.padding(top = 4.dp))
                }
            }

            item {
                SettingCard(
                    title = "📊 Stats Hub",
                    subtitle = "Screen time, focus history, study tracker, stat charts, coin log, backup"
                ) {
                    TextButton(
                        onClick = { navController.navigate(RootRoute.StatsHub.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Stats Hub →", color = Color(0xFF4CAF50)) }
                }
            }

            item {
                SettingCard(
                    title = "🔕 Notifications & Admin",
                    subtitle = "Block distracting notifications, prevent uninstall"
                ) {
                    TextButton(
                        onClick = { navController.navigate(RootRoute.NotificationBlockerSettings.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Notification Blocker & Device Admin →") }
                }
            }

            item {
                SettingCard(title = "Focus Timer", subtitle = "Pomodoro timer that rewards coins per session") {
                    Button(
                        onClick = { try { navController.navigate(RootRoute.FocusTimer.route) { launchSingleTop = true } } catch (_: Exception) {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0A0A))
                    ) { Text("Open Focus Timer →", color = Color(0xFFEF5350)) }
                }
            }

            item {
                SettingCard(title = "Notifications", subtitle = "Daily briefing, quest reminders, streak warnings") {
                    Button(
                        onClick = { try { navController.navigate(RootRoute.QuestNotifications.route) { launchSingleTop = true } } catch (_: Exception) {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A1A0A))
                    ) { Text("Notification Settings →", color = Color(0xFF4CAF50)) }
                }
            }

            item {
                SettingCard(title = "Stat Settings", subtitle = "Rename Strength / Intelligence / Focus / Discipline") {
                    Button(
                        onClick = { try { navController.navigate(RootRoute.StatSettings.route) { launchSingleTop = true } } catch (_: Exception) {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A1A))
                    ) { Text("Rename Stats →", color = Color(0xFF9C27B0)) }
                }
            }

            item {
                SettingCard(
                    title = "Crash Log",
                    subtitle = "View last crash details for debugging"
                ) {
                    Button(
                        onClick = { try { navController.navigate(RootRoute.CrashLog.route) { launchSingleTop = true } } catch (_: Exception) {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0A0A))
                    ) { Text("View Crash Log", color = Color(0xFFFF6B6B)) }
                }
            }

            // ── GAMEPLAY (now in Play Hub) ─────────────────────────────
            item {
                SettingCard(
                    title = "🎮 Play Hub",
                    subtitle = "Boss battles, quest chains, rival, productivity score, store"
                ) {
                    TextButton(
                        onClick = { navController.navigate(RootRoute.PlayHub.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Play Hub →", color = Color(0xFFFF9800)) }
                }
            }

            // ── DISCIPLINE ─────────────────────────────────────────────────
            item {
                SettingCard(
                    title = "🔒 Discipline",
                    subtitle = "Lockdown escalation, panic button cooldown"
                ) {
                    TextButton(
                        onClick = { navController.navigate(RootRoute.LockdownSettings.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lockdown Escalation Settings →") }
                }
            }

            // ── RPG MODE ──────────────────────────────────────────────────
            item {
                SettingCard(
                    title = "⚔️ RPG Mode",
                    subtitle = "Rename UI labels to RPG terms. Off by default."
                ) {
                    TextButton(
                        onClick = { navController.navigate(RootRoute.RpgSettings.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("RPG Mode Settings →", color = Color(0xFFFFD700)) }

                }
            }

            // ── KAI PERSONALITY ───────────────────────────────────────────
            item {
                SettingCard(
                    title = "🤖 Kai Personality",
                    subtitle = "Friendly Coach, Strict Sensei, Rival, Philosopher, Anime"
                ) {
                    TextButton(
                        onClick = { navController.navigate(RootRoute.KaiPersonality.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose Kai's Personality →") }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
        } // close Column
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
