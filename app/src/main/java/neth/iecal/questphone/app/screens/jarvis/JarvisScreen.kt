package neth.iecal.questphone.app.screens.jarvis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import neth.iecal.questphone.app.screens.launcher.AppListViewModel
import nethical.questphone.data.UserInfo
import java.text.SimpleDateFormat
import java.util.*

// ── Color palette ─────────────────────────────────────────────────────────────
private val JarvisBg      = Color(0xFF0A0A0F)
private val JarvisAccent  = Color(0xFF00BFFF)
private val JarvisDim     = Color(0xFF1A1A2E)
private val JarvisCard    = Color(0xFF111122)
private val UserBubble    = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisScreen(navController: NavController, userInfo: UserInfo) {
    val ctx = LocalContext.current
    val appListViewModel: AppListViewModel = hiltViewModel()
    val showCoinDialog by appListViewModel.showCoinDialog.collectAsState()
    val selectedPackage by appListViewModel.selectedPackage.collectAsState()
    val coins by appListViewModel.coins.collectAsState()
    val minutesPer5Coins by appListViewModel.minutesPerFiveCoins.collectAsState()
    val areHardLockedQuestsAvailable by appListViewModel.isHardLockedQuestsToday.collectAsState()
    val remainingFreePasses by appListViewModel.remainingFreePassesToday.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }  // 0=chat 1=commands 2=settings

    if (showCoinDialog) {
        neth.iecal.questphone.app.screens.launcher.dialogs.LauncherDialog(
            coins = coins,
            onDismiss = { appListViewModel.dismissDialog() },
            pkgName = selectedPackage,
            rootNavController = navController,
            minutesPerFiveCoins = minutesPer5Coins,
            unlockApp = { appListViewModel.onConfirmUnlockApp(it) },
            startDestination = when {
                areHardLockedQuestsAvailable -> neth.iecal.questphone.app.navigation.LauncherDialogRoutes.ShowAllQuest.route
                coins >= 5 -> neth.iecal.questphone.app.navigation.LauncherDialogRoutes.UnlockAppDialog.route
                else -> neth.iecal.questphone.app.navigation.LauncherDialogRoutes.LowCoins.route
            },
            remainingFreePasses = remainingFreePasses,
            onFreePassUsed = { appListViewModel.useFreePass() },
            areHardLockQuestsPresent = areHardLockedQuestsAvailable
        )
    }

    Scaffold(
        containerColor = JarvisBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(JarvisAccent.copy(alpha = 0.15f)),
                            Alignment.Center
                        ) { Text("⚡", fontSize = 18.sp) }
                        Column {
                            Text("Jarvis", fontWeight = FontWeight.Black,
                                color = JarvisAccent, letterSpacing = 2.sp)
                            Text("Offline Assistant", fontSize = 10.sp,
                                color = JarvisAccent.copy(alpha = 0.5f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = JarvisAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JarvisBg
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = JarvisDim, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, null) },
                    label = { Text("Chat", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisAccent,
                        selectedTextColor = JarvisAccent,
                        indicatorColor = JarvisAccent.copy(alpha = 0.15f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, null) },
                    label = { Text("Commands", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisAccent,
                        selectedTextColor = JarvisAccent,
                        indicatorColor = JarvisAccent.copy(alpha = 0.15f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = JarvisAccent,
                        selectedTextColor = JarvisAccent,
                        indicatorColor = JarvisAccent.copy(alpha = 0.15f),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> JarvisChatTab(userInfo = userInfo, navController = navController, appListViewModel = appListViewModel)
                1 -> JarvisCommandsTab()
                2 -> JarvisSettingsTab(navController = navController)
            }
        }
    }
}

// ── Chat Tab ──────────────────────────────────────────────────────────────────

@Composable
private fun JarvisChatTab(userInfo: UserInfo, navController: NavController, appListViewModel: AppListViewModel) {
    val ctx = LocalContext.current
    var messages by remember { mutableStateOf(JarvisStorage.loadHistory(ctx)) }
    var input by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val engine = remember {
        JarvisEngine(
            ctx = ctx,
            userInfo = userInfo,
            navCallback = { route -> navController.navigate(route) },
            launchAppCallback = { pkg -> appListViewModel.onAppClick(pkg) }
        )
    }

    fun send() {
        val text = input.trim()
        if (text.isBlank()) return
        input = ""
        isProcessing = true

        val userMsg = JarvisMessage(text = text, isUser = true)
        JarvisStorage.appendMessage(ctx, userMsg)
        messages = JarvisStorage.loadHistory(ctx)

        val response = engine.process(text)
        val botMsg = JarvisMessage(text = response.text, isUser = false)
        JarvisStorage.appendMessage(ctx, botMsg)

        response.action?.let { action ->
            when (action) {
                is JarvisAction.LaunchApp -> appListViewModel.onAppClick(action.packageName)
                is JarvisAction.Navigate -> navController.navigate(action.route)
                is JarvisAction.OpenUrl -> {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(action.url)).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                is JarvisAction.CopyToClipboard -> {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Jarvis", action.text))
                }
                is JarvisAction.ShareText -> {
                    ctx.startActivity(android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, action.text)
                        }, "Share via"
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
            }
        }

        messages = JarvisStorage.loadHistory(ctx)
        isProcessing = false
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(JarvisBg)) {
        if (messages.isEmpty()) {
            // Empty state
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("⚡", fontSize = 64.sp)
                    Text("JARVIS", fontWeight = FontWeight.Black, fontSize = 24.sp,
                        color = JarvisAccent, letterSpacing = 4.sp,
                        fontFamily = FontFamily.Monospace)
                    Text("Offline. Instant. Always ready.",
                        fontSize = 13.sp, color = JarvisAccent.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    // Quick suggestion chips
                    val suggestions = listOf("my stats", "what time", "my dasha",
                        "motivate me", "help", "battery")
                    suggestions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { s ->
                                SuggestionChip(
                                    onClick = { input = s },
                                    label = { Text(s, fontSize = 11.sp, color = JarvisAccent) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = JarvisAccent.copy(alpha = 0.1f)
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = JarvisAccent.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(messages, key = { it.id }) { msg ->
                    JarvisBubble(msg)
                }
                if (isProcessing) item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(JarvisAccent.copy(alpha = 0.15f)),
                            Alignment.Center
                        ) { Text("⚡", fontSize = 14.sp) }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(JarvisCard)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = JarvisAccent, strokeWidth = 2.dp
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        // Input bar
        Row(
            Modifier.fillMaxWidth()
                .background(JarvisDim)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask Jarvis...", color = Color.Gray, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = JarvisAccent,
                    unfocusedBorderColor = JarvisAccent.copy(alpha = 0.3f),
                    cursorColor = JarvisAccent,
                    focusedContainerColor = JarvisCard,
                    unfocusedContainerColor = JarvisCard
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            IconButton(
                onClick = { send() },
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(if (input.isBlank()) JarvisAccent.copy(0.2f) else JarvisAccent)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null,
                    tint = if (input.isBlank()) Color.Gray else Color.Black,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun JarvisBubble(msg: JarvisMessage) {
    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(JarvisAccent.copy(alpha = 0.15f))
                    .align(Alignment.Bottom),
                Alignment.Center
            ) { Text("⚡", fontSize = 14.sp) }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (msg.isUser) 16.dp else 4.dp,
                        bottomEnd = if (msg.isUser) 4.dp else 16.dp
                    ))
                    .background(if (msg.isUser) UserBubble else JarvisCard)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    msg.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = if (!msg.isUser) FontFamily.Monospace else FontFamily.Default
                )
            }
            Text(time, fontSize = 9.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
        if (msg.isUser) Spacer(Modifier.width(8.dp))
    }
}

// ── Commands Tab ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisCommandsTab() {
    val ctx = LocalContext.current
    var commands by remember { mutableStateOf(JarvisStorage.loadCommands(ctx)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCmd by remember { mutableStateOf<CustomCommand?>(null) }
    var search by remember { mutableStateOf("") }

    if (showAddDialog || editingCmd != null) {
        CommandEditorDialog(
            initial = editingCmd,
            onSave = { cmd ->
                JarvisStorage.upsertCommand(ctx, cmd)
                commands = JarvisStorage.loadCommands(ctx)
                showAddDialog = false
                editingCmd = null
            },
            onDismiss = { showAddDialog = false; editingCmd = null }
        )
    }

    Column(Modifier.fillMaxSize().background(JarvisBg)) {
        // Search
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text("Search commands...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = JarvisAccent) },
            trailingIcon = {
                if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                    Icon(Icons.Default.Clear, null, tint = Color.Gray)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = JarvisAccent,
                unfocusedBorderColor = JarvisAccent.copy(alpha = 0.3f),
                cursorColor = JarvisAccent,
                focusedContainerColor = JarvisCard,
                unfocusedContainerColor = JarvisCard
            ),
            shape = RoundedCornerShape(12.dp)
        )

        val filtered = commands.filter { cmd ->
            search.isBlank() || cmd.triggers.any { it.contains(search, ignoreCase = true) } ||
            cmd.response.contains(search, ignoreCase = true) ||
            cmd.category.contains(search, ignoreCase = true)
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { BuiltinCommandsSection() }
            item {
                Text("MY COMMANDS",
                    fontWeight = FontWeight.Black, fontSize = 11.sp,
                    color = JarvisAccent.copy(alpha = 0.6f), letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⚡", fontSize = 40.sp)
                            Text("No custom commands yet",
                                color = Color.Gray, fontSize = 14.sp)
                            Text("Tap + to add your first command",
                                color = JarvisAccent.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { cmd ->
                    CommandCard(
                        cmd = cmd,
                        onEdit = { editingCmd = cmd },
                        onDelete = {
                            JarvisStorage.deleteCommand(ctx, cmd.id)
                            commands = JarvisStorage.loadCommands(ctx)
                        },
                        onToggle = {
                            JarvisStorage.upsertCommand(ctx, cmd.copy(isEnabled = !cmd.isEnabled))
                            commands = JarvisStorage.loadCommands(ctx)
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

    Box(Modifier.fillMaxSize(), Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.padding(20.dp),
            containerColor = JarvisAccent,
            contentColor = Color.Black
        ) { Icon(Icons.Default.Add, null) }
    }
}

// Built-in commands reference data
private val BUILTIN_COMMANDS = listOf(
    "⏰ Time & Date" to listOf(
        "what time" to "Current time",
        "what date / today's date" to "Today's date",
        "my streak" to "Current streak",
        "longest streak" to "Best streak record"
    ),
    "⚔️ Stats & Level" to listOf(
        "my level / what level" to "Your current level",
        "my xp / how much xp" to "XP progress",
        "my coins / coin balance" to "Coin balance",
        "my stats / show stats" to "All stat points",
        "strength / intelligence / focus stat / discipline" to "Individual stat",
        "highest stat / weakest stat" to "Best/worst stat",
        "level up / how close level" to "Level-up progress"
    ),
    "🔮 Spiritual" to listOf(
        "my dasha / current dasha" to "Current Mahadasha",
        "when dasha end" to "Dasha end date",
        "my lagna / ascendant" to "Lagna sign",
        "my rashi / moon sign" to "Rashi",
        "my nakshatra / birth star" to "Nakshatra",
        "my dharma / life purpose" to "Dharma from My Life",
        "my gotra / kuldevi" to "Gotra & Kuldevi",
        "life path / numerology" to "Life path number"
    ),
    "📖 My Life" to listOf(
        "my struggles" to "Current struggles",
        "my mantras / mantra list" to "Mantra list",
        "read mantra / first mantra" to "Reads first mantra",
        "today journal" to "Today's journal entry",
        "last journal / recent journal" to "Latest journal entry",
        "5 year / my vision" to "5-year vision",
        "my lineage / my ancestors" to "Lineage info"
    ),
    "👥 People" to listOf(
        "how many people / allies count" to "Ally count",
        "my best friends" to "Best friends list",
        "my family / family list" to "Family members"
    ),
    "🧭 Navigate" to listOf(
        "go home / home screen" to "Go to HomeScreen",
        "go to store / open store" to "Open Emporium",
        "go to stats / stats hub" to "Open Stats Hub",
        "go to people / open allies" to "Open People",
        "my life / open my life" to "Open My Life",
        "open settings" to "Launcher Settings",
        "open focus timer / pomodoro" to "Focus Timer",
        "open kai / kai chat" to "Open Kai AI",
        "rpg settings / open rpg" to "RPG Settings"
    ),
    "📱 Apps" to listOf(
        "open [app name]" to "Launch any app",
        "launch [app name]" to "Launch any app",
        "start [app name]" to "Launch any app"
    ),
    "🎲 Fun & Utility" to listOf(
        "roll dice" to "Random number 1-6",
        "flip coin" to "Heads or tails",
        "random quest" to "Suggest a quest idea",
        "motivate me / inspiration" to "Motivational message",
        "daily affirmation" to "Positive affirmation",
        "calculate / what is [math]" to "Math calculation",
        "android version / device info" to "Device info",
        "gate warning / arise" to "Solo Leveling gate scene"
    ),
    "ℹ️ Help" to listOf(
        "help / what can you do" to "Show command overview",
        "commands list" to "This screen"
    )
)

@Composable
private fun BuiltinCommandsSection() {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .background(JarvisCard, RoundedCornerShape(12.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📋 Built-in Commands (${BUILTIN_COMMANDS.sumOf { it.second.size }})",
                color = JarvisAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(if (expanded) "▲" else "▼", color = Color.Gray, fontSize = 12.sp)
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)) {
                BUILTIN_COMMANDS.forEach { (category, cmds) ->
                    Text(category, color = JarvisAccent.copy(alpha = 0.7f),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                    cmds.forEach { (trigger, desc) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(JarvisCard.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\"$trigger\"",
                                color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.weight(1f))
                            Text(desc, color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(
    cmd: CustomCommand,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete command?") },
            text = { Text("\"${cmd.triggers.firstOrNull() ?: ""}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            containerColor = JarvisCard
        )
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCard)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        cmd.triggers.joinToString(" / "),
                        fontWeight = FontWeight.Bold, color = JarvisAccent,
                        fontSize = 14.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(cmd.action.name.lowercase().replace("_", " "),
                        fontSize = 10.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cmd.usageCount > 0) {
                        Text("×${cmd.usageCount}", fontSize = 10.sp,
                            color = JarvisAccent.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 8.dp))
                    }
                    Switch(
                        checked = cmd.isEnabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisBg,
                            checkedTrackColor = JarvisAccent
                        )
                    )
                }
            }
            if (cmd.response.isNotBlank()) {
                Text("→ \"${cmd.response.take(80)}\"",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
            Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                IconButton(onClick = onEdit, Modifier.size(30.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(14.dp), tint = Color.Gray)
                }
                IconButton(onClick = { showDeleteConfirm = true }, Modifier.size(30.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = Color.Red.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ── Command Editor Dialog ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandEditorDialog(
    initial: CustomCommand?,
    onSave: (CustomCommand) -> Unit,
    onDismiss: () -> Unit
) {
    var triggersText by remember { mutableStateOf(initial?.triggers?.joinToString(", ") ?: "") }
    var action by remember { mutableStateOf(initial?.action ?: CommandAction.SPEAK) }
    var actionParam by remember { mutableStateOf(initial?.actionParam ?: "") }
    var response by remember { mutableStateOf(initial?.response ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Custom") }
    var showActionMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisCard,
        title = {
            Text(if (initial == null) "New Command" else "Edit Command",
                color = JarvisAccent, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = triggersText, onValueChange = { triggersText = it },
                    label = { Text("Triggers (comma separated)", color = Color.Gray) },
                    placeholder = { Text("open notes, my notes, notes", color = Color.DarkGray) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = jarvisFieldColors()
                )

                // Action type selector
                Box {
                    OutlinedTextField(
                        value = action.name.lowercase().replace("_", " "),
                        onValueChange = {},
                        label = { Text("Action Type", color = Color.Gray) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { showActionMenu = true },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null, tint = JarvisAccent)
                        },
                        colors = jarvisFieldColors()
                    )
                    DropdownMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { showActionMenu = false },
                        modifier = Modifier.background(JarvisCard)
                    ) {
                        CommandAction.entries.forEach { a ->
                            DropdownMenuItem(
                                text = { Text(a.name.lowercase().replace("_", " "),
                                    color = if (a == action) JarvisAccent else Color.White) },
                                onClick = { action = a; showActionMenu = false }
                            )
                        }
                    }
                }

                // Action param hint changes by type
                val paramHint = when (action) {
                    CommandAction.LAUNCH_APP -> "Package name (e.g. com.google.android.youtube)"
                    CommandAction.NAVIGATE -> "Route (e.g. home_screen/)"
                    CommandAction.OPEN_URL -> "URL (e.g. https://google.com)"
                    CommandAction.CLIPBOARD, CommandAction.SHARE, CommandAction.SPEAK -> "Text to use"
                    CommandAction.RANDOM -> "Options separated by | (opt1|opt2|opt3)"
                    CommandAction.NOTIFICATION -> "Notification text"
                    else -> "Parameter"
                }
                OutlinedTextField(
                    value = actionParam, onValueChange = { actionParam = it },
                    label = { Text("Action Parameter", color = Color.Gray) },
                    placeholder = { Text(paramHint, color = Color.DarkGray) },
                    singleLine = action != CommandAction.RANDOM,
                    modifier = Modifier.fillMaxWidth(),
                    colors = jarvisFieldColors()
                )

                OutlinedTextField(
                    value = response, onValueChange = { response = it },
                    label = { Text("Response Text", color = Color.Gray) },
                    placeholder = { Text("What Jarvis says back", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = jarvisFieldColors()
                )

                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text("Category", color = Color.Gray) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = jarvisFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (triggersText.isNotBlank()) {
                        val triggers = triggersText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        onSave(CustomCommand(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            triggers = triggers, action = action,
                            actionParam = actionParam.trim(), response = response.trim(),
                            category = category.trim(),
                            usageCount = initial?.usageCount ?: 0
                        ))
                    }
                },
                enabled = triggersText.isNotBlank()
            ) { Text("Save", color = JarvisAccent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

// ── Settings Tab ──────────────────────────────────────────────────────────────

@Composable
private fun JarvisSettingsTab(navController: androidx.navigation.NavController) {
    val ctx = LocalContext.current
    var prefs by remember { mutableStateOf(JarvisStorage.loadJarvisPrefs(ctx)) }

    fun save(p: JarvisPrefs) { prefs = p; JarvisStorage.saveJarvisPrefs(ctx, p) }

    LazyColumn(
        Modifier.fillMaxSize().background(JarvisBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            Text("JARVIS SETTINGS", fontWeight = FontWeight.Black, fontSize = 11.sp,
                color = JarvisAccent.copy(alpha = 0.6f), letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 4.dp))
        }

        item {
            JarvisSettingCard("Text-to-Speech", "Speak responses aloud via device TTS") {
                Switch(
                    checked = prefs.ttsEnabled,
                    onCheckedChange = { save(prefs.copy(ttsEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = JarvisAccent, checkedThumbColor = JarvisBg
                    )
                )
            }
        }

        item {
            JarvisSettingCard("Fuzzy Matching", "Match commands even with typos or partial phrases") {
                Switch(
                    checked = prefs.fuzzyMatch,
                    onCheckedChange = { save(prefs.copy(fuzzyMatch = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = JarvisAccent, checkedThumbColor = JarvisBg
                    )
                )
            }
        }

        item {
            JarvisSettingCard("Show Suggestions", "Show quick command chips on empty chat") {
                Switch(
                    checked = prefs.showSuggestions,
                    onCheckedChange = { save(prefs.copy(showSuggestions = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = JarvisAccent, checkedThumbColor = JarvisBg
                    )
                )
            }
        }

        item {
            Text("PERSONALITY", fontWeight = FontWeight.Black, fontSize = 11.sp,
                color = JarvisAccent.copy(alpha = 0.6f), letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }

        val personalities = listOf(
            "normal" to "Direct and helpful",
            "system" to "【SYSTEM】Solo Leveling cold voice",
            "sensei" to "Strict. No sugarcoating.",
            "stoic" to "Stoic. Calm. Quotes Marcus Aurelius."
        )
        personalities.forEach { (key, desc) ->
            item {
                Card(
                    Modifier.fillMaxWidth().clickable { save(prefs.copy(personality = key)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (prefs.personality == key)
                            JarvisAccent.copy(alpha = 0.15f) else JarvisCard
                    ),
                    border = if (prefs.personality == key)
                        androidx.compose.foundation.BorderStroke(1.dp, JarvisAccent.copy(alpha = 0.5f))
                    else null
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(key.replaceFirstChar { it.uppercase() },
                                fontWeight = FontWeight.SemiBold,
                                color = if (prefs.personality == key) JarvisAccent else Color.White)
                            Text(desc, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (prefs.personality == key) {
                            Icon(Icons.Default.Check, null,
                                tint = JarvisAccent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        item {\n            Text(\"DATA\", fontWeight = FontWeight.Black, fontSize = 11.sp,
                color = JarvisAccent.copy(alpha = 0.6f), letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }

        item {
            val ctx2 = LocalContext.current
            // Render Sync Status
            val syncEnabled = neth.iecal.questphone.backed.sync.RenderSyncPrefs.isEnabled(ctx2)
            val lastSync = neth.iecal.questphone.backed.sync.RenderSyncPrefs.getLastSyncAt(ctx2)
            val lastSyncStr = if (lastSync == 0L) "Never"
            else java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastSync))
            JarvisSettingCard(
                "Render Auto-Sync",
                if (syncEnabled) "✅ ON · Last: $lastSyncStr"
                else "⚠️ OFF · Go to Profile → Auto-Sync to configure"
            ) {
                Surface(
                    color = if (syncEnabled) Color(0xFF1B5E20) else JarvisCard,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Text(
                        if (syncEnabled) "ON" else "OFF",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = if (syncEnabled) Color(0xFF69F0AE) else Color.Gray,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {\n            Text(\"AI ASSISTANT\", fontWeight = FontWeight.Black, fontSize = 11.sp,
                color = JarvisAccent.copy(alpha = 0.6f), letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }

        item {
            val ctx2 = LocalContext.current
            var jarvisDefault by remember {
                mutableStateOf(
                    ctx2.getSharedPreferences("jarvis_prefs", android.content.Context.MODE_PRIVATE)
                        .getBoolean("use_jarvis_as_default", false)
                )
            }
            JarvisSettingCard(
                "Use Jarvis as Default AI",
                "Tapping the Kai icon on homescreen opens Jarvis instead"
            ) {
                Switch(
                    checked = jarvisDefault,
                    onCheckedChange = {
                        jarvisDefault = it
                        ctx2.getSharedPreferences("jarvis_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("use_jarvis_as_default", it).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = JarvisAccent, checkedThumbColor = JarvisBg
                    )
                )
            }
        }

        item {
            val ctx2 = LocalContext.current
            OutlinedButton(
                onClick = { JarvisStorage.clearHistory(ctx2) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear Chat History")
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun JarvisSettingCard(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCard)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(),
            Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
            trailing()
        }
    }
}

@Composable
private fun jarvisFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = JarvisAccent,
    unfocusedBorderColor = JarvisAccent.copy(alpha = 0.3f),
    cursorColor = JarvisAccent,
    focusedContainerColor = JarvisBg,
    unfocusedContainerColor = JarvisBg,
    focusedLabelColor = JarvisAccent,
    unfocusedLabelColor = Color.Gray
)
