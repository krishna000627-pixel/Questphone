package neth.iecal.questphone.app.screens.launcher

import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import neth.iecal.questphone.BuildConfig
import neth.iecal.questphone.R
import neth.iecal.questphone.app.navigation.LauncherDialogRoutes
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.components.TopBarActions
import neth.iecal.questphone.app.screens.launcher.HomeStatusBar
import neth.iecal.questphone.core.rpg.AppNameResolver
import neth.iecal.questphone.core.rpg.RpgTerms
import neth.iecal.questphone.app.screens.launcher.dialogs.DonationsDialog
import neth.iecal.questphone.app.screens.launcher.dialogs.LauncherDialog
import neth.iecal.questphone.app.screens.quest.setup.deep_focus.SelectAppsDialog
import neth.iecal.questphone.app.theme.LocalCustomTheme
import neth.iecal.questphone.app.theme.smoothRed
import neth.iecal.questphone.core.focus.*
import neth.iecal.questphone.core.services.LockScreenService
import neth.iecal.questphone.core.services.performLockScreenAction
import neth.iecal.questphone.core.utils.managers.QuestHelper
import nethical.questphone.core.core.utils.managers.isAccessibilityServiceEnabled
import nethical.questphone.core.core.utils.managers.openAccessibilityServiceScreen
import android.content.Intent as AndroidIntent
import java.lang.reflect.Method

data class SidePanelItem(
    val icon: Int,
    val onClick: () -> Unit,
    val contentDesc: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    navController: NavController?,
    viewModel: HomeScreenViewModel,
) {
    val context = LocalContext.current

    val time by viewModel.time
    val questList by viewModel.questList.collectAsState()
    val completedQuests by viewModel.completedQuests.collectAsState()
    val shortcuts = viewModel.shortcuts
    val tempShortcuts = viewModel.tempShortcuts
    val coins by viewModel.coins.collectAsState()
    val streak by viewModel.currentStreak.collectAsState()
    val homeQuestCount by viewModel.homeQuestCount.collectAsState()
    var isAppSelectorVisible by remember { mutableStateOf(false) }

    val allSidePanelItems = remember {
        listOf(
            SidePanelItem(R.drawable.customize, { navController?.navigate(RootRoute.Customize.route) { launchSingleTop = true } }, "Customize"),
            SidePanelItem(R.drawable.baseline_gamepad_24, { navController?.navigate(RootRoute.PlayHub.route) { launchSingleTop = true } }, "Play"),
            SidePanelItem(R.drawable.baseline_view_week_24, { navController?.navigate(RootRoute.StatsHub.route) { launchSingleTop = true } }, "Stats"),
            SidePanelItem(R.drawable.store, { navController?.navigate(RootRoute.Store.route) { launchSingleTop = true } }, "Store"),
            SidePanelItem(nethical.questphone.data.R.drawable.quest_adderpng, { navController?.navigate(RootRoute.AddNewQuest.route) { launchSingleTop = true } }, "Add Quest"),
            SidePanelItem(R.drawable.profile_icon, { navController?.navigate(RootRoute.Profile.route) { launchSingleTop = true } }, "Profile"),
            SidePanelItem(nethical.questphone.data.R.drawable.quest_analytics, { navController?.navigate(RootRoute.ListAllQuest.route) { launchSingleTop = true } }, "Quest Analytics"),
            SidePanelItem(R.drawable.baseline_info_24, { navController?.navigate(RootRoute.GemmaChat.route) { launchSingleTop = true } }, "AI Chat"),
            SidePanelItem(R.drawable.quest_manage_settings, { navController?.navigate(RootRoute.LauncherSettings.route) { launchSingleTop = true } }, "Settings")
        )
    }

    // Read panel config once — wrapped in remember to prevent recomposition loop (StackOverflow)
    val sidePanelItems = remember {
        val hidden = viewModel.userRepository.getSidePanelHidden()
        val savedOrder = viewModel.userRepository.getSidePanelOrder()
        val ordered = if (savedOrder.isEmpty()) allSidePanelItems
        else {
            val map = allSidePanelItems.associateBy { it.contentDesc }
            val front = savedOrder.mapNotNull { map[it] }
            val rest = allSidePanelItems.filter { it.contentDesc !in savedOrder }
            front + rest
        }
        ordered.filter { it.contentDesc !in hidden }
    }

    var isAllQuestsDialogVisible by remember { mutableStateOf(false) }

    // Separate guards per gesture so they never interfere with each other
    var verticalSwitched by remember { mutableStateOf(false) }
    var horizontalSwitched by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val swipeIconAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "offsetY"
    )

    val showDonationDialog by viewModel.showDonationsDialog.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isDoubleTapToSleepEnabled = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            isAccessibilityServiceEnabled(context, LockScreenService::class.java)
    }

    LaunchedEffect(Unit) {
        viewModel.handleCheckStreakFailure()
        viewModel.filterQuests()
    }

    DisposableEffect(Unit) {
        try { context.startService(AndroidIntent(context, JarvisListenerService::class.java).apply { action = ACTION_START }) } catch (_: Exception) {}
        onDispose {
            try { context.startService(AndroidIntent(context, JarvisListenerService::class.java).apply { action = ACTION_STOP }) } catch (_: Exception) {}
        }
    }

    // Consume back press on home screen — navigating to HomeScreen from HomeScreen
    // causes a full recompose/reload. As the launcher root, back should do nothing.
    BackHandler(enabled = true) { /* intentionally empty */ }

    if (showDonationDialog) {
        DonationsDialog { viewModel.hideDonationDialog() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = LocalCustomTheme.current.getRootColorScheme().surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->

        if (isAppSelectorVisible) {
            SelectAppsDialog(
                tempShortcuts,
                onDismiss = { isAppSelectorVisible = false },
                onConfirm = { viewModel.saveShortcuts(); isAppSelectorVisible = false }
            )
        }
        if (isAllQuestsDialogVisible) {
            LauncherDialog(
                onDismiss = { isAllQuestsDialogVisible = false },
                rootNavController = navController,
                startDestination = LauncherDialogRoutes.ShowAllQuest.route
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // --- Vertical swipe: UP = App Drawer, DOWN = Notifications ---
                .pointerInput(Unit) {
                    var verticalDragOffset = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            verticalDragOffset = 0f
                            verticalSwitched = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            verticalDragOffset += dragAmount
                            if (verticalDragOffset < -80f) {
                                if (!verticalSwitched) {
                                    verticalSwitched = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        navController?.navigate(RootRoute.AppList.route) {
                                            launchSingleTop = true
                                            popUpTo(RootRoute.HomeScreen.route) { inclusive = false }
                                        }
                                    } catch (_: Exception) {}
                                }
                                return@detectVerticalDragGestures
                            }
                            if (verticalDragOffset > 80f) {
                                if (!verticalSwitched) {
                                    verticalSwitched = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        context.getSystemService("statusbar")?.let { svc ->
                                            val cls = Class.forName("android.app.StatusBarManager")
                                            val expand: Method = cls.getMethod("expandNotificationsPanel")
                                            expand.invoke(svc)
                                        }
                                    } catch (e: Exception) { Log.e("HomeScreen", "Notification panel error", e) }
                                }
                            }
                            return@detectVerticalDragGestures
                        }
                    )
                }
                // --- Horizontal swipe: LEFT = Widgets, RIGHT = App Drawer ---
                .pointerInput(Unit) {
                    var horizontalDragOffset = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDragOffset = 0f
                            horizontalSwitched = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            horizontalDragOffset += dragAmount
                            if (horizontalDragOffset < -80f) {
                                if (!horizontalSwitched) {
                                    horizontalSwitched = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        navController?.navigate(RootRoute.WidgetScreen.route) {
                                            launchSingleTop = true
                                        }
                                    } catch (_: Exception) {}
                                }
                                return@detectHorizontalDragGestures
                            }
                            if (horizontalDragOffset > 80f) {
                                if (!horizontalSwitched) {
                                    horizontalSwitched = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        navController?.navigate(RootRoute.AppList.route) {
                                            launchSingleTop = true
                                            popUpTo(RootRoute.HomeScreen.route) { inclusive = false }
                                        }
                                    } catch (_: Exception) {}
                                }
                                return@detectHorizontalDragGestures
                            }
                        }
                    )
                }
                // --- Double-tap to sleep ---
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isDoubleTapToSleepEnabled) {
                                performLockScreenAction()
                            } else {
                                if (BuildConfig.IS_FDROID) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Enable Accessibility Service to use double-tap to sleep.",
                                            actionLabel = "Open",
                                            duration = SnackbarDuration.Short
                                        ).also { result ->
                                            if (result == SnackbarResult.ActionPerformed)
                                                openAccessibilityServiceScreen(context, LockScreenService::class.java)
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Double tap to sleep is only available on fdroid/gh release variant.",
                                            actionLabel = "Okay",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            LocalCustomTheme.current.ThemeObjects(innerPadding)
            Box(modifier = Modifier.fillMaxSize()) {

                // Main content
                Column(Modifier.padding(WindowInsets.statusBarsIgnoringVisibility.asPaddingValues())) {
                    TopBarActions(coins, streak, true, true)
                    Column(Modifier.padding(8.dp)) {
                        Box { viewModel.getHomeWidget()?.invoke(Modifier.size(200.dp)) }
                        Spacer(Modifier.size(12.dp))
                        Text(
                            time,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { viewModel.toggleTimeCLock() })
                        )
                        Text("Today's Quests", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(12.dp))

                        if (questList.isEmpty()) {
                            TextButton(onClick = { navController?.navigate(RootRoute.AddNewQuest.route) }) {
                                Row {
                                    Icon(Icons.Default.Add, "Add Quests")
                                    Spacer(Modifier.size(4.dp))
                                    Text("Add Quests", fontWeight = FontWeight.ExtraLight, fontSize = 23.sp)
                                }
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            userScrollEnabled = false
                        ) {
                            items(questList.size) { index ->
                                val baseQuest = questList[index]
                                val isFailed = QuestHelper.isTimeOver(baseQuest)
                                val isCompleted = completedQuests.contains(baseQuest.id)
                                Text(
                                    text = baseQuest.title,
                                    fontWeight = FontWeight.ExtraLight,
                                    fontSize = 23.sp,
                                    color = if (isFailed && !isCompleted) smoothRed else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.clickable(
                                        onClick = { navController?.navigate(RootRoute.ViewQuest.route + baseQuest.id) },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false)
                                    )
                                )
                            }
                            item {
                                Text(
                                    text = LocalCustomTheme.current.expandQuestsText,
                                    fontWeight = FontWeight.ExtraLight,
                                    fontSize = 15.sp,
                                    modifier = Modifier.clickable(
                                        onClick = { isAllQuestsDialogVisible = true },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false)
                                    )
                                )
                            }
                        }
                    }
                }

                // Side panel (bottom-left)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Status mini-bar above icons
                    // HomeStatusBar removed — accessed via Play Hub in side panel
                    LazyColumn(
                        modifier = Modifier
                            .background(LocalCustomTheme.current.getExtraColorScheme().toolBoxContainer, RoundedCornerShape(16.dp))
                            .padding(15.dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(sidePanelItems) { item ->
                            Box(
                                modifier = Modifier
                                    .size(35.dp)
                                    .clickable(onClick = { item.onClick() }, interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.contentDesc == "AI Chat") {
                                    // Pixel art avatar for Kai
                                    neth.iecal.questphone.app.screens.etc.KaiSidePanelIcon(
                                        avatarIndex = viewModel.userRepository.userInfo.aiAvatarIndex
                                    )
                                } else {
                                    Image(
                                        painterResource(item.icon), item.contentDesc, Modifier.size(28.dp),
                                        colorFilter = ColorFilter.tint(LocalCustomTheme.current.getRootColorScheme().primary.copy(alpha = 0.5f), BlendMode.Modulate)
                                    )
                                }
                            }
                        }
                    }
                }

                // Shortcuts (bottom-right)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                        if (shortcuts.isEmpty()) {
                            item {
                                TextButton(onClick = { isAppSelectorVisible = true }) {
                                    Row {
                                        Icon(Icons.Default.Add, "Add Shortcuts")
                                        Spacer(Modifier.size(4.dp))
                                        Text("Add Shortcuts", fontWeight = FontWeight.ExtraLight, fontSize = 23.sp)
                                    }
                                }
                            }
                        }
                        item {
                            Text(
                                text = "Screentime",
                                fontWeight = FontWeight.ExtraLight, fontSize = 23.sp, textAlign = TextAlign.End,
                                modifier = Modifier.wrapContentWidth().combinedClickable(
                                    onClick = { navController?.navigate(RootRoute.ShowScreentimeStats.route) },
                                    onLongClick = { isAppSelectorVisible = true }
                                )
                            )
                        }
                        itemsIndexed(shortcuts) { _, pkg ->
                            val name = try {
                                AppNameResolver.resolve(context, pkg, viewModel.userRepository.userInfo)
                            } catch (_: Exception) { pkg }
                            Text(
                                text = name,
                                fontWeight = FontWeight.ExtraLight, fontSize = 23.sp, textAlign = TextAlign.End,
                                modifier = Modifier.wrapContentWidth().combinedClickable(
                                    onClick = { context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) } },
                                    onLongClick = { isAppSelectorVisible = true }
                                )
                            )
                        }
                    }
                }

                // Swipe hint icon
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = if (navController != null || LocalCustomTheme.current.docLink == null) "Swipe up" else "Click to read perks",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = swipeIconAnimation.dp)
                        .padding(bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() * 2)
                )
            }
        }
    }
}
