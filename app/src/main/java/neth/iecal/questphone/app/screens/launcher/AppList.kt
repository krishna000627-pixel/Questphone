package neth.iecal.questphone.app.screens.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.navigation.LauncherDialogRoutes
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.launcher.dialogs.LauncherDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppList(navController: NavController, viewModel: AppListViewModel) {

    val apps by viewModel.filteredApps.collectAsState()
    val showDialog by viewModel.showCoinDialog.collectAsState()
    val selectedPackage by viewModel.selectedPackage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showHideConfirm by viewModel.showHideConfirm.collectAsState()
    val context = LocalContext.current

    var longPressedPkg by remember { mutableStateOf("") }
    var longPressedName by remember { mutableStateOf("") }
    var showLongPressMenu by remember { mutableStateOf(false) }
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
                    Text("Hide", color = Color(0xFFE57373))
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
                                        ?.replace(Regex("["'*`]"), "")
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
                        Text("Rename", modifier = Modifier.fillMaxWidth(), color = Color.White)
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    TextButton(
                        onClick = {
                            showLongPressMenu = false
                            viewModel.onLongAppClick(longPressedPkg)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hide from launcher", modifier = Modifier.fillMaxWidth(), color = Color.White)
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
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
                            if (isPinned) "Unpin from Home" else "📌 Pin to Home",
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isPinned) Color(0xFFFF9800) else Color(0xFF4CAF50)
                        )
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
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
                        Text("App Info", modifier = Modifier.fillMaxWidth(), color = Color.White)
                    }
                    val currentRename = viewModel.getAppRenameIfSet(longPressedPkg)
                    if (currentRename != null) {
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                        TextButton(
                            onClick = {
                                viewModel.renameApp(longPressedPkg, "")
                                showLongPressMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset name", modifier = Modifier.fillMaxWidth(), color = Color(0xFF888888), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLongPressMenu = false }) { Text("Close") }
            },
            containerColor = Color(0xFF0D0D0D),
            titleContentColor = Color.White,
            textContentColor = Color.White
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
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text("Search Apps") },
                        placeholder = { Text("Type app name...") },
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true
                    )
                    Spacer(Modifier.size(4.dp))
                }

                items(apps, key = { it.packageName }) { app ->
                    val displayName = viewModel.getDisplayName(app.packageName).ifBlank { app.name }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    try { viewModel.onAppClick(app.packageName) } catch (_: Exception) {}
                                },
                                onLongClick = {
                                    longPressedPkg = app.packageName
                                    longPressedName = displayName
                                    showLongPressMenu = true
                                }
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)
                        )
                        // Real name deliberately hidden — renamed app shows only custom name
                    }
                }

                item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars)) }
            }
        }
    }
}
