package neth.iecal.questphone.core.utils.termux

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import neth.iecal.questphone.R
import neth.iecal.questphone.app.screens.game.rewardUserForQuestCompl
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.QuestRepositoryEntryPoint
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.repositories.UserRepositoryEntryPoint
import neth.iecal.questphone.core.services.AppBlockerServiceInfo
import neth.iecal.questphone.core.services.INTENT_ACTION_UNLOCK_APP
import nethical.questphone.core.core.utils.getCurrentDate
import nethical.questphone.data.BaseIntegrationId
import nethical.questphone.data.game.StreakData
import nethical.questphone.data.habitica.StatPoints
import nethical.questphone.data.json
import nethical.questphone.data.quest.ai.snap.AiSnap
import nethical.questphone.data.quest.focus.DeepFocus
import nethical.questphone.data.quest.focus.FocusTimeConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Localhost-only HTTP bridge so Termux (or anything else on-device) can read and act on
 * QuestPhone's data with plain `curl`. Only binds to 127.0.0.1 — never reachable off-device,
 * and unlike the old fake terminal this never touches Termux's filesystem directly.
 *
 * Endpoints:
 *   GET  /quests                 -> all quests
 *   GET  /quests/{id}            -> one quest
 *   POST /quests/{id}/complete   -> marks it done the same way the in-app quest view does
 *   GET  /balance                -> {"coins": N}
 *   GET  /stats                  -> level, xp, streak, stat points, diamonds
 *   GET  /inventory              -> item counts
 *
 * Note on /complete: it reuses the app's own [rewardUserForQuestCompl] reward path, which
 * grants XP/coins via a Compose-observed state — same mechanism the in-app quest view and
 * the WebView integration already rely on. That means the reward is applied as soon as
 * QuestPhone's UI is next composed (it's the launcher, so in practice this is near-instant),
 * not necessarily the exact instant the HTTP call returns.
 */
class QuestPhoneLocalServer : Service() {

    private lateinit var questRepository: QuestRepository
    private lateinit var userRepository: UserRepository
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        questRepository = EntryPointAccessors.fromApplication(
            applicationContext, QuestRepositoryEntryPoint::class.java
        ).questRepository()
        userRepository = EntryPointAccessors.fromApplication(
            applicationContext, UserRepositoryEntryPoint::class.java
        ).userRepository()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        if (serverSocket == null || serverSocket?.isClosed == true) {
            scope.launch { runServer() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { serverSocket?.close() } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val channelId = "questphone_termux_bridge"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr?.createNotificationChannel(
                NotificationChannel(channelId, "Termux bridge", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("QuestPhone Termux bridge")
            .setContentText("Listening on 127.0.0.1:$PORT (local only)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun runServer() {
        try {
            serverSocket = ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))
            while (serverSocket?.isClosed == false) {
                val socket = serverSocket!!.accept()
                scope.launch { handleClient(socket) }
            }
        } catch (_: Exception) {
            // socket closed on stop, or failed to bind (port in use) — nothing else to do
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { sock ->
            try {
                val reader = BufferedReader(InputStreamReader(sock.inputStream))
                val requestLine = reader.readLine() ?: return
                val requestParts = requestLine.split(" ")
                if (requestParts.size < 2) return
                val method = requestParts[0]
                val path = requestParts[1].substringBefore("?")

                var contentLength = 0
                var headerLine = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    if (headerLine.startsWith("Content-Length", ignoreCase = true)) {
                        contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    headerLine = reader.readLine()
                }
                var requestBody = ""
                if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    requestBody = String(buf)
                }

                val (status, respBody) = route(method, path, requestBody)
                val contentType = if (path.trim('/') == "home") "text/plain; charset=utf-8" else "application/json"
                writeResponse(sock.getOutputStream(), status, respBody, contentType)
            } catch (e: Exception) {
                try {
                    writeResponse(sock.getOutputStream(), 500, """{"error":${jsonString(e.message ?: "unknown")}}""")
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun route(method: String, path: String, body: String): Pair<Int, String> {
        val segments = path.trim('/').split("/").filter { it.isNotEmpty() }
        return try {
            when {
                method == "GET" && segments == listOf("quests") ->
                    200 to json.encodeToString(questRepository.getAllQuestsAsList())

                // Ready-to-print dashboard — see [homeSummary]. Plain text, not JSON.
                method == "GET" && segments == listOf("home") ->
                    homeSummary()

                method == "POST" && segments == listOf("quests") ->
                    createQuest(body)

                method == "GET" && segments.size == 2 && segments[0] == "quests" -> {
                    val quest = questRepository.getQuestById(segments[1])
                    if (quest != null) 200 to json.encodeToString(quest)
                    else 404 to """{"error":"quest not found"}"""
                }

                method == "POST" && segments.size == 3 && segments[0] == "quests" && segments[2] == "complete" ->
                    completeQuest(segments[1])

                method == "GET" && segments == listOf("balance") ->
                    200 to json.encodeToString(BalanceResponse(userRepository.userInfo.coins))

                method == "GET" && segments == listOf("stats") -> {
                    val u = userRepository.userInfo
                    200 to json.encodeToString(
                        StatsResponse(
                            level = u.level,
                            xp = u.xp,
                            diamonds = u.diamonds,
                            streak = u.streak,
                            statPoints = u.statPoints
                        )
                    )
                }

                method == "GET" && segments == listOf("inventory") ->
                    200 to json.encodeToString(
                        InventoryResponse(userRepository.userInfo.inventory.mapKeys { it.key.name })
                    )

                // ── Apps ─────────────────────────────────────────────
                method == "GET" && segments == listOf("apps") ->
                    200 to json.encodeToString(listInstalledApps())

                method == "GET" && segments == listOf("apps", "hidden") ->
                    200 to json.encodeToString(userRepository.userInfo.hiddenPackages.toList())

                method == "POST" && segments == listOf("apps", "open") ->
                    openApp(body)

                // ── Fuzzy: "ascension hall", "whatsapp" — tries a named in-app
                //    screen first, then an installed app by label. ──────────
                method == "POST" && segments == listOf("launch") ->
                    launch(body)

                // ── Navigate to a QuestPhone screen (goes through the app's real
                //    UI/nav graph — so PIN/vault prompts, blocker, and hidden-app
                //    rules all still apply exactly as if the user tapped there) ──
                method == "POST" && segments.size == 2 && segments[0] == "open" ->
                    openNamedScreen(segments[1])

                else -> 404 to """{"error":"unknown route"}"""
            }
        } catch (e: Exception) {
            500 to """{"error":${jsonString(e.message ?: "unknown")}}"""
        }
    }

    private suspend fun homeSummary(): Pair<Int, String> {
        val u = userRepository.userInfo
        val today = nethical.questphone.core.core.utils.getCurrentDay()
        val todayDate = getCurrentDate()
        val quests = questRepository.getAllQuestsAsList().filter { q ->
            !q.is_destroyed && q.selected_days.contains(today) &&
                (q.start_date.isEmpty() || q.start_date <= todayDate)
        }
        val sb = StringBuilder()
        sb.append("QuestPhone — Lv${u.level}  ${u.xp}xp  streak ${u.streak.currentStreak}  ")
            .append("${u.coins} coins  ${u.diamonds} diamonds\n")
        if (quests.isEmpty()) {
            sb.append("No quests scheduled today.\n")
        } else {
            sb.append("Today:\n")
            quests.forEach { q ->
                val done = q.last_completed_on == todayDate
                sb.append("  [${if (done) "x" else " "}] ${q.title}  (${q.id})\n")
            }
        }
        return 200 to sb.toString()
    }

    private suspend fun createQuest(body: String): Pair<Int, String> {
        if (body.isBlank()) return 400 to """{"error":"missing json body"}"""
        val req = try {
            json.decodeFromString<CreateQuestRequest>(body)
        } catch (e: Exception) {
            return 400 to """{"error":${jsonString("invalid json: ${e.message}")}}"""
        }
        if (req.title.isBlank()) return 400 to """{"error":"title is required"}"""

        var instructions = req.instructions
        val (integrationId, questJson) = when (req.type) {
            2 -> BaseIntegrationId.DEEP_FOCUS to json.encodeToString(
                DeepFocus(
                    focusTimeConfig = FocusTimeConfig(
                        initialTime = req.duration_minutes.toString(),
                        finalTime = req.duration_minutes.toString(),
                        initialUnit = "m",
                        finalUnit = "m"
                    )
                )
            )
            3 -> BaseIntegrationId.AI_SNAP to json.encodeToString(AiSnap(taskDescription = req.task_description))
            4 -> {
                if (req.external_url.isNotBlank() && instructions.isBlank()) {
                    instructions = "URL: ${req.external_url} — finish setup in-app (Add Quest > External Integration); this needs a sign-in step Termux can't do."
                }
                BaseIntegrationId.EXTERNAL_INTEGRATION to ""
            }
            5 -> BaseIntegrationId.OPTIONAL_QUEST to ""
            else -> BaseIntegrationId.SWIFT_MARK to ""
        }

        val quest = neth.iecal.questphone.data.CommonQuestInfo(
            title = req.title,
            reward = req.reward,
            integration_id = integrationId,
            selected_days = req.selected_days,
            time_range = req.time_range,
            isHardLock = req.isHardLock,
            instructions = instructions,
            quest_json = questJson
        )
        questRepository.upsertQuest(quest)
        return 200 to json.encodeToString(quest)
    }

    /** Launches an app by package name OR by (fuzzy) label — "com.whatsapp" and
     *  "whatsapp" both work. Hidden apps open the same way (hiding only affects the
     *  app list, not launching). If the target is on the blocked/locked list and
     *  isn't already temporarily unlocked, this does NOT launch it — it returns a
     *  402 asking for confirmation (see [openAppWithLockCheck]), same as the coin
     *  dialog the in-app app list shows. Pass `"confirm":true` to actually spend
     *  the coins and launch. */
    private fun openApp(body: String): Pair<Int, String> {
        val req = try { json.decodeFromString<OpenAppRequest>(body) } catch (e: Exception) {
            return 400 to """{"error":${jsonString("invalid json: ${e.message}")}}"""
        }
        if (req.`package`.isBlank()) return 400 to """{"error":"package is required"}"""

        val pkg = resolvePackage(req.`package`) ?: return 404 to
            """{"error":${jsonString("no installed app matches '${req.`package`}'")}}"""

        return openAppWithLockCheck(pkg, req.confirm, req.coins)
    }

    /** Resolves an exact package name if it's launchable, otherwise falls back to
     *  matching it against installed app labels (exact, then substring). */
    private fun resolvePackage(nameOrPackage: String): String? {
        if (packageManager.getLaunchIntentForPackage(nameOrPackage) != null) return nameOrPackage
        val target = normalizeKey(nameOrPackage)
        val apps = listInstalledApps()
        apps.firstOrNull { normalizeKey(it.label) == target }?.let { return it.packageName }
        val partial = apps.filter { normalizeKey(it.label).contains(target) }
        return if (partial.size == 1) partial[0].packageName else null
    }

    /** The distraction blocker watches foreground-app usage system-wide, so it can't
     *  be bypassed by launching an app this way — but launching a locked app straight
     *  from Termux would just bounce you right back to QuestPhone with no context on
     *  *why*. So: if [pkg] is on the blocked list and not already temporarily unlocked,
     *  ask first — same coin-for-minutes trade the in-app unlock dialog offers, just
     *  surfaced as JSON for Termux to turn into a y/n prompt. */
    private fun openAppWithLockCheck(pkg: String, confirm: Boolean, coins: Int): Pair<Int, String> {
        val isLocked = userRepository.getBlockedPackages().contains(pkg) &&
            !AppBlockerServiceInfo.unlockedApps.containsKey(pkg)

        if (!isLocked) return launchAppByPackage(pkg)

        val minutesPer5 = getSharedPreferences("minutes_per_5", MODE_PRIVATE).getInt("minutes_per_5", 10)

        if (!confirm) {
            return 402 to json.encodeToString(
                LockedAppResponse(
                    `package` = pkg,
                    cost_coins = coins,
                    unlock_minutes = minutesPer5 * (coins / 5).coerceAtLeast(1)
                )
            )
        }

        if (userRepository.userInfo.coins < coins) {
            return 402 to """{"error":"not enough coins","have":${userRepository.userInfo.coins},"need":$coins}"""
        }

        val minutes = minutesPer5 * (coins / 5).coerceAtLeast(1)
        userRepository.useCoins(coins, "Termux app unlock")
        sendBroadcast(Intent(INTENT_ACTION_UNLOCK_APP).apply {
            putExtra("selected_time", minutes * 60_000L)
            putExtra("package_name", pkg)
        })
        return launchAppByPackage(pkg)
    }

    private fun launchAppByPackage(pkg: String): Pair<Int, String> {
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            ?: return 404 to """{"error":"app not found or has no launcher activity"}"""
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return 200 to """{"status":"launched","package":${jsonString(pkg)}}"""
    }

    private fun normalizeKey(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Free-text resolve, so "questphone ascension hall" or "questphone whatsapp"
     *  both just work: tries a named in-app screen first (exact, then substring),
     *  then falls back to an installed app whose label matches — going through the
     *  same lock-check as [openApp] so a blocked app still asks first. */
    private fun launch(body: String): Pair<Int, String> {
        val req = try { json.decodeFromString<LaunchRequest>(body) } catch (e: Exception) {
            return 400 to """{"error":${jsonString("invalid json: ${e.message}")}}"""
        }
        if (req.query.isBlank()) return 400 to """{"error":"query is required"}"""
        val target = normalizeKey(req.query)

        NAMED_SCREENS.entries.firstOrNull { normalizeKey(it.key) == target }?.let {
            return openScreen(it.value)
        }
        NAMED_SCREENS.entries.firstOrNull {
            val k = normalizeKey(it.key)
            k.contains(target) || target.contains(k)
        }?.let { return openScreen(it.value) }

        val apps = listInstalledApps()
        apps.firstOrNull { normalizeKey(it.label) == target }?.let {
            return openAppWithLockCheck(it.packageName, req.confirm, req.coins)
        }
        val partial = apps.filter { normalizeKey(it.label).contains(target) }
        return when {
            partial.size == 1 -> openAppWithLockCheck(partial[0].packageName, req.confirm, req.coins)
            partial.size > 1 -> 300 to json.encodeToString(
                LaunchAmbiguous(partial.map { it.label })
            )
            else -> 404 to """{"error":${jsonString("no screen or app matches '${req.query}'")}}"""
        }
    }

    private fun openScreen(route: String): Pair<Int, String> {
        neth.iecal.questphone.app.navigation.PendingNavigation.pendingRoute = route
        val intent = Intent(this, neth.iecal.questphone.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
        return 200 to """{"status":"opened","route":${jsonString(route)}}"""
    }

    /** Named shortcuts to in-app screens — add more here as needed, no other code
     *  needs to change (see [NAMED_SCREENS]). */
    private fun openNamedScreen(name: String): Pair<Int, String> {
        val target = normalizeKey(name)
        val route = NAMED_SCREENS.entries.firstOrNull { normalizeKey(it.key) == target }?.value
            ?: return 404 to """{"error":${jsonString("unknown screen '$name'. try: ${NAMED_SCREENS.keys.joinToString()}")}}"""
        return openScreen(route)
    }

    private fun listInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        return launchables.mapNotNull { resolveInfo ->
            try {
                val pkg = resolveInfo.activityInfo.packageName
                AppInfo(pkg, resolveInfo.loadLabel(pm).toString())
            } catch (_: Exception) { null }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private suspend fun completeQuest(id: String): Pair<Int, String> {
        val quest = questRepository.getQuestById(id) ?: return 404 to """{"error":"quest not found"}"""

        quest.last_completed_on = getCurrentDate()
        quest.synced = false
        quest.last_updated = System.currentTimeMillis()
        questRepository.updateQuest(quest)

        if (quest.statReward1 > 0 || quest.statReward2 > 0 || quest.statReward3 > 0 || quest.statReward4 > 0) {
            val sp = userRepository.userInfo.statPoints
            userRepository.userInfo.statPoints = sp.copy(
                value1 = sp.value1 + quest.statReward1,
                value2 = sp.value2 + quest.statReward2,
                value3 = sp.value3 + quest.statReward3,
                value4 = sp.value4 + quest.statReward4
            )
            userRepository.saveUserInfo()
        }

        // Same reward path the in-app quest view uses — grants XP/coins as soon as
        // QuestPhone's UI is next composed.
        rewardUserForQuestCompl(quest)

        return 200 to json.encodeToString(CompleteResponse(quest.id, quest.reward))
    }

    private fun writeResponse(out: OutputStream, status: Int, body: String, contentType: String = "application/json") {
        val statusText = when (status) {
            200 -> "OK"
            300 -> "Multiple Choices"
            402 -> "Payment Required"
            404 -> "Not Found"
            400 -> "Bad Request"
            else -> "Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun jsonString(s: String) = "\"" + s.replace("\"", "'") + "\""

    companion object {
        const val PORT = 8342
        private const val NOTIF_ID = 8342

        /** name used in `POST /open/{name}` and as a match candidate for
         *  `POST /launch` -> RootRoute path. Every entry here is a screen that's
         *  already navigated to elsewhere in the app with no extra arguments, so
         *  it's safe to jump to directly. Add more as needed — nothing else
         *  needs touching. */
        private val NAMED_SCREENS: Map<String, String> = mapOf(
            "home" to neth.iecal.questphone.app.navigation.RootRoute.HomeScreen.route,
            "app list" to neth.iecal.questphone.app.navigation.RootRoute.AppList.route,
            "widgets" to neth.iecal.questphone.app.navigation.RootRoute.WidgetScreen.route,
            "customize" to neth.iecal.questphone.app.navigation.RootRoute.Customize.route,
            "store" to neth.iecal.questphone.app.navigation.RootRoute.Store.route,
            "profile" to neth.iecal.questphone.app.navigation.RootRoute.Profile.route,
            "profile settings" to neth.iecal.questphone.app.navigation.RootRoute.ProfileSettings.route,
            "github sync" to neth.iecal.questphone.app.navigation.RootRoute.GitHubSync.route,
            "tracker settings" to neth.iecal.questphone.app.navigation.RootRoute.TrackerSettings.route,
            "settings" to neth.iecal.questphone.app.navigation.RootRoute.LauncherSettings.route,
            "launcher settings" to neth.iecal.questphone.app.navigation.RootRoute.LauncherSettings.route,
            "screentime" to neth.iecal.questphone.app.navigation.RootRoute.ShowScreentimeStats.route,
            "hidden apps" to neth.iecal.questphone.app.navigation.RootRoute.HiddenAppsSettings.route,
            "custom voice actions" to neth.iecal.questphone.app.navigation.RootRoute.CustomVoiceActionsSettings.route,
            "study quota" to neth.iecal.questphone.app.navigation.RootRoute.StudyQuotaSettings.route,
            "stranger mode" to neth.iecal.questphone.app.navigation.RootRoute.StrangerModeSettings.route,
            "json quest converter" to neth.iecal.questphone.app.navigation.RootRoute.JsonQuestConverter.route,
            "crash log" to neth.iecal.questphone.app.navigation.RootRoute.CrashLog.route,
            "gemma chat" to neth.iecal.questphone.app.navigation.RootRoute.GemmaChat.route,
            "focus timer" to neth.iecal.questphone.app.navigation.RootRoute.FocusTimer.route,
            "quest notifications" to neth.iecal.questphone.app.navigation.RootRoute.QuestNotifications.route,
            "stat settings" to neth.iecal.questphone.app.navigation.RootRoute.StatSettings.route,
            "debug centre" to neth.iecal.questphone.app.navigation.RootRoute.DebugCentre.route,
            "debug center" to neth.iecal.questphone.app.navigation.RootRoute.DebugCentre.route,
            "add quest" to neth.iecal.questphone.app.navigation.RootRoute.AddNewQuest.route,
            "list quests" to neth.iecal.questphone.app.navigation.RootRoute.ListAllQuest.route,
            "templates" to neth.iecal.questphone.app.navigation.RootRoute.SelectTemplates.route,
            "setup template" to neth.iecal.questphone.app.navigation.RootRoute.SetupTemplate.route,
            "quest plan generator" to neth.iecal.questphone.app.navigation.RootRoute.QuestPlanGenerator.route,
            "coin transaction log" to neth.iecal.questphone.app.navigation.RootRoute.CoinTransactionLog.route,
            "render sync" to neth.iecal.questphone.app.navigation.RootRoute.RenderSync.route,
            "stat history" to neth.iecal.questphone.app.navigation.RootRoute.StatHistory.route,
            "notification blocker" to neth.iecal.questphone.app.navigation.RootRoute.NotificationBlockerSettings.route,
            "ai memory trainer" to neth.iecal.questphone.app.navigation.RootRoute.AiMemoryTrainer.route,
            "boss battle" to neth.iecal.questphone.app.navigation.RootRoute.BossBattle.route,
            "quest chains" to neth.iecal.questphone.app.navigation.RootRoute.QuestChains.route,
            "rival" to neth.iecal.questphone.app.navigation.RootRoute.RivalScreen.route,
            "productivity score" to neth.iecal.questphone.app.navigation.RootRoute.ProductivityScore.route,
            "kai personality" to neth.iecal.questphone.app.navigation.RootRoute.KaiPersonality.route,
            "weekly report" to neth.iecal.questphone.app.navigation.RootRoute.WeeklyReport.route,
            "lockdown settings" to neth.iecal.questphone.app.navigation.RootRoute.LockdownSettings.route,
            "ascension hall" to neth.iecal.questphone.app.navigation.RootRoute.AscensionHall.route,
            "rpg settings" to neth.iecal.questphone.app.navigation.RootRoute.RpgSettings.route,
            "people database" to neth.iecal.questphone.app.navigation.RootRoute.PeopleDatabase.route,
            "jarvis" to neth.iecal.questphone.app.navigation.RootRoute.Jarvis.route,
            "my life" to neth.iecal.questphone.app.navigation.RootRoute.MyLife.route,
            "my life settings" to neth.iecal.questphone.app.navigation.RootRoute.MyLifeSettings.route,
            "app locker settings" to neth.iecal.questphone.app.navigation.RootRoute.AppLockerSettings.route,
            "app vault" to neth.iecal.questphone.app.navigation.RootRoute.AppVaultSettings.route,
            "vault" to neth.iecal.questphone.app.navigation.RootRoute.AppVaultSettings.route,
            "calculator" to neth.iecal.questphone.app.navigation.RootRoute.CalculatorVault.route,
            "plugin store" to neth.iecal.questphone.app.navigation.RootRoute.PluginStore.route,
            "plugins" to neth.iecal.questphone.app.navigation.RootRoute.PluginStore.route,
            "game booster" to neth.iecal.questphone.app.navigation.RootRoute.GameBoosterSettings.route
        )
    }
}

@Serializable
private data class BalanceResponse(val coins: Int)

@Serializable
private data class StatsResponse(
    val level: Int,
    val xp: Int,
    val diamonds: Int,
    val streak: StreakData,
    val statPoints: StatPoints
)

@Serializable
private data class InventoryResponse(val items: Map<String, Int>)

@Serializable
private data class CompleteResponse(val quest_id: String, val reward_coins: Int, val status: String = "completed")

@Serializable
private data class CreateQuestRequest(
    val title: String,
    val reward: Int = 5,
    /** 1=Swift Mark (quick manual check-off, default), 2=Deep Focus (timed session,
     *  see [duration_minutes]), 3=AI Snap (photo-verified, see [task_description]),
     *  4=External Integration (needs finishing in-app — see [external_url]),
     *  5=Optional Quest (doesn't count against streak requirements). */
    val type: Int = 1,
    val duration_minutes: Int = 25,
    val task_description: String = "",
    val external_url: String = "",
    val selected_days: Set<nethical.questphone.data.DayOfWeek> =
        nethical.questphone.data.DayOfWeek.values().toSet(),
    val time_range: List<Int> = listOf(0, 24),
    val isHardLock: Boolean = false,
    val instructions: String = ""
)

@Serializable
private data class OpenAppRequest(val `package`: String, val confirm: Boolean = false, val coins: Int = 5)

@Serializable
private data class LaunchRequest(val query: String, val confirm: Boolean = false, val coins: Int = 5)

@Serializable
private data class LaunchAmbiguous(val matches: List<String>, val error: String = "multiple apps match — be more specific")

@Serializable
private data class LockedAppResponse(
    val `package`: String,
    val cost_coins: Int,
    val unlock_minutes: Int,
    val locked: Boolean = true,
    val message: String = "this app is blocked — spend coins to unlock it?"
)

@Serializable
private data class AppInfo(val packageName: String, val label: String)
