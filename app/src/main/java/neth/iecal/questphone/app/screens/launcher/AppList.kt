package neth.iecal.questphone.app.screens.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.navigation.LauncherDialogRoutes
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.soloLeveling.GateWarningOverlay
import neth.iecal.questphone.app.screens.soloLeveling.SoloLevelingStorage
import neth.iecal.questphone.app.screens.etc.PluginUnlockBlockerDialog
import neth.iecal.questphone.app.screens.launcher.dialogs.LauncherDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppList(navController: NavController, viewModel: AppListViewModel) {

    val apps by viewModel.filteredApps.collectAsState()
    val showDialog by viewModel.showCoinDialog.collectAsState()
    val selectedPackage by viewModel.selectedPackage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showHideConfirm by viewModel.showHideConfirm.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val context = LocalContext.current

    var longPressedPkg by remember { mutableStateOf("") }
    var longPressedName by remember { mutableStateOf("") }
    var showLongPressMenu by remember { mutableStateOf(false) }
    var showGateWarning by remember { mutableStateOf(false) }
    var gateWarningPkg by remember { mutableStateOf("") }
    var gateWarningName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var pinnedPackages by remember { mutableStateOf(viewModel.getPinnedShortcuts()) }

    // Hide confirm dialog
    if (showHideConfirm.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHideConfirm() },
            title = { Text("Hide App") },
            text = { Text("Hide \"${viewModel.getDisplayName(showHideConfirm)}\" from the launcher? You can unhide it in Settings.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmHideApp(showHideConfirm) }) {
                    Text("Hide", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissHideConfirm() }) { Text("Cancel") }
            }
        )
    }

    // Rename dialog with AI suggestion
    if (showRenameDialog) {
        val scope = rememberCoroutineScope()
        var isAiLoading by remember { mutableStateOf(false) }
        var aiError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename App") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Rename how \"$longPressedName\" appears in your launcher",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Display name") },
                        placeholder = { Text(longPressedName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val rpgMode = viewModel.userRepository.userInfo.rpgModeEnabled
                    val personality = viewModel.userRepository.userInfo.kaiPersonality
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isAiLoading = true
                                aiError = false
                                try {
                                    val styleHint = when {
                                        rpgMode -> "fantasy/RPG theme. Example: Notes→Tome, Maps→Cartographer, Camera→Vision Orb"
                                        personality == "strict" -> "strict, no-nonsense, short"
                                        personality == "rival" -> "competitive, edgy, cool"
                                        personality == "philosopher" -> "stoic, ancient, wise"
                                        personality == "anime" -> "dramatic anime style"
                                        else -> "friendly, modern, creative"
                                    }
                                    val prompt = "App name: $longPressedName. Give it a $styleHint display name. Reply with the name ONLY. No explanation. No punctuation. Max 3 words."
                                    val result = viewModel.gemmaRepository.quickChat(prompt)
                                    val suggestion = result.getOrNull()
                                        ?.replace(Regex("""["'*`]"""), "")
                                        ?.trim()?.take(25) ?: ""
                                    if (suggestion.isNotBlank() && !suggestion.contains("User") && !suggestion.contains("wants")) renameInput = suggestion
                                    else aiError = true
                                } catch (_: Exception) { aiError = true }
                                isAiLoading = false
                            }
                        },
                        enabled = !isAiLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Kai is thinking…")
                        } else {
                            Text("✨ Suggest name with Kai")
                        }
                    }
                    if (aiError) {
                        Text("Kai couldn't suggest a name. Type one manually.",
                            fontSize = 11.sp, color = Color(0xFFE53935))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameApp(longPressedPkg, renameInput)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Long-press menu
    // Gate warning overlay (Solo Leveling mode)
    if (showGateWarning) {
        GateWarningOverlay(
            appName = gateWarningName,
            onProceed = {
                showGateWarning = false
                try { viewModel.onAppClick(gateWarningPkg) } catch (_: Exception) {}
            },
            onCancel = { showGateWarning = false }
        )
    }

    if (showLongPressMenu) {
        AlertDialog(
            onDismissRequest = { showLongPressMenu = false },
            title = { Text(longPressedName, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        onClick = {
                            renameInput = viewModel.getAppRenameIfSet(longPressedPkg) ?: ""
                            showLongPressMenu = false
                            showRenameDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rename", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = {
                            showLongPressMenu = false
                            viewModel.onLongAppClick(longPressedPkg)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hide from launcher", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val isPinned = longPressedPkg in pinnedPackages
                    TextButton(
                        onClick = {
                            viewModel.togglePinnedShortcut(longPressedPkg)
                            pinnedPackages = viewModel.getPinnedShortcuts()
                            showLongPressMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isPinned) "Unpin from Home" else "Pin to Home",
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isPinned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = {
                            showLongPressMenu = false
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", longPressedPkg, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("App Info", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface)
                    }
                    val currentRename = viewModel.getAppRenameIfSet(longPressedPkg)
                    if (currentRename != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton(
                            onClick = {
                                viewModel.renameApp(longPressedPkg, "")
                                showLongPressMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset name", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLongPressMenu = false }) { Text("Close") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
    val minutesPer5Coins by viewModel.minutesPerFiveCoins.collectAsState()
    val areHardLockedQuestsAvailable by viewModel.isHardLockedQuestsToday.collectAsState()

    BackHandler {
        viewModel.onSearchQueryChange("")
        keyboardController?.hide()
        try {
            navController.navigate(RootRoute.HomeScreen.route) {
                popUpTo(RootRoute.HomeScreen.route) { inclusive = false }
                launchSingleTop = true
            }
        } catch (_: Exception) {}
    }

    val coins by viewModel.coins.collectAsState()
    val remainingFreePasses by viewModel.remainingFreePassesToday.collectAsState()
    val listState = rememberLazyListState()

    var pulledDownHard by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                ) {
                    if (!pulledDownHard) {
                        pulledDownHard = true
                        viewModel.onSearchQueryChange("")
                        keyboardController?.hide()
                        try {
                            navController.navigate(RootRoute.HomeScreen.route) {
                                popUpTo(RootRoute.HomeScreen.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        } catch (_: Exception) {}
                    }
                }
                if (available.y != 0f || available.x != 0f) keyboardController?.hide()
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(Unit) {
        pulledDownHard = false
        viewModel.loadApps()
        viewModel.loadHardLockedQuests()
    }

    Scaffold { innerPadding ->
        if (showDialog) {
            LauncherDialog(
                coins = coins,
                onDismiss = { viewModel.dismissDialog() },
                pkgName = selectedPackage,
                rootNavController = navController,
                minutesPerFiveCoins = minutesPer5Coins,
                unlockApp = { viewModel.onConfirmUnlockApp(it) },
                startDestination = when {
                    areHardLockedQuestsAvailable -> LauncherDialogRoutes.ShowAllQuest.route
                    coins >= 5 -> LauncherDialogRoutes.UnlockAppDialog.route
                    else -> LauncherDialogRoutes.LowCoins.route
                },
                remainingFreePasses = remainingFreePasses,
                onFreePassUsed = { viewModel.useFreePass() },
                areHardLockQuestsPresent = areHardLockedQuestsAvailable
            )
        }

        // Plugin Store spec §2: First-Open Distraction Blocker Intercept
        val pendingPluginBlocker by viewModel.pluginStoreRepository.pendingUnlockBlocker.collectAsState()
        pendingPluginBlocker?.let { entry ->
            PluginUnlockBlockerDialog(
                entry = entry,
                currentCoins = coins,
                onDismiss = { viewModel.pluginStoreRepository.dismissUnlockBlocker() },
                onConfirmUnlock = {
                    val unlocked = viewModel.pluginStoreRepository.tryUnlock(
                        entry.packageName, entry.unlockCost, entry.name
                    )
                    viewModel.pluginStoreRepository.dismissUnlockBlocker()
                    if (unlocked) viewModel.launchPluginAfterUnlock(entry.packageName)
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(12.dp),
                contentPadding = innerPadding,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sci-Fi System Header Telemetry
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "【 SYSTEM DIRECTORY 】",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "● ONLINE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = "· ${apps.size} NODES",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Search Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                placeholder = {
                                    Text(
                                        "Search apps...",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                leadingIcon = {
                                    Text(
                                        "🔍",
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        Box {
                                            var showSortMenu by remember { mutableStateOf(false) }

                                            IconButton(onClick = { showSortMenu = true }) {
                                                Text(
                                                    "⁝",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false },
                                                modifier = Modifier
                                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                Text(
                                                    "// SELECT MATRIX SORT",
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "⟨ A–Z ⟩ Alphabetical Protocol",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (sortMode == AppSortMode.ALPHABETICAL) {
                                                            Text(
                                                                "[ ACTIVE ]",
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.setSortMode(AppSortMode.ALPHABETICAL)
                                                        showSortMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "⟨ USAGE ⟩ Recommended / Frequent",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (sortMode == AppSortMode.USAGE) {
                                                            Text(
                                                                "[ ACTIVE ]",
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.setSortMode(AppSortMode.USAGE)
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                    }
                }

                items(apps, key = { it.packageName }) { app ->
                    // Special internal entries use their preset name directly
                    val displayName = when (app.packageName) {
                        "neth.iecal.questphone.people_db"        -> app.name
                        "neth.iecal.questphone.my_life"          -> app.name
                        "neth.iecal.questphone.jarvis"           -> app.name
                        "neth.iecal.questphone.calculator_vault" -> app.name
                        "neth.iecal.questphone.study_tracker"    -> app.name // "⚔️ Ascension Hall"
                        "neth.iecal.questphone.pluginstore"      -> app.name // always "Plugin Store" — not renameable
                        else -> viewModel.getDisplayName(app.packageName).ifBlank { app.name }
                    }

                    val isSpecial = app.packageName.startsWith("neth.iecal.questphone.") || viewModel.isPlugin(app.packageName)
                    val isDistraction = viewModel.isDistraction(app.packageName)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    when (app.packageName) {
                                        "neth.iecal.questphone.people_db" ->
                                            navController.navigate(RootRoute.PeopleDatabase.route)
                                        "neth.iecal.questphone.my_life" ->
                                            navController.navigate(RootRoute.MyLife.route)
                                        "neth.iecal.questphone.jarvis" ->
                                            navController.navigate(RootRoute.Jarvis.route)
                                        "neth.iecal.questphone.calculator_vault" ->
                                            navController.navigate(RootRoute.CalculatorVault.route)
                                        "neth.iecal.questphone.study_tracker" ->
                                            navController.navigate(RootRoute.AscensionHall.route)
                                        "neth.iecal.questphone.pluginstore" ->
                                            navController.navigate(RootRoute.PluginStore.route)
                                        else -> {
                                            if (SoloLevelingStorage.isEnabled(navController.context) &&
                                                SoloLevelingStorage.isGateWarningEnabled(navController.context) &&
                                                viewModel.isDistraction(app.packageName)) {
                                                gateWarningPkg = app.packageName
                                                gateWarningName = displayName
                                                showGateWarning = true
                                            } else {
                                                try { viewModel.onAppClick(app.packageName) } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (app.packageName != "neth.iecal.questphone.people_db" &&
                                        app.packageName != "neth.iecal.questphone.my_life" &&
                                        app.packageName != "neth.iecal.questphone.calculator_vault" &&
                                        app.packageName != "neth.iecal.questphone.study_tracker" &&
                                        app.packageName != "neth.iecal.questphone.pluginstore" &&
                                        app.packageName != "neth.iecal.questphone.notes" &&
                                        app.packageName != "neth.iecal.questphone.flashcards") {
                                        longPressedPkg = app.packageName
                                        longPressedName = displayName
                                        showLongPressMenu = true
                                    }
                                }
                            )
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = if (isSpecial) "◈" else "▸",
                                color = if (isSpecial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = if (isSpecial) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 19.sp
                                ),
                                color = if (isSpecial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (isDistraction) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "DANGER",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars)) }
            }
        }
    }
}
