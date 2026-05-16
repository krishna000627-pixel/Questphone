package neth.iecal.questphone.app.navigation

sealed class RootRoute(val route: String) {
    // Launcher
    data object HomeScreen      : RootRoute("home_screen/")
    data object AppList         : RootRoute("app_list/")
    data object WidgetScreen    : RootRoute("widgetsList/")
    data object Customize       : RootRoute("customize/")

    // Store (merged)
    data object Store           : RootRoute("store/")

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

    // Legacy quest views
    data object ViewQuest       : RootRoute("view_quest/")
    data object AddNewQuest     : RootRoute("add_quest/")
    data object ListAllQuest    : RootRoute("list_quest/")
    data object QuestStats      : RootRoute("questStats/")
    data object SelectTemplates : RootRoute("templates_screen/")
    data object SetupTemplate   : RootRoute("setup_template/")
    data object SetIntegration  : RootRoute("set_quest_integration/")
    data object IntegrationDocs : RootRoute("tutorial/")
    data object QuestCategorySetup    : RootRoute("quest_category_setup/")

    data object YouTubeAllowance      : RootRoute("youtube_allowance/")
    data object StudyTracker          : RootRoute("study_tracker/")
    data object FocusSessionHistory   : RootRoute("focus_session_history/")
    data object WeeklyScreenTime      : RootRoute("weekly_screen_time/")
    data object AppTimeLimit          : RootRoute("app_time_limit/")
    data object QuestPlanGenerator    : RootRoute("quest_plan_generator/")
    data object KaiModelSelector      : RootRoute("kai_model_selector/")
    data object KaiWeeklySummary      : RootRoute("kai_weekly_summary/")
    data object CoinTransactionLog    : RootRoute("coin_transaction_log/")
    data object BackupRestore         : RootRoute("backup_restore/")
    data object StatHistory               : RootRoute("stat_history/")
    data object NotificationBlockerSettings : RootRoute("notif_blocker_settings/")
    data object AiMemoryTrainer             : RootRoute("ai_memory_trainer/")
    data object BossBattle                  : RootRoute("boss_battle/")
    data object QuestChains                 : RootRoute("quest_chains/")
    data object RivalScreen                 : RootRoute("rival_screen/")
    data object ProductivityScore           : RootRoute("productivity_score/")
    data object KaiPersonality              : RootRoute("kai_personality/")
    data object WeeklyReport                : RootRoute("weekly_report/")
    data object LockdownSettings            : RootRoute("lockdown_settings/")
    data object PlayHub                     : RootRoute("play_hub/")
    data object StatsHub                    : RootRoute("stats_hub/")
    data object RpgSettings                 : RootRoute("rpg_settings/")

    // v3.3 – People, Routines, Data Vault
    data object PeopleDatabase  : RootRoute("people_database/")
    data object Routines        : RootRoute("routines/")
    data object DataVault       : RootRoute("data_vault/")
}
