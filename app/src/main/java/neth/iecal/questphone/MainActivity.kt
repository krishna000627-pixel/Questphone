package neth.iecal.questphone

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.etc.DocumentViewerScreen
import neth.iecal.questphone.app.screens.etc.ScreentimeStatsScreen
import neth.iecal.questphone.app.screens.etc.WifiSyncScreen
import neth.iecal.questphone.app.screens.etc.LauncherSettingsScreen
import neth.iecal.questphone.app.screens.etc.TrackerSettingsScreen
import neth.iecal.questphone.app.screens.account.ProfileSettingsScreen
import neth.iecal.questphone.app.screens.etc.GitHubSyncScreen
import neth.iecal.questphone.app.screens.account.ProfileScreen
import neth.iecal.questphone.app.screens.etc.SetCoinRewardRatio
import neth.iecal.questphone.app.screens.game.RewardDialogMaker
import neth.iecal.questphone.app.screens.game.StoreScreen
import neth.iecal.questphone.app.screens.launcher.AppList
import neth.iecal.questphone.app.screens.launcher.AppListViewModel
import neth.iecal.questphone.app.screens.jarvis.JarvisScreen
import neth.iecal.questphone.app.screens.launcher.CustomizeScreen
import neth.iecal.questphone.app.screens.launcher.HomeScreen
import neth.iecal.questphone.app.screens.launcher.HomeScreenViewModel
import neth.iecal.questphone.app.screens.launcher.widget.WidgetScreen
import neth.iecal.questphone.app.screens.onboard.subscreens.SelectApps
import neth.iecal.questphone.app.screens.onboard.subscreens.SelectAppsModes
import neth.iecal.questphone.app.screens.onboard.subscreens.ShowSocialsScreen
import neth.iecal.questphone.app.screens.onboard.subscreens.ShowTutorial
import neth.iecal.questphone.app.screens.pet.TheSystemDialog
import neth.iecal.questphone.app.screens.quest.ListAllQuests
import neth.iecal.questphone.app.screens.quest.ViewQuest
import neth.iecal.questphone.app.screens.quest.setup.SetIntegration
import neth.iecal.questphone.app.screens.quest.stats.specific.BaseQuestStatsView
import neth.iecal.questphone.app.screens.quest.templates.SelectFromTemplates
import neth.iecal.questphone.app.screens.quest.templates.SetupTemplate
import neth.iecal.questphone.app.screens.quest.templates.TemplatesViewModel
import android.Manifest
import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import neth.iecal.questphone.app.theme.LauncherTheme
import neth.iecal.questphone.app.theme.customThemes.PitchBlackTheme
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.StatsRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.sync.GitHubSyncManager
import neth.iecal.questphone.backed.sync.RenderSyncManager
import neth.iecal.questphone.backed.sync.RenderSyncPrefs
import neth.iecal.questphone.core.services.AppBlockerService
import neth.iecal.questphone.core.utils.FcmHandler
import neth.iecal.questphone.core.utils.receiver.AppInstallReceiver
import neth.iecal.questphone.core.utils.reminder.NotificationScheduler
import neth.iecal.questphone.data.IntegrationId
import nethical.questphone.core.core.utils.fromHex
import javax.inject.Inject


@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : ComponentActivity() {

    private var navControllerRef: androidx.navigation.NavController? = null
    var isLaunchingApp: Boolean = false
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var gitHubSyncManager: GitHubSyncManager
    @Inject lateinit var questRepository: QuestRepository
    @Inject lateinit var statRepository: StatsRepository

    private lateinit var appInstallReceiver: AppInstallReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply wallpaper flag immediately so window background shows live wallpaper
        applyWallpaperFlag()
        // Make window background transparent so wallpaper renders behind Compose
        if (android.os.Build.VERSION.SDK_INT >= 12) {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        handleNotificationIntent(intent)
        val questId = intent.getStringExtra("quest_id")
        enableEdgeToEdge()
        val data = getSharedPreferences("onboard", MODE_PRIVATE)
        val notificationScheduler = NotificationScheduler(applicationContext,questRepository)
        val currentTheme = themes[userRepository.userInfo.customization_info.equippedTheme] ?: themes.values.first()
        // Read onboard flag synchronously — avoids white screen flash on restart
        val isOnboarded = data.getBoolean("onboard", false)
        if (isOnboarded) {
            startForegroundService(Intent(this, AppBlockerService::class.java))
        }
        setContent {
            var currentTheme = remember { mutableStateOf(currentTheme) }

            LaunchedEffect(Unit) {
                notificationScheduler.createNotificationChannel()
                notificationScheduler.reloadAllReminders()
                neth.iecal.questphone.backed.sync.RenderSyncPrefs.initIfNeeded(applicationContext)
                // v2.9 – Kai daily briefing + weekly summary
                if (isOnboarded) {
                    neth.iecal.questphone.core.utils.reminder.scheduleDailyKaiBriefing(
                        applicationContext,
                        userRepository.userInfo.dailyBriefingHour
                    )
                    neth.iecal.questphone.app.screens.etc.KaiWeeklySummaryWorker.schedule(applicationContext)
                }
            }
            LauncherTheme(currentTheme.value) {
                // ── Device wallpaper bridge ───────────────────────────────
                val useWallpaper = userRepository.userInfo.useDeviceWallpaper
                // Use .value directly — 'by' delegation fails outside @Composable scope
                val wallpaperState = remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

                if (useWallpaper) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        try {
                            val wm = WallpaperManager.getInstance(this@MainActivity)
                            val drawable = wm.drawable ?: wm.peekDrawable() ?: wm.builtInDrawable
                            wallpaperState.value = (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
                        } catch (_: Exception) { }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val bmp = wallpaperState.value
                    if (useWallpaper && bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                Surface(
                    color = if (useWallpaper)
                        androidx.compose.ui.graphics.Color.Transparent
                    else
                        MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    androidx.compose.runtime.DisposableEffect(navController) {
                        navControllerRef = navController
                        onDispose { navControllerRef = null }
                    }

                    val unSyncedQuestItems = remember { questRepository.getUnSyncedQuests() }
                    val context = LocalContext.current

                    RewardDialogMaker(userRepository, gitHubSyncManager)

                    TheSystemDialog()
                    LaunchedEffect(Unit) {
                        unSyncedQuestItems.collect {
                            notificationScheduler.reloadAllReminders()
                        }
                    }

                    val appListViewModel : AppListViewModel = hiltViewModel()
                    val homeScreenViewModel : HomeScreenViewModel = hiltViewModel()
                    val templatesViewModel: TemplatesViewModel = hiltViewModel()

                    val scope = rememberCoroutineScope()
                    DisposableEffect(Unit) {
                        val receiver = AppInstallReceiver { packageName ->
                            scope.launch(Dispatchers.IO) {
                                appListViewModel.loadApps()
                            }
                        }

                        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                            addDataScheme("package")
                        }

                        context.registerReceiver(receiver, filter)

                        onDispose {
                            context.unregisterReceiver(receiver)
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = if(questId!=null) "${RootRoute.ViewQuest.route}${questId}" else RootRoute.HomeScreen.route,
                        popEnterTransition = { fadeIn(animationSpec = tween(700)) },
                        popExitTransition = { fadeOut(animationSpec = tween(700)) },
                    ) {

                        composable(
                            route = "${RootRoute.SelectApps.route}{mode}",
                            arguments = listOf(navArgument("mode") { type = NavType.IntType })
                        ) { backstack ->
                            val mode = backstack.arguments?.getInt("mode")
                            SelectApps(SelectAppsModes.entries[mode!!])
                        }
                        composable(RootRoute.HomeScreen.route) {
                            HomeScreen(navController,homeScreenViewModel)
                        }
                        composable(RootRoute.WidgetScreen.route) {
                            WidgetScreen(navController)
                        }


                        composable(RootRoute.Store.route) {
                            LauncherTheme(PitchBlackTheme()) {
                                StoreScreen(navController)
                            }
                        }

                        composable(RootRoute.Customize.route) {
                            LauncherTheme(PitchBlackTheme()) {
                                CustomizeScreen(navController, currentTheme = currentTheme)
                            }
                        }
                        composable(RootRoute.AppList.route) {
                            AppList(navController,appListViewModel)
                        }

                        composable(RootRoute.ListAllQuest.route) {
                            ListAllQuests(navController)
                        }
                        composable(RootRoute.SelectTemplates.route) {
                            SelectFromTemplates(navController, templatesViewModel)
                        }
                        composable(RootRoute.SetupTemplate.route) {
                            SetupTemplate(navController, templatesViewModel)
                        }
                        composable(
                            route = "${RootRoute.ViewQuest.route}{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")

                            ViewQuest(navController, questRepository,id!!)
                        }

                        navigation(
                            startDestination = RootRoute.SetIntegration.route,
                            route = RootRoute.AddNewQuest.route
                        ) {
                            composable(RootRoute.SetIntegration.route) {
                                SetIntegration(
                                    navController
                                )
                            }
                            IntegrationId.entries.forEach { item ->
                                composable(
                                    route = item.name + "/{id}",
                                    arguments = listOf(navArgument("id") {
                                        type = NavType.StringType
                                    })
                                ) { backstack ->
                                    var id = backstack.arguments?.getString("id")
                                    if (id == "ntg") {
                                        id = null
                                    }
                                    item.setupScreen.invoke(id, navController)
                                }
                            }
                        }
                        composable("${RootRoute.QuestStats.route}{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id")

                            BaseQuestStatsView(id!!, navController)
                        }

                        composable(RootRoute.SetCoinRewardRatio.route){
                            SetCoinRewardRatio()
                        }
                        composable("${RootRoute.IntegrationDocs.route}{name}"){ backStackEntry ->
                            val id = backStackEntry.arguments?.getString("name")
                            val url = IntegrationId.valueOf(id.toString()).docLink
                            DocumentViewerScreen(url)
                        }
                        composable("${RootRoute.DocViewer.route}{url}"){ backStackEntry ->
                            val url = backStackEntry.arguments?.getString("url")
                            DocumentViewerScreen(String.fromHex(url.toString()))
                        }
                        composable(RootRoute.ShowSocials.route) {
                            ShowSocialsScreen()
                        }

                        composable(RootRoute.ShowTutorials.route) {
                            ShowTutorial()
                        }
                        composable(RootRoute.ShowScreentimeStats.route) {
                            ScreentimeStatsScreen(navController)
                        }
                        composable(RootRoute.WifiSync.route) {
                            WifiSyncScreen(navController)
                        }
                        composable(RootRoute.Profile.route) {
                            ProfileScreen(navController)
                        }
                        composable(RootRoute.LauncherSettings.route) {
                            LauncherSettingsScreen(navController)
                        }
                        // Settings sub-pages
                        composable(RootRoute.HiddenAppsSettings.route) {
                            neth.iecal.questphone.app.screens.etc.HiddenAppsScreen(navController)
                        }
                        composable(RootRoute.CustomVoiceActionsSettings.route) {
                            neth.iecal.questphone.app.screens.etc.CustomVoiceActionsScreen(navController)
                        }
                        composable(RootRoute.StudyQuotaSettings.route) {
                            neth.iecal.questphone.app.screens.etc.StudyQuotaSettingsScreen(navController)
                        }
                        composable(RootRoute.StrangerModeSettings.route) {
                            neth.iecal.questphone.app.screens.etc.StrangerModeSettingsScreen(navController)
                        }
                        composable(RootRoute.JsonQuestConverter.route) {
                            neth.iecal.questphone.app.screens.etc.JsonQuestConverterScreen(navController)
                        }
                        composable(RootRoute.CrashLog.route) {
                            neth.iecal.questphone.app.screens.etc.CrashLogScreen(navController)
                        }
                        composable(RootRoute.GemmaChat.route) {
                            neth.iecal.questphone.app.screens.etc.GemmaChatScreen(navController)
                        }
                        composable(RootRoute.FocusTimer.route) {
                            neth.iecal.questphone.app.screens.etc.FocusTimerScreen(navController)
                        }
                        composable(RootRoute.QuestNotifications.route) {
                            neth.iecal.questphone.app.screens.etc.QuestNotificationsScreen(navController)
                        }
                        composable(RootRoute.StatSettings.route) {
                            neth.iecal.questphone.app.screens.etc.StatSettingsScreen(navController)
                        }
                        composable(RootRoute.TrackerSettings.route) {
                            TrackerSettingsScreen(navController)
                        }

                        composable(RootRoute.ProfileSettings.route) {
                            ProfileSettingsScreen(navController)
                        }
                        composable(RootRoute.GitHubSync.route) {
                            GitHubSyncScreen(navController)
                        }





                        // ---- v2.8–v3.2 new screens ----
                        composable(RootRoute.StudyTracker.route) {
                            neth.iecal.questphone.app.screens.etc.StudyTrackerScreen(navController)
                        }
                        composable(RootRoute.FocusSessionHistory.route) {
                            neth.iecal.questphone.app.screens.etc.FocusSessionHistoryScreen(navController)
                        }
                        composable(RootRoute.AppTimeLimit.route) {
                            neth.iecal.questphone.app.screens.etc.AppTimeLimitScreen(navController)
                        }
                        composable(RootRoute.QuestPlanGenerator.route) {
                            neth.iecal.questphone.app.screens.etc.QuestPlanGeneratorScreen(navController)
                        }
                        composable(RootRoute.KaiModelSelector.route) {
                            neth.iecal.questphone.app.screens.etc.KaiModelSelectorScreen(navController)
                        }
                        composable(RootRoute.CoinTransactionLog.route) {
                            neth.iecal.questphone.app.screens.game.CoinTransactionLogScreen(navController)
                        }
                        composable(RootRoute.BackupRestore.route) {
                            neth.iecal.questphone.app.screens.account.BackupRestoreScreen(navController)
                        }
                        composable(RootRoute.RenderSync.route) {
                            neth.iecal.questphone.app.screens.account.RenderSyncSettingsScreen(navController)
                        }
                        composable(RootRoute.StatHistory.route) {
                            neth.iecal.questphone.app.screens.account.StatHistoryScreen(navController)
                        }
                        composable(RootRoute.NotificationBlockerSettings.route) {
                            neth.iecal.questphone.app.screens.etc.NotificationBlockerSettingsScreen(navController)
                        }
                        composable(RootRoute.AiMemoryTrainer.route) {
                            neth.iecal.questphone.app.screens.etc.AiMemoryTrainerScreen(navController)
                        }
                        composable(RootRoute.BossBattle.route) {
                            neth.iecal.questphone.app.screens.game.BossBattleScreen(navController)
                        }
                        composable(RootRoute.QuestChains.route) {
                            neth.iecal.questphone.app.screens.quest.QuestChainsScreen(navController)
                        }
                        composable(RootRoute.RivalScreen.route) {
                            neth.iecal.questphone.app.screens.game.RivalScreen(navController)
                        }
                        composable(RootRoute.ProductivityScore.route) {
                            neth.iecal.questphone.app.screens.etc.ProductivityScoreScreen(navController)
                        }
                        composable(RootRoute.KaiPersonality.route) {
                            neth.iecal.questphone.app.screens.etc.KaiPersonalityScreen(navController)
                        }
                        composable(RootRoute.WeeklyReport.route) {
                            neth.iecal.questphone.app.screens.etc.WeeklyReportScreen(navController)
                        }
                        composable(RootRoute.LockdownSettings.route) {
                            neth.iecal.questphone.app.screens.etc.LockdownSettingsScreen(navController)
                        }
                        composable(RootRoute.PlayHub.route) {
                            neth.iecal.questphone.app.screens.game.PlayHubScreen(navController)
                        }
                        composable(RootRoute.StatsHub.route) {
                            neth.iecal.questphone.app.screens.etc.StatsHubScreen(navController)
                        }
                        composable(RootRoute.RpgSettings.route) {
                            neth.iecal.questphone.app.screens.etc.RpgSettingsScreen(navController)
                        }
                        composable(RootRoute.PeopleDatabase.route) {
                            neth.iecal.questphone.app.screens.people.PeopleDatabaseScreen(navController)
                        }
                        composable(RootRoute.Jarvis.route) {
                            JarvisScreen(
                                navController = navController,
                                userInfo = userRepository.userInfo
                            )
                        }
                        composable(RootRoute.MyLife.route) {
                            neth.iecal.questphone.app.screens.mylife.MyLifeScreen(navController)
                        }
                        composable(RootRoute.MyLifeSettings.route) {
                            neth.iecal.questphone.app.screens.mylife.MyLifeSettingsScreen(navController)
                        }
                        composable(RootRoute.CalculatorVault.route) {
                            neth.iecal.questphone.app.screens.locker.CalculatorVaultScreen(navController)
                        }
                        composable(RootRoute.AppLockerSettings.route) {
                            neth.iecal.questphone.app.screens.locker.AppLockerSettingsScreen(navController)
                        }
                        composable(RootRoute.AppVaultSettings.route) {
                            neth.iecal.questphone.app.screens.locker.AppVaultSettingsScreen(navController)
                        }
                    }

                } // Surface
                } // wallpaper Box

            }
        }
    }

    private fun applyWallpaperFlag() {
        val useWallpaper = try {
            // Read directly from shared prefs to avoid DI timing issues
            getSharedPreferences("user_info", MODE_PRIVATE).getBoolean("useDeviceWallpaper", false)
        } catch (_: Exception) { false }

        if (useWallpaper) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLaunchingApp) { isLaunchingApp = false; return }
        navControllerRef?.let { nav ->
            val homeRoute = neth.iecal.questphone.app.navigation.RootRoute.HomeScreen.route
            if (nav.currentDestination?.route != homeRoute) {
                nav.popBackStack(homeRoute, inclusive = false)
            }
        }
    }

    override fun onResume() {
        handleNotificationIntent(intent)
        super.onResume()
        gitHubSyncManager.flushPendingIfOnline()
        // Auto-pull: silently restore from server if it has newer data
        if (RenderSyncPrefs.isEnabled(this)) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val url = RenderSyncPrefs.getServerUrl(this@MainActivity)
                    val token = RenderSyncPrefs.getSyncToken(this@MainActivity)
                    val lastSync = RenderSyncPrefs.getLastSyncAt(this@MainActivity)
                    if (url.isBlank() || token.isBlank()) return@launch
                    val conn = (java.net.URL("$url/sync/check?clientUpdatedAt=$lastSync")
                        .openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("x-sync-token", token)
                        setRequestProperty("x-device-id", RenderSyncManager.getDeviceId(this@MainActivity))
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    val code = conn.responseCode
                    val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    conn.disconnect()
                    if (code == 200) {
                        val hasUpdate = org.json.JSONObject(body).optBoolean("hasUpdate", false)
                        if (hasUpdate) {
                            RenderSyncManager.pull(this@MainActivity, userRepository, questRepository)
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
        val isHomeIntent = intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        if (isHomeIntent) {
            if (neth.iecal.questphone.core.services.AppBlockerServiceInfo.deepFocus.isRunning) return
            navControllerRef?.let { nav ->
                val homeRoute = neth.iecal.questphone.app.navigation.RootRoute.HomeScreen.route
                if (nav.currentDestination?.route != homeRoute) {
                    nav.popBackStack(homeRoute, inclusive = false)
                }
            }
        }
    }
    private fun handleNotificationIntent(intent: Intent?) {
        intent?.extras?.let { extras ->
            val data = mutableMapOf<String, String>()
            for (key in extras.keySet()) {
                val value = extras.get(key)?.toString() ?: ""
                data[key] = value
            }

            Log.d("notification Data", data.toString())

            FcmHandler.handleData(this, data,userRepository)
        }
    }


}


