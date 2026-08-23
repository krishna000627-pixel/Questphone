package neth.iecal.questphone.app.screens.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.navigation.LauncherDialogRoutes
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.soloLeveling.GateWarningOverlay
import neth.iecal.questphone.app.screens.soloLeveling.SoloLevelingStorage
import neth.iecal.questphone.app.screens.launcher.dialogs.LauncherDialog
import nethical.questphone.data.AppInfo

// Fake package names for internal QuestPhone entries
private val fakePackages = setOf(
    "neth.iecal.questphone.people_db",
    "neth.iecal.questphone.my_life",
    "neth.iecal.questphone.jarvis",
    "neth.iecal.questphone.calculator_vault",
    "neth.iecal.questphone.study_tracker"
)

@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName in fakePackages) return@remember null
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) { null }
    }
}

@Composable
fun AppIconOrPlaceholder(packageName: String, size: Int = 42) {
    val icon = rememberAppIcon(packageName)
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size * 0.22f).dp))
        )
    } else {
        // Placeholder circle for internal/unknown entries
        Surface(
            modifier = Modifier.size(size.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f)
        ) {}
    }
}

// Groups a flat list into sections: "#" for non-alpha, then A-Z
private fun groupByLetter(apps: List<AppInfo>): List<Pair<String?, AppInfo>> {
    return apps.map { app ->
        val first = app.name.trim().firstOrNull()?.uppercaseChar()
        val header = if (first != null && first.isLetter()) first.toString() else "#"
        header to app
    }
}

private fun buildSections(apps: List<AppInfo>): List<Any> {
    // Any = String (header) | AppInfo (row)
    val grouped = mutableListOf<Any>()
    var lastHeader = ""
    groupByLetter(apps).forEach { (header, app) ->
        if (header != lastHeader) {
            grouped.add(header ?: "#")
            lastHeader = header ?: "#"
        }
        grouped.add(app)
    }
    return grouped
}

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
    var showGateWarning by remember { mutableStateOf(false) }
    var gateWarningPkg by remember { mutableStateOf("") }
    var gateWarningName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var pinnedPackages by remember { mutableStateOf(viewModel.getPinnedShortcuts()) }
    var showSearch by remember { mutableStateOf(false) }

    // Build sections: only when not searching
    val sections = remember(apps, searchQuery) {
        if (searchQuery.isNotEmpty()) emptyList() else buildSections(apps)
    }

    // A-Z letters present in current list (for sidebar)
    val presentLetters = remember(sections) {
        sections.filterIsInstance<String>().distinct()
    }

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

    // Rename dialog
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
                        Text("Kai couldn't suggest a name. Type one manually.", fontSize = 11.sp, color = Color(0xFFE53935))
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

    // Gate warning
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

    // Long-press menu
    if (showLongPressMenu) {
        AlertDialog(
            onDismissRequest = { showLongPressMenu = false },
            title = { Text(longPressedName, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        renameInput = viewModel.getAppRenameIfSet(longPressedPkg) ?: ""
                        showLongPressMenu = false
                        showRenameDialog = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Rename", modifier = Modifier.fillMaxWidth(), color = Color.White)
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    TextButton(onClick = {
                        showLongPressMenu = false
                        viewModel.onLongAppClick(longPressedPkg)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Hide from launcher", modifier = Modifier.fillMaxWidth(), color = Color.White)
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    val isPinned = longPressedPkg in pinnedPackages
                    TextButton(onClick = {
                        viewModel.togglePinnedShortcut(longPressedPkg)
                        pinnedPackages = viewModel.getPinnedShortcuts()
                        showLongPressMenu = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (isPinned) "Unpin from Home" else "📌 Pin to Home",
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isPinned) Color(0xFFFF9800) else Color(0xFF4CAF50)
                        )
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    TextButton(onClick = {
                        showLongPressMenu = false
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", longPressedPkg, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {}
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("App Info", modifier = Modifier.fillMaxWidth(), color = Color.White)
                    }
                    val currentRename = viewModel.getAppRenameIfSet(longPressedPkg)
                    if (currentRename != null) {
                        HorizontalDivider(color = Color(0xFF2A2A2A))
                        TextButton(onClick = {
                            viewModel.renameApp(longPressedPkg, "")
                            showLongPressMenu = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Reset name", modifier = Modifier.fillMaxWidth(), color = Color(0xFF888888), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLongPressMenu = false }) { Text("Close") } },
            containerColor = Color(0xFF0D0D0D),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val minutesPer5Coins by viewModel.minutesPerFiveCoins.collectAsState()
    val areHardLockedQuestsAvailable by viewModel.isHardLockedQuestsToday.collectAsState()
    val coins by viewModel.coins.collectAsState()
    val remainingFreePasses by viewModel.remainingFreePassesToday.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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

    BackHandler {
        viewModel.onSearchQueryChange("")
        keyboardController?.hide()
        showSearch = false
        try {
            navController.navigate(RootRoute.HomeScreen.route) {
                popUpTo(RootRoute.HomeScreen.route) { inclusive = false }
                launchSingleTop = true
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        pulledDownHard = false
        viewModel.loadApps()
        viewModel.loadHardLockedQuests()
    }

    // ── Search overlay ────────────────────────────────────────────────────────
    if (showSearch) {
        LaunchedEffect(Unit) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // ── Root: transparent so wallpaper shows through ──────────────────────────
    Scaffold(containerColor = Color.Transparent) { innerPadding ->
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
            // ── Search overlay ────────────────────────────────────────────────
            if (showSearch) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { showSearch = false; viewModel.onSearchQueryChange(""); keyboardController?.hide() } }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp)
                        .padding(top = innerPadding.calculateTopPadding() + 16.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        placeholder = { Text("Search apps…", color = Color.White.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Color.White.copy(alpha = 0.7f)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, "Clear", tint = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                            focusedBorderColor = Color.White.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(apps, key = { it.packageName }) { app ->
                                val displayName = when (app.packageName) {
                                    "neth.iecal.questphone.people_db" -> app.name
                                    "neth.iecal.questphone.my_life" -> app.name
                                    "neth.iecal.questphone.jarvis" -> app.name
                                    "neth.iecal.questphone.calculator_vault" -> app.name
                                    "neth.iecal.questphone.study_tracker" -> app.name
                                    else -> viewModel.getDisplayName(app.packageName).ifBlank { app.name }
                                }
                                AppRow(
                                    app = app,
                                    displayName = displayName,
                                    navController = navController,
                                    viewModel = viewModel,
                                    onLongPress = { pkg, name ->
                                        if (pkg !in fakePackages) {
                                            longPressedPkg = pkg
                                            longPressedName = name
                                            showLongPressMenu = true
                                        }
                                    },
                                    onGateWarning = { pkg, name ->
                                        gateWarningPkg = pkg
                                        gateWarningName = name
                                        showGateWarning = true
                                    }
                                )
                            }
                        }
                    }
                }
                return@Scaffold
            }

            // ── Main Niagara-style list ───────────────────────────────────────
            Row(modifier = Modifier.fillMaxSize()) {
                // App list (left side, leaves space for A-Z sidebar)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .consumeWindowInsets(innerPadding)
                        .padding(start = 20.dp, end = 4.dp),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 24.dp,
                        bottom = innerPadding.calculateBottomPadding() + 80.dp
                    ),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    sections.forEach { item ->
                        when (item) {
                            is String -> {
                                // Letter header
                                stickyHeader(key = "header_$item") {
                                    Text(
                                        text = item,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 18.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            is AppInfo -> {
                                item(key = item.packageName) {
                                    val displayName = when (item.packageName) {
                                        "neth.iecal.questphone.people_db" -> item.name
                                        "neth.iecal.questphone.my_life" -> item.name
                                        "neth.iecal.questphone.jarvis" -> item.name
                                        "neth.iecal.questphone.calculator_vault" -> item.name
                                        "neth.iecal.questphone.study_tracker" -> item.name
                                        else -> viewModel.getDisplayName(item.packageName).ifBlank { item.name }
                                    }
                                    AppRow(
                                        app = item,
                                        displayName = displayName,
                                        navController = navController,
                                        viewModel = viewModel,
                                        onLongPress = { pkg, name ->
                                            if (pkg !in fakePackages) {
                                                longPressedPkg = pkg
                                                longPressedName = name
                                                showLongPressMenu = true
                                            }
                                        },
                                        onGateWarning = { pkg, name ->
                                            gateWarningPkg = pkg
                                            gateWarningName = name
                                            showGateWarning = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // A-Z sidebar
                if (searchQuery.isEmpty() && presentLetters.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight()
                            .padding(vertical = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        presentLetters.forEach { letter ->
                            Text(
                                text = letter,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .pointerInput(letter) {
                                        detectTapGestures {
                                            scope.launch {
                                                val idx = sections.indexOf(letter)
                                                if (idx >= 0) listState.animateScrollToItem(idx)
                                            }
                                        }
                                    }
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Search button (bottom-right, Niagara style)
            IconButton(
                onClick = { showSearch = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 28.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp
                    )
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    displayName: String,
    navController: NavController,
    viewModel: AppListViewModel,
    onLongPress: (String, String) -> Unit,
    onGateWarning: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    when (app.packageName) {
                        "neth.iecal.questphone.people_db" -> navController.navigate(RootRoute.PeopleDatabase.route)
                        "neth.iecal.questphone.my_life" -> navController.navigate(RootRoute.MyLife.route)
                        "neth.iecal.questphone.jarvis" -> navController.navigate(RootRoute.Jarvis.route)
                        "neth.iecal.questphone.calculator_vault" -> navController.navigate(RootRoute.CalculatorVault.route)
                        "neth.iecal.questphone.study_tracker" -> navController.navigate(RootRoute.AscensionHall.route)
                        else -> {
                            if (SoloLevelingStorage.isEnabled(navController.context) &&
                                SoloLevelingStorage.isGateWarningEnabled(navController.context) &&
                                viewModel.isDistraction(app.packageName)
                            ) {
                                onGateWarning(app.packageName, displayName)
                            } else {
                                try { viewModel.onAppClick(app.packageName) } catch (_: Exception) {}
                            }
                        }
                    }
                },
                onLongClick = { onLongPress(app.packageName, displayName) }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconOrPlaceholder(packageName = app.packageName, size = 42)
        Spacer(Modifier.width(16.dp))
        Text(
            text = displayName,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            letterSpacing = 0.2.sp
        )
    }
}
