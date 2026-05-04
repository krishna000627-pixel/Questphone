package neth.iecal.questphone.app.navigation

sealed class RootRoute(val route: String) {
    // Launcher
    data object HomeScreen      : RootRoute("home_screen/")
    data object AppList         : RootRoute("app_list/")
    data object WidgetScreen    : RootRoute("widgetsList/")
    data object Customize       : RootRoute("customize/")

    // Habitica Quest System (replaces old quest system)

    // Store (merged)
    data object Store           : RootRoute("store/")

    // Achievements + Stats

    // Profile
    data object Profile         : RootRoute("profile/")
    data object ProfileSettings : RootRoute("profile_settings/")
    data object GitHubSync      : RootRoute("github_sync/")
    data object TrackerSettings : RootRoute("tracker_settings/")

    // Settings
    data object LauncherSettings    : RootRoute("launcher_settings/")
    data object SetCoinRewardRatio  : RootRoute("set_coin_reward_ratio/")
    data object ShowScreentimeStats : RootRoute("showScreentime/")
    data object WifiSync            : RootRoute("wifi_sync/")
    // Settings sub-pages
    data object HiddenAppsSettings      : RootRoute("hidden_apps_settings/")
    data object CustomVoiceActionsSettings : RootRoute("custom_voice_actions_settings/")
    data object StudyQuotaSettings      : RootRoute("study_quota_settings/")
    data object StrangerModeSettings    : RootRoute("stranger_mode_settings/")
    data object JsonQuestConverter      : RootRoute("json_quest_converter/")
    data object CrashLog                : RootRoute("crash_log/")
    data object GemmaChat               : RootRoute("gemma_chat/")
    data object FocusTimer              : RootRoute("focus_timer/")
    data object QuestNotifications      : RootRoute("quest_notifications/")
    data object StatSettings            : RootRoute("stat_settings/")

    // Onboard/misc
    data object OnBoard         : RootRoute("onboard/")
    data object SelectApps      : RootRoute("select_apps/")
    data object TermsScreen     : RootRoute("terms_screen")
    data object DocViewer       : RootRoute("docViewer/")
    data object ShowSocials     : RootRoute("showSocials/")
    data object ShowTutorials   : RootRoute("showTutorial{/")

    // Legacy kept for compatibility (old quest views still referenced in ViewQuest screens)
    data object ViewQuest       : RootRoute("view_quest/")
    data object AddNewQuest     : RootRoute("add_quest/")
    data object ListAllQuest    : RootRoute("list_quest/")
    data object QuestStats      : RootRoute("questStats/")
    data object SelectTemplates : RootRoute("templates_screen/")
    data object SetupTemplate   : RootRoute("setup_template/")
    data object SetIntegration  : RootRoute("set_quest_integration/")
    data object IntegrationDocs : RootRoute("tutorial/")
    // v2.8 – Quest intelligence
    data object QuestCategorySetup    : RootRoute("quest_category_setup/")

    // v2.9 – Notifications / YouTube
    data object YouTubeAllowance      : RootRoute("youtube_allowance/")

    // v3.0 – Study integration
    data object StudyTracker          : RootRoute("study_tracker/")
    data object FocusSessionHistory   : RootRoute("focus_session_history/")
    data object WeeklyScreenTime      : RootRoute("weekly_screen_time/")
    data object AppTimeLimit          : RootRoute("app_time_limit/")

    // v3.1 – Kai upgrades
    data object QuestPlanGenerator    : RootRoute("quest_plan_generator/")
    data object KaiModelSelector      : RootRoute("kai_model_selector/")
    data object KaiWeeklySummary      : RootRoute("kai_weekly_summary/")

    // v3.2 – Polish
    data object CoinTransactionLog    : RootRoute("coin_transaction_log/")
    data object BackupRestore         : RootRoute("backup_restore/")
    data object StatHistory           : RootRoute("stat_history/")
}