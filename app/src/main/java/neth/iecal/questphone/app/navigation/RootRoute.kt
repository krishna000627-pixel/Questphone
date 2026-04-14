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
}
