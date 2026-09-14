package neth.iecal.questphone.app.screens.terminal

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.launcher.AppListViewModel
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.data.DayOfWeek
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Calendar
import javax.inject.Inject

private enum class WizardStep {
    TITLE, DAYS, TIME_YN, TIME_START, TIME_END, REWARD, HARDLOCK, DONE
}

private data class QuestDraft(
    var title:     String      = "",
    var days:      Set<DayOfWeek> = emptySet(),
    var hasTime:   Boolean     = false,
    var startHour: Int         = 0,
    var endHour:   Int         = 24,
    var reward:    Int         = 10,
    var hardLock:  Boolean     = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val questRepository: QuestRepository,
    private val userRepository:  UserRepository
) : ViewModel() {

    val lines       = mutableStateOf<List<TermLine>>(emptyList())
    val input       = mutableStateOf("")
    var wizardActive = false
        private set

    private val _lines      = mutableListOf<TermLine>()
    private var wizardStep: WizardStep? = null
    private var draft       = QuestDraft()
    private var currentDir  = "~"
    private var appCache: List<android.content.pm.ApplicationInfo> = emptyList()

    init { boot() }

    // ── Output helpers ─────────────────────────────────────────────
    private fun add(line: TermLine) {
        _lines.add(line)
        lines.value = _lines.toList()
    }

    private fun out(text: String, color: androidx.compose.ui.graphics.Color = TermColors.White) =
        add(TermLine.Output(text, color))

    private fun ok(t: String)   = out("✓ $t", TermColors.Green)
    private fun err(t: String)  = out("✗ $t", TermColors.Red)
    private fun info(t: String) = out("  $t", TermColors.Gray)

    private fun boot() {
        add(TermLine.Output("QuestPhone Terminal v2.0  [shell + quest manager]", TermColors.Green))
        add(TermLine.Output("Type 'help' for all commands.", TermColors.Gray))
        add(TermLine.Blank)
    }

    // ── Enter handler ──────────────────────────────────────────────
    suspend fun onEnter(ctx: Context, nav: androidx.navigation.NavController, appVm: AppListViewModel) {
        val raw = input.value.trim()
        input.value = ""
        if (raw.isBlank()) return

        if (wizardActive) {
            add(TermLine.Output("  > $raw", TermColors.Yellow))
            handleWizard(raw)
            return
        }

        add(TermLine.Input(raw))
        handleCommand(raw, ctx, nav, appVm)
    }

    // ── Command router ─────────────────────────────────────────────
    private fun handleCommand(
        raw: String, ctx: Context,
        nav: androidx.navigation.NavController,
        appVm: AppListViewModel
    ) {
        val cmd   = raw.trim()
        val lower = cmd.lowercase()
        val args  = cmd.split(" ")

        when {
            // ── Real shell execution ───────────────────────────────
            lower.startsWith("pkg ") ||
            lower.startsWith("apt ") ||
            lower.startsWith("apt-get ") ||
            lower.startsWith("pip ") ||
            lower.startsWith("pip3 ") ||
            lower.startsWith("python") ||
            lower.startsWith("node ") ||
            lower.startsWith("bash ") ||
            lower.startsWith("sh ") -> runShell(cmd)

            // File system
            lower.startsWith("ls ") && !lower.startsWith("ls /quest") &&
            !lower.startsWith("ls /app") && !lower.startsWith("ls /bank") -> {
                val path = args.getOrElse(1) { "." }
                runShell("ls -la $path")
            }
            lower == "ls" && currentDir !in listOf("/quest", "/app", "/bank") ->
                runShell("ls -la $currentDir".replace("~", System.getenv("HOME") ?: "/data/data/com.termux/files/home"))

            lower.startsWith("cat ") -> runShell(cmd)
            lower.startsWith("mkdir ") -> runShell(cmd)
            lower.startsWith("rm ") && !lower.startsWith("rm /quest/") -> runShell(cmd)
            lower.startsWith("cp ") -> runShell(cmd)
            lower.startsWith("mv ") -> runShell(cmd)
            lower.startsWith("echo ") -> runShell(cmd)
            lower.startsWith("grep ") -> runShell(cmd)
            lower.startsWith("find ") -> runShell(cmd)
            lower.startsWith("curl ") -> runShell(cmd)
            lower.startsWith("wget ") -> runShell(cmd)
            lower.startsWith("chmod ") -> runShell(cmd)
            lower.startsWith("zip ") || lower.startsWith("unzip ") -> runShell(cmd)
            lower.startsWith("git ") -> runShell(cmd)
            lower.startsWith("nano ") || lower.startsWith("vim ") -> err("Text editors not supported. Use 'cat > file' via shell")
            lower == "pwd" -> {
                runShell("pwd")
                currentDir = currentDir
            }
            lower == "df" || lower == "df -h" -> runShell("df -h")
            lower == "free" || lower == "free -h" -> runShell("free -h")
            lower == "uname -a" || lower == "uname" -> runShell("uname -a")
            lower.startsWith("ps") -> runShell(cmd)
            lower.startsWith("kill ") -> handleKill(args.getOrElse(1) { "" }, ctx)
            lower.startsWith("env") -> runShell("env")
            lower.startsWith("export ") -> runShell(cmd)
            lower.startsWith("which ") -> runShell(cmd)
            lower == "id" || lower == "whoami" -> {
                runShell("id")
                val coins = userRepository.coinsState.value ?: 0
                info("gc: ◈$coins  |  user: krishna")
            }

            // ── Navigation ─────────────────────────────────────────
            lower == "help" -> showHelp()
            lower == "clear" || lower == "cls" -> {
                _lines.clear(); lines.value = emptyList(); boot()
            }

            lower == "cd /quest" || lower == "cd quest" -> {
                currentDir = "/quest"
                viewModelScope.launch { listQuests() }
            }
            lower == "cd /app" || lower == "cd app" -> {
                currentDir = "/app"
                listApps(ctx)
            }
            lower == "cd /bank" || lower == "cd bank" -> {
                currentDir = "/bank"
                listBank()
            }
            lower == "cd /settings" || lower == "cd settings" -> {
                currentDir = "/settings"
                nav.navigate(RootRoute.LauncherSettings.route)
                ok("Opening settings...")
            }
            lower == "cd ~" || lower == "cd" || lower == "cd /" -> {
                currentDir = "~"
                nav.navigate(RootRoute.HomeScreen.route)
            }
            lower.startsWith("cd ") -> {
                val path = args.getOrElse(1) { "." }
                runShell("cd $path && pwd")
                currentDir = path
            }

            // ── Quest commands ─────────────────────────────────────
            lower == "quest add" || lower == "add quest" || lower == "touch /quest/new" ->
                startQuestWizard()

            lower == "ls /quest" || (lower == "ls" && currentDir == "/quest") ->
                viewModelScope.launch { listQuests() }

            lower.startsWith("quest delete ") || lower.startsWith("rm /quest/") -> {
                val title = cmd.substringAfter("quest delete ").substringAfter("/quest/").trim()
                viewModelScope.launch { deleteQuest(title) }
            }

            lower.startsWith("quest done ") || lower.startsWith("complete ") -> {
                val title = cmd.substringAfter("quest done ").substringAfter("complete ").trim()
                viewModelScope.launch { completeQuest(title) }
            }

            // ── App commands ───────────────────────────────────────
            lower == "ls /app" || (lower == "ls" && currentDir == "/app") -> listApps(ctx)
            lower.startsWith("open ") -> openApp(args.drop(1).joinToString(" "), ctx)
            lower.startsWith("launch ") -> openApp(args.drop(1).joinToString(" "), ctx)

            // /ohi — open Instagram from hidden apps (same runner as App Vault)
            lower == "/ohi" -> {
                val instaPkg = "com.instagram.android"
                neth.iecal.questphone.core.vault.AppVaultManager.setVaultLaunch(ctx, instaPkg)
                neth.iecal.questphone.core.vault.AppVaultManager.setVaultDisabledUntil(
                    ctx, System.currentTimeMillis() + 60_000L)
                openHiddenApp(instaPkg, ctx)
            }

            // /oh <package> — open ANY hidden/vault app by package name
            lower.startsWith("/oh ") -> {
                val pkg = args.drop(1).joinToString(" ").trim()
                neth.iecal.questphone.core.vault.AppVaultManager.setVaultLaunch(ctx, pkg)
                neth.iecal.questphone.core.vault.AppVaultManager.setVaultDisabledUntil(
                    ctx, System.currentTimeMillis() + 60_000L)
                openHiddenApp(pkg, ctx)
            }

            // Open by number from ls /app
            lower.matches(Regex("\\d+")) -> {
                val idx = lower.toIntOrNull()?.minus(1) ?: -1
                if (currentDir == "/app" && idx >= 0 && idx < appCache.size) {
                    val app = appCache[idx]
                    val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        ok("Launching ${ctx.packageManager.getApplicationLabel(app)}...")
                    } else err("Cannot launch")
                } else err("Type 'ls /app' first, then enter number")
            }

            // ── Bank commands ──────────────────────────────────────
            lower == "ls /bank" || (lower == "ls" && currentDir == "/bank") -> listBank()
            lower == "balance" || lower == "gc" -> {
                val coins = userRepository.coinsState.value ?: 0
                out("◈ $coins GC", TermColors.Yellow)
            }

            // ── System info ────────────────────────────────────────
            lower == "neofetch" || lower == "fetch" -> neofetch(ctx)
            lower == "battery" || lower == "bat" -> showBattery(ctx)
            lower == "stats" || lower == "top" -> viewModelScope.launch { showStats() }
            lower == "wifi" || lower == "ifconfig" -> showWifi(ctx)
            lower == "storage" || lower == "disk" -> runShell("df -h /sdcard")

            // ── Notifications + Alarms ─────────────────────────────
            lower.startsWith("notify ") -> {
                val msg = cmd.substringAfter("notify ").trim()
                sendNotification(ctx, msg)
            }
            lower.startsWith("alarm ") -> {
                val time = args.getOrElse(1) { "" }
                setAlarm(ctx, time)
            }
            lower.startsWith("timer ") -> {
                val mins = args.getOrElse(1) { "25" }.toIntOrNull() ?: 25
                setTimer(ctx, mins)
            }

            // ── Debug / settings ───────────────────────────────────
            lower == "debug" -> nav.navigate(RootRoute.DebugCentre.route)
            lower == "exit" || lower == "quit" -> {
                ok("Exiting terminal...")
                nav.navigate(RootRoute.HomeScreen.route)
            }

            // ── Fallback to shell ──────────────────────────────────
            else -> runShell(cmd)
        }
    }

    // ── Real shell execution ───────────────────────────────────────
    private fun runShell(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("sh", "-c", cmd)
                pb.environment()["HOME"] =
                    System.getenv("HOME") ?: "/data/data/com.termux/files/home"
                pb.environment()["PATH"] =
                    (System.getenv("PATH") ?: "") +
                    ":/data/data/com.termux/files/usr/bin" +
                    ":/data/data/com.termux/files/usr/bin/applets"
                pb.redirectErrorStream(true)
                val proc   = pb.start()
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                var lineCount = 0
                while (reader.readLine().also { line = it } != null && lineCount < 200) {
                    val l = line!!
                    withContext(Dispatchers.Main) {
                        out(l, when {
                            l.startsWith("error") || l.startsWith("Error") ||
                            l.startsWith("fatal") -> TermColors.Red
                            l.startsWith("warning") || l.startsWith("Warning") ->
                                TermColors.Yellow
                            l.startsWith("OK") || l.startsWith("✓") -> TermColors.Green
                            else -> TermColors.White
                        })
                    }
                    lineCount++
                }
                if (lineCount >= 200) withContext(Dispatchers.Main) {
                    info("... output truncated at 200 lines")
                }
                proc.waitFor()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    err("shell error: ${e.message}")
                    info("Is Termux installed? Some commands need Termux binaries")
                }
            }
        }
    }

    // ── App listing ────────────────────────────────────────────────
    private fun listApps(ctx: Context) {
        val pm   = ctx.packageManager
        val all  = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        appCache = all
        add(TermLine.Blank)
        out("NUM  APP NAME               PACKAGE", TermColors.Cyan)
        out("─────────────────────────────────────────────────", TermColors.Gray)
        all.forEachIndexed { i, app ->
            val name = pm.getApplicationLabel(app).toString()
            out("%03d  %-22s %s".format(
                i + 1,
                name.take(22),
                app.packageName.take(35)
            ), if (i % 2 == 0) TermColors.White else TermColors.Gray)
        }
        add(TermLine.Blank)
        info("${all.size} apps  ·  type number or 'open <name>' to launch")
        add(TermLine.Blank)
    }

    // ── Open app by name or package ───────────────────────────────
    private fun openApp(query: String, ctx: Context) {
        val pm   = ctx.packageManager
        val q    = query.trim().lowercase()
        if (q.isBlank()) { err("Usage: open <app name or package>"); return }

        // Match against package name exact, then label contains
        val all  = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }

        val match = all.firstOrNull { it.packageName.equals(q, ignoreCase = true) }
            ?: all.firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(q) }

        if (match == null) { err("App not found: $query"); return }

        val intent = pm.getLaunchIntentForPackage(match.packageName)
            ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent != null) {
            ctx.startActivity(intent)
            out("Launching ${pm.getApplicationLabel(match)}…", TermColors.GreenDim)
        } else {
            err("Cannot launch ${match.packageName}")
        }
    }

    // ── Open hidden/vault app by package ──────────────────────────
    private fun openHiddenApp(pkg: String, ctx: Context) {
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent != null) {
            ctx.startActivity(intent)
            out("Launching $pkg…", TermColors.GreenDim)
        } else {
            err("Package not found or not launchable: $pkg")
        }
    }

    // ── Quest listing ──────────────────────────────────────────────
    private suspend fun listQuests() {
        val all     = questRepository.getAllQuestsAsList()
        val todayStr = java.time.LocalDate.now().toString()
        val todayDay = nethical.questphone.core.core.utils.getCurrentDay()
        if (all.isEmpty()) { info("No quests. Run: quest add"); return }
        add(TermLine.Blank)
        out("ST   TITLE                    DAYS       REWARD  LOCK", TermColors.Cyan)
        out("────────────────────────────────────────────────────────", TermColors.Gray)
        all.sortedBy { it.title }.forEach { q ->
            val done    = q.last_completed_on == todayStr
            val active  = q.selected_days.any { it == todayDay } &&
                (q.start_date.isEmpty() || q.start_date <= todayStr) &&
                (q.auto_destruct.isEmpty() || q.auto_destruct >= todayStr)
            val status  = when { done -> "[✓]"; active -> "[●]"; else -> "[ ]" }
            val color   = when { done -> TermColors.GreenDim; active -> TermColors.White; else -> TermColors.Gray }
            val days    = q.selected_days.joinToString("") { d ->
                when(d) {
                    DayOfWeek.MON -> "M"; DayOfWeek.TUE -> "T"
                    DayOfWeek.WED -> "W"; DayOfWeek.THU -> "TH"
                    DayOfWeek.FRI -> "F"; DayOfWeek.SAT -> "SA"
                    DayOfWeek.SUN -> "SU"
                }
            }
            out("%-4s %-24s %-10s ◈%-6d %s".format(
                status, q.title.take(24), days.take(10),
                q.reward, if (q.isHardLock) "HARD" else ""
            ), color)
        }
        add(TermLine.Blank)
        info("${all.count { it.last_completed_on == todayStr }}/${all.size} done today")
        add(TermLine.Blank)
    }

    // ── Bank listing ───────────────────────────────────────────────
    private fun listBank() {
        val coins    = userRepository.coinsState.value ?: 0
        val holdings = userRepository.getStockHoldings()
        add(TermLine.Blank)
        out("── /bank ─────────────────────────────", TermColors.Cyan)
        info("balance : ◈$coins GC")
        if (holdings.isNotEmpty()) {
            add(TermLine.Blank)
            out("SYMBOL   SHARES      AVG BUY    INVESTED", TermColors.Cyan)
            out("──────────────────────────────────────────", TermColors.Gray)
            holdings.forEach { (sym, h) ->
                out("%-8s %-11.4f ◈%-9.2f ◈%.2f".format(
                    sym, h.quantity, h.avgBuyPrice, h.totalInvested))
            }
        } else info("no holdings")
        add(TermLine.Blank)
        info("open bank screen: cd /bank → then navigate in app")
        add(TermLine.Blank)
    }

    // ── Quest wizard ───────────────────────────────────────────────
    private fun startQuestWizard() {
        wizardActive = true
        wizardStep   = WizardStep.TITLE
        draft        = QuestDraft()
        add(TermLine.Blank)
        out("── quest add ─────────────────────────", TermColors.Cyan)
        add(TermLine.Prompt("Title:", "quest name"))
    }

    private fun handleWizard(input: String) {
        when (wizardStep) {
            WizardStep.TITLE -> {
                if (input.isBlank()) { err("Title cannot be empty"); return }
                draft.title = input
                wizardStep  = WizardStep.DAYS
                add(TermLine.Prompt("Days:", "M T W TH F SA SU  or  ALL"))
            }
            WizardStep.DAYS -> {
                val p = parseDays(input)
                if (p.isEmpty()) { err("Invalid. Use: M T W TH F SA SU or ALL"); return }
                draft.days = p
                wizardStep = WizardStep.TIME_YN
                add(TermLine.Prompt("Time range? [y/n]:"))
            }
            WizardStep.TIME_YN -> when (input.lowercase()) {
                "y", "yes" -> {
                    draft.hasTime = true; wizardStep = WizardStep.TIME_START
                    add(TermLine.Prompt("Start hour [0-23]:"))
                }
                "n", "no" -> {
                    draft.hasTime = false; wizardStep = WizardStep.REWARD
                    add(TermLine.Prompt("GC Reward:", "default 10"))
                }
                else -> err("Enter y or n")
            }
            WizardStep.TIME_START -> {
                val h = input.toIntOrNull()
                if (h == null || h !in 0..23) { err("Enter 0–23"); return }
                draft.startHour = h; wizardStep = WizardStep.TIME_END
                add(TermLine.Prompt("End hour [0-23]:"))
            }
            WizardStep.TIME_END -> {
                val h = input.toIntOrNull()
                if (h == null || h !in 0..23) { err("Enter 0–23"); return }
                if (h <= draft.startHour) { err("End must be after start"); return }
                draft.endHour = h; wizardStep = WizardStep.REWARD
                add(TermLine.Prompt("GC Reward:", "default 10"))
            }
            WizardStep.REWARD -> {
                draft.reward = input.toIntOrNull()?.coerceIn(1, 9999) ?: 10
                wizardStep   = WizardStep.HARDLOCK
                add(TermLine.Prompt("Hard lock? [y/n]:", "fail = lose GC"))
            }
            WizardStep.HARDLOCK -> {
                draft.hardLock = input.lowercase() in listOf("y", "yes")
                wizardStep     = WizardStep.DONE
                viewModelScope.launch { saveQuest() }
            }
            else -> {}
        }
    }

    private suspend fun saveQuest() {
        val q = CommonQuestInfo(
            title         = draft.title,
            reward        = draft.reward,
            selected_days = draft.days,
            time_range    = if (draft.hasTime) listOf(draft.startHour, draft.endHour)
                            else listOf(0, 24),
            isHardLock    = draft.hardLock,
            auto_destruct = "9999-12-31"
        )
        questRepository.upsertQuest(q)
        add(TermLine.Blank)
        out("── created ───────────────────────────", TermColors.Green)
        ok("\"${draft.title}\"")
        info("days   : ${draft.days.joinToString(" ") { it.name }}")
        info("reward : ◈${draft.reward} GC")
        info("lock   : ${if (draft.hardLock) "HARD" else "soft"}")
        add(TermLine.Blank)
        wizardActive = false; wizardStep = null
    }

    private suspend fun deleteQuest(title: String) {
        val q = questRepository.getAllQuestsAsList()
            .find { it.title.equals(title, ignoreCase = true) }
        if (q == null) { err("not found: $title"); return }
        questRepository.deleteQuest(q)
        ok("deleted: ${q.title}")
    }

    private suspend fun completeQuest(title: String) {
        val today = java.time.LocalDate.now().toString()
        val q = questRepository.getAllQuestsAsList()
            .find { it.title.equals(title, ignoreCase = true) }
        if (q == null) { err("not found: $title"); return }
        if (q.last_completed_on == today) { info("already done today"); return }
        questRepository.upsertQuest(q.copy(last_completed_on = today))
        userRepository.addCoins(q.reward, "Quest: ${q.title}")
        ok("${q.title}  +◈${q.reward} GC")
    }

    // ── System info ────────────────────────────────────────────────
    private fun neofetch(ctx: Context) {
        val pm      = ctx.packageManager
        val pkgInfo = pm.getPackageInfo(ctx.packageName, 0)
        val coins   = userRepository.coinsState.value ?: 0
        val bm      = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val bat     = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val ram     = Runtime.getRuntime()
        val usedMb  = (ram.totalMemory() - ram.freeMemory()) / 1024 / 1024
        val totalMb = Runtime.getRuntime().maxMemory() / 1024 / 1024

        add(TermLine.Blank)
        out("  ██████╗ ██████╗ ", TermColors.Green)
        out("  ██╔══██╗██╔══██╗  krishna@questphone", TermColors.Green)
        out("  ██║  ██║██████╔╝  ─────────────────────────────", TermColors.Green)
        out("  ██║  ██║██╔═══╝   OS: Android ${Build.VERSION.RELEASE}", TermColors.White)
        out("  ██████╔╝██║       Device: ${Build.MANUFACTURER} ${Build.MODEL}", TermColors.White)
        out("  ╚═════╝ ╚═╝       App: QuestPhone v${pkgInfo.versionName}", TermColors.White)
        out("                    GC: ◈$coins", TermColors.Yellow)
        out("                    Battery: $bat%", TermColors.White)
        out("                    RAM: ${usedMb}MB / ${totalMb}MB", TermColors.White)
        out("                    API: ${Build.VERSION.SDK_INT}", TermColors.White)
        out("                    Arch: ${Build.SUPPORTED_ABIS.firstOrNull()}", TermColors.White)
        add(TermLine.Blank)
    }

    private fun showBattery(ctx: Context) {
        val bm  = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val mA  = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chrg= bm.isCharging
        val bar = "█".repeat(lvl / 10) + "░".repeat(10 - lvl / 10)
        out("[$bar] $lvl%  ${if (chrg) "⚡ charging" else "discharging"}  ${mA/1000}mA",
            when { lvl > 50 -> TermColors.Green; lvl > 20 -> TermColors.Yellow; else -> TermColors.Red })
    }

    private fun showWifi(ctx: Context) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val info = wm.connectionInfo
        val ip   = android.text.format.Formatter.formatIpAddress(info.ipAddress)
        out("SSID : ${info.ssid}", TermColors.Cyan)
        out("IP   : $ip", TermColors.White)
        out("RSSI : ${info.rssi} dBm", TermColors.White)
        out("Speed: ${info.linkSpeed} Mbps", TermColors.White)
    }

    private fun handleKill(appName: String, ctx: Context) {
        if (appName.isBlank()) { err("Usage: kill <package>"); return }
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        try {
            am.killBackgroundProcesses(appName)
            ok("Killed: $appName")
        } catch (e: Exception) { err("Cannot kill: ${e.message}") }
    }

    private fun sendNotification(ctx: Context, msg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                "terminal", "Terminal", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = androidx.core.app.NotificationCompat.Builder(ctx, "terminal")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("QuestPhone Terminal")
            .setContentText(msg)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), n)
        ok("Notification sent: $msg")
    }

    private fun setAlarm(ctx: Context, time: String) {
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) { err("Format: alarm HH:MM"); return }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, parts[0])
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, parts[1])
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "QuestPhone Terminal")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        ok("Alarm set for ${time}")
    }

    private fun setTimer(ctx: Context, mins: Int) {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, mins * 60)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "QuestPhone Timer")
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        ok("Timer set: ${mins}min")
    }

    private suspend fun showStats() {
        val quests = questRepository.getAllQuestsAsList()
        val coins  = userRepository.coinsState.value ?: 0
        val today  = java.time.LocalDate.now().toString()
        add(TermLine.Blank)
        out("── stats ─────────────────────────────", TermColors.Cyan)
        info("user       : krishna")
        info("gc         : ◈$coins")
        info("quests     : ${quests.size} total")
        info("done today : ${quests.count { it.last_completed_on == today }}")
        info("best streak: ${quests.maxOfOrNull { it.completionStreak } ?: 0} days")
        info("holdings   : ${userRepository.getStockHoldings().size} stocks")
        add(TermLine.Blank)
    }

    private fun parseDays(input: String): Set<DayOfWeek> {
        if (input.uppercase().trim() == "ALL") return DayOfWeek.entries.toSet()
        val map = mapOf(
            "M" to DayOfWeek.MON, "T" to DayOfWeek.TUE,
            "W" to DayOfWeek.WED, "TH" to DayOfWeek.THU,
            "F" to DayOfWeek.FRI, "SA" to DayOfWeek.SAT,
            "SU" to DayOfWeek.SUN
        )
        return input.uppercase().split(" ", ",").mapNotNull { map[it.trim()] }.toSet()
    }

    // ── Help ───────────────────────────────────────────────────────
    private fun showHelp() {
        add(TermLine.Blank)
        out("── hidden apps ───────────────────────", TermColors.Cyan)
        info("/ohi              open Instagram (hidden)")
        info("/oh <pkg>         open any hidden/vault app")
        add(TermLine.Blank)
        out("── shell (real Termux commands) ──────", TermColors.Cyan)
        info("pkg upgrade / apt update / pip install")
        info("ls  cat  mkdir  rm  cp  mv  find  grep")
        info("git clone / git pull / git push")
        info("curl  wget  python  node  bash")
        info("ps  kill <pkg>  df  free  env")
        add(TermLine.Blank)
        out("── navigation ────────────────────────", TermColors.Cyan)
        info("cd /quest   cd /app   cd /bank   cd /settings   cd ~")
        add(TermLine.Blank)
        out("── quests ────────────────────────────", TermColors.Cyan)
        info("quest add          wizard to add quest")
        info("ls /quest          list all quests")
        info("quest done <title> mark quest complete")
        info("quest delete <title>")
        add(TermLine.Blank)
        out("── apps ──────────────────────────────", TermColors.Cyan)
        info("ls /app            list installed apps")
        info("open <name>        launch by name")
        info("<number>           launch from ls /app list")
        add(TermLine.Blank)
        out("── bank ──────────────────────────────", TermColors.Cyan)
        info("balance            GC balance")
        info("ls /bank           holdings")
        add(TermLine.Blank)
        out("── system ────────────────────────────", TermColors.Cyan)
        info("neofetch           system info")
        info("battery            battery status")
        info("wifi               WiFi info")
        info("stats              quest + GC stats")
        info("notify <msg>       send notification")
        info("alarm HH:MM        set alarm")
        info("timer <mins>       set countdown timer")
        info("kill <package>     force stop app")
        info("clear  exit  debug  whoami")
        add(TermLine.Blank)
        out("── days format ───────────────────────", TermColors.Gray)
        info("M T W TH F SA SU   or   ALL")
        add(TermLine.Blank)
    }
}
