package neth.iecal.questphone.app.screens.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.data.DayOfWeek
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar
import javax.inject.Inject

private enum class WizardStep {
    TITLE, DAYS, TIME_YN, TIME_START, TIME_END, REWARD, HARDLOCK, DONE
}

private data class QuestDraft(
    var title:     String         = "",
    var days:      Set<DayOfWeek> = emptySet(),
    var hasTime:   Boolean        = false,
    var startHour: Int            = 0,
    var endHour:   Int            = 24,
    var reward:    Int            = 10,
    var hardLock:  Boolean        = false
)

// ── Session model ──────────────────────────────────────────────────
private data class Session(
    val id:     String,
    val lines:  MutableList<TermLine> = mutableListOf(),
    var dir:    String = "~"
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val questRepository: QuestRepository,
    private val userRepository:  UserRepository
) : ViewModel() {

    // ── Public state ───────────────────────────────────────────────
    val lines      = mutableStateOf<List<TermLine>>(emptyList())
    val input      = mutableStateOf("")
    val sessions   = mutableStateOf<List<String>>(listOf("1"))
    var currentSessionId = "1"
        private set
    var wizardActive = false
        private set

    // ── Private ────────────────────────────────────────────────────
    private val sessionMap    = mutableMapOf("1" to Session("1"))
    private var activeSession = sessionMap["1"]!!
    private var wizardStep:   WizardStep? = null
    private var draft         = QuestDraft()
    private var sessionCounter = 1

    // App cache — persists across commands so number-launch works
    private var appCache: List<android.content.pm.ApplicationInfo> = emptyList()

    // Command history
    private val history     = mutableListOf<String>()
    private var historyIdx  = -1

    // Termux binary paths
    private val termuxBin = listOf(
        "/data/data/com.termux/files/usr/bin",
        "/data/data/com.termux/files/usr/bin/applets",
        "/data/data/com.termux/files/usr/local/bin"
    )

    init { boot() }

    // ── Output helpers ─────────────────────────────────────────────
    private fun add(line: TermLine) {
        activeSession.lines.add(line)
        lines.value = activeSession.lines.toList()
    }

    private fun out(t: String, c: androidx.compose.ui.graphics.Color = TermColors.White) =
        add(TermLine.Output(t, c))
    private fun ok(t: String)   = out("✓ $t", TermColors.Green)
    private fun err(t: String)  = out("✗ $t", TermColors.Red)
    private fun info(t: String) = out("  $t", TermColors.Gray)

    private fun boot() {
        out("QuestPhone Terminal  [session ${activeSession.id}]", TermColors.Green)
        // Detect Termux
        val termuxBash = java.io.File("/data/data/com.termux/files/usr/bin/bash")
        if (termuxBash.exists()) {
            out("✓ Termux detected — full shell support active", TermColors.GreenDim)
            out("  pkg · apt · python · git · curl · wget · all available", TermColors.Gray)
        } else {
            out("⚠ Termux not found — basic shell only", TermColors.Yellow)
            out("  Install Termux from F-Droid for full support", TermColors.Gray)
        }
        out("Type 'help' for commands. Press ≡ for sessions.", TermColors.Gray)
        add(TermLine.Blank)
    }

    // ── Session management ─────────────────────────────────────────
    fun newSession() {
        sessionCounter++
        val sid  = sessionCounter.toString()
        val sess = Session(sid)
        sessionMap[sid]      = sess
        activeSession        = sess
        currentSessionId     = sid
        sessions.value       = sessionMap.keys.sorted()
        lines.value          = emptyList()
        boot()
    }

    fun switchSession(sid: String) {
        val s = sessionMap[sid] ?: return
        activeSession    = s
        currentSessionId = sid
        lines.value      = s.lines.toList()
    }

    // ── History navigation ─────────────────────────────────────────
    fun historyUp() {
        if (history.isEmpty()) return
        historyIdx = (historyIdx + 1).coerceAtMost(history.size - 1)
        input.value = history[history.size - 1 - historyIdx]
    }

    fun historyDown() {
        if (historyIdx <= 0) { historyIdx = -1; input.value = ""; return }
        historyIdx--
        input.value = history[history.size - 1 - historyIdx]
    }

    // ── Tab completion ─────────────────────────────────────────────
    fun onTab(ctx: Context) {
        val cur = input.value
        val cmds = listOf("quest add", "ls /quest", "ls /app", "ls /bank",
            "quest delete ", "quest done ", "open ", "balance", "neofetch",
            "battery", "wifi", "stats", "cd /quest", "cd /app", "cd /bank",
            "cd /settings", "cd ~", "help", "clear", "exit", "notify ",
            "alarm ", "timer ", "pkg ", "apt ", "git ", "curl ", "wget ")
        val match = cmds.filter { it.startsWith(cur) }
        if (match.size == 1) {
            input.value = match[0]
        } else if (match.size > 1) {
            out(match.joinToString("  "), TermColors.Cyan)
        }
    }

    fun onEsc() { wizardActive = false; wizardStep = null }

    // ── Main enter ─────────────────────────────────────────────────
    suspend fun onEnter(ctx: Context, nav: androidx.navigation.NavController) {
        val raw = input.value.trim()
        input.value  = ""
        historyIdx   = -1
        if (raw.isBlank()) return

        if (wizardActive) {
            add(TermLine.Output("  > $raw", TermColors.Yellow))
            handleWizard(raw)
            return
        }

        // Add to history
        if (history.isEmpty() || history.last() != raw)
            history.add(raw)

        add(TermLine.Input(raw))
        handleCommand(raw, ctx, nav)
    }

    // ── Command router ─────────────────────────────────────────────
    private fun handleCommand(
        raw: String,
        ctx: Context,
        nav: androidx.navigation.NavController
    ) {
        val lower = raw.trim().lowercase()
        val args  = raw.trim().split(Regex("\\s+"))

        when {
            // ── Navigation ─────────────────────────────────────────
            lower == "help"  -> showHelp()
            lower == "clear" || lower == "cls" -> {
                activeSession.lines.clear()
                lines.value = emptyList()
                boot()
            }
            lower == "exit" || lower == "quit" -> {
                ok("Exiting terminal...")
                nav.navigate("home_screen/")
            }
            lower == "debug" -> nav.navigate("debug_centre/")

            // cd
            lower == "cd /quest" || lower == "cd quest" -> {
                activeSession.dir = "/quest"
                viewModelScope.launch { listQuests(ctx) }
            }
            lower == "cd /app" || lower == "cd app" -> {
                activeSession.dir = "/app"
                listApps(ctx)
            }
            lower == "cd /bank" || lower == "cd bank" -> {
                activeSession.dir = "/bank"
                listBank()
            }
            lower == "cd /settings" || lower == "cd settings" -> {
                nav.navigate("launcher_settings/")
            }
            lower == "cd ~" || lower == "cd" -> {
                activeSession.dir = "~"
                nav.navigate("home_screen/")
            }
            lower.startsWith("cd ") -> {
                val path = args.getOrElse(1) { "." }
                activeSession.dir = path
                runShell("cd $path && pwd")
            }

            lower == "pwd" -> out(activeSession.dir)

            // ── Quest commands ─────────────────────────────────────
            lower == "quest add" || lower == "add quest" -> startQuestWizard()

            lower == "ls /quest" || (lower == "ls" && activeSession.dir == "/quest") ->
                viewModelScope.launch { listQuests(ctx) }

            lower.startsWith("quest delete ") -> {
                val t = raw.substringAfter("quest delete ").trim()
                viewModelScope.launch { deleteQuest(t) }
            }
            lower.startsWith("quest done ") || lower.startsWith("complete ") -> {
                val t = raw.substringAfter(" ").substringAfter(" ").trim()
                viewModelScope.launch { completeQuest(t) }
            }

            // ── App commands ───────────────────────────────────────
            lower == "ls /app" || (lower == "ls" && activeSession.dir == "/app") ->
                listApps(ctx)

            // Number — launch from cached app list
            lower.matches(Regex("0*\\d+")) -> {
                val idx = lower.trimStart('0').toIntOrNull()?.minus(1) ?: -1
                if (appCache.isEmpty()) {
                    err("Run 'ls /app' first to load app list")
                } else if (idx < 0 || idx >= appCache.size) {
                    err("Invalid number. Valid: 1–${appCache.size}")
                } else {
                    val app    = appCache[idx]
                    val pm     = ctx.packageManager
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        ok("Launching ${pm.getApplicationLabel(app)}...")
                    } else err("Cannot launch")
                }
            }

            lower.startsWith("open ") || lower.startsWith("launch ") -> {
                val name = args.drop(1).joinToString(" ")
                openApp(name, ctx)
            }

            // ── Bank ───────────────────────────────────────────────
            lower == "ls /bank" || (lower == "ls" && activeSession.dir == "/bank") -> listBank()
            lower == "balance" || lower == "gc" -> {
                out("◈ ${userRepository.coinsState.value ?: 0} GC", TermColors.Yellow)
            }

            // ── System info ────────────────────────────────────────
            lower == "neofetch" || lower == "fetch" -> neofetch(ctx)
            lower == "battery" || lower == "bat"    -> showBattery(ctx)
            lower == "wifi" || lower == "ifconfig"  -> showWifi(ctx)
            lower == "stats" || lower == "top"      -> viewModelScope.launch { showStats() }
            lower == "whoami" || lower == "id"      -> {
                out("krishna  ◈${userRepository.coinsState.value ?: 0} GC", TermColors.Cyan)
            }
            lower == "storage" || lower == "disk"   -> runShell("df -h")

            // ── Notifications / Alarms ─────────────────────────────
            lower.startsWith("notify ") -> sendNotification(ctx, raw.substringAfter("notify ").trim())
            lower.startsWith("alarm ")  -> setAlarm(ctx, args.getOrElse(1) { "" })
            lower.startsWith("timer ")  -> setTimer(ctx, args.getOrElse(1) { "25" }.toIntOrNull() ?: 25)

            lower.startsWith("kill ") -> {
                val pkg = args.getOrElse(1) { "" }
                val am  = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                try { am.killBackgroundProcesses(pkg); ok("Killed: $pkg") }
                catch (e: Exception) { err("Cannot kill: ${e.message}") }
            }

            // ── Real shell — Termux path prepended ────────────────
            else -> runShell(raw)
        }
    }

    // ── Real shell execution via Termux bash ──────────────────────
    private fun runShell(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use Termux bash directly so all Termux commands work natively
                val termuxBash = "/data/data/com.termux/files/usr/bin/bash"
                val termuxSh   = "/data/data/com.termux/files/usr/bin/sh"
                val shell = when {
                    java.io.File(termuxBash).exists() -> termuxBash
                    java.io.File(termuxSh).exists()   -> termuxSh
                    else                               -> "sh"
                }
                val path = "/data/data/com.termux/files/usr/bin" +
                    ":/data/data/com.termux/files/usr/bin/applets" +
                    ":/data/data/com.termux/files/usr/local/bin" +
                    ":/system/bin:/system/xbin"
                val pb = ProcessBuilder(shell, "-c", cmd)
                pb.environment()["PATH"]    = path
                pb.environment()["HOME"]    = "/data/data/com.termux/files/home"
                pb.environment()["PREFIX"]  = "/data/data/com.termux/files/usr"
                pb.environment()["TMPDIR"]  = "/data/data/com.termux/files/usr/tmp"
                pb.environment()["LANG"]    = "en_US.UTF-8"
                pb.environment()["TERM"]    = "xterm-256color"
                pb.environment()["SHELL"]   = shell
                pb.redirectErrorStream(true)

                val proc   = pb.start()
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null && count < 300) {
                    val l = line!!
                    withContext(Dispatchers.Main) {
                        out(l, when {
                            l.lowercase().startsWith("error") ||
                            l.lowercase().startsWith("fatal") -> TermColors.Red
                            l.lowercase().startsWith("warn")  -> TermColors.Yellow
                            l.startsWith("✓") || l.startsWith("OK") -> TermColors.Green
                            else -> TermColors.White
                        })
                    }
                    count++
                }
                if (count >= 300) withContext(Dispatchers.Main) { info("...truncated") }
                proc.waitFor()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    err("${e.message}")
                    info("Tip: install Termux for full shell support")
                }
            }
        }
    }

    // ── App listing ────────────────────────────────────────────────
    private fun listApps(ctx: Context) {
        val pm  = ctx.packageManager
        val all = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        appCache = all   // persist for number-launch

        add(TermLine.Blank)
        out("NUM   NAME                       PACKAGE", TermColors.Cyan)
        out("─────────────────────────────────────────────────────", TermColors.Gray)
        all.forEachIndexed { i, app ->
            val name = pm.getApplicationLabel(app).toString()
            val num  = "%03d".format(i + 1)
            // Alternate white/gray for readability
            val col  = if (i % 2 == 0) TermColors.White else TermColors.Gray
            out("$num   %-26s %s".format(name.take(26), app.packageName), col)
        }
        add(TermLine.Blank)
        out("  ${all.size} apps  ·  type number to launch  ·  open <name>", TermColors.Green)
        add(TermLine.Blank)
    }

    // ── Quest listing ──────────────────────────────────────────────
    private suspend fun listQuests(ctx: Context) {
        val all      = questRepository.getAllQuestsAsList()
        val todayStr = java.time.LocalDate.now().toString()
        val todayDay = nethical.questphone.core.core.utils.getCurrentDay()

        if (all.isEmpty()) { info("No quests. Run: quest add"); return }

        add(TermLine.Blank)
        out("ST   TITLE                    DAYS       GC    LOCK", TermColors.Cyan)
        out("──────────────────────────────────────────────────────", TermColors.Gray)
        all.sortedBy { it.title }.forEach { q ->
            val done   = q.last_completed_on == todayStr
            val active = q.selected_days.any { it == todayDay } &&
                (q.start_date.isEmpty() || q.start_date <= todayStr) &&
                (q.auto_destruct.isEmpty() || q.auto_destruct >= todayStr)
            val st  = when { done -> "[✓]"; active -> "[●]"; else -> "[ ]" }
            val col = when { done -> TermColors.GreenDim; active -> TermColors.White; else -> TermColors.Gray }
            val days = q.selected_days.joinToString("") { d ->
                when(d) {
                    DayOfWeek.MON -> "M"
                    DayOfWeek.TUE -> "T"
                    DayOfWeek.WED -> "W"
                    DayOfWeek.THU -> "TH"
                    DayOfWeek.FRI -> "F"
                    DayOfWeek.SAT -> "SA"
                    DayOfWeek.SUN -> "SU"
                }
            }
            out("%-4s %-24s %-10s %-5d %s".format(
                st, q.title.take(24), days.take(10),
                q.reward, if (q.isHardLock) "HARD" else ""
            ), col)
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
        out("  balance : ◈$coins GC", TermColors.Yellow)
        if (holdings.isNotEmpty()) {
            add(TermLine.Blank)
            out("SYMBOL   SHARES      AVG BUY", TermColors.Cyan)
            out("────────────────────────────────", TermColors.Gray)
            holdings.forEach { (sym, h) ->
                out("%-8s %-11.4f ◈%.2f".format(sym, h.quantity, h.avgBuyPrice))
            }
        } else info("no holdings")
        add(TermLine.Blank)
    }

    // ── App launcher ───────────────────────────────────────────────
    private fun openApp(name: String, ctx: Context) {
        val pm  = ctx.packageManager
        // Try exact name match
        val app = appCache.firstOrNull {
            pm.getApplicationLabel(it).toString().equals(name, ignoreCase = true)
        } ?: pm.getInstalledApplications(0).firstOrNull {
            pm.getApplicationLabel(it).toString().equals(name, ignoreCase = true)
        }
        if (app != null) {
            val intent = pm.getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) { ctx.startActivity(intent); ok("Launching $name...") }
            else err("No launcher for $name")
            return
        }
        // Aliases
        val aliases = mapOf(
            "yt" to "com.google.android.youtube",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "whatsapp" to "com.whatsapp",
            "wa" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "ig" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "tg" to "org.telegram.messenger",
            "maps" to "com.google.android.apps.maps",
            "settings" to "com.android.settings"
        )
        val pkg    = aliases[name.lowercase()]
        val intent = pkg?.let { pm.getLaunchIntentForPackage(it) }
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent != null) { ctx.startActivity(intent); ok("Launching $name...") }
        else { err("not found: $name"); info("try: ls /app") }
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
                draft.title = input; wizardStep = WizardStep.DAYS
                add(TermLine.Prompt("Days:", "M T W TH F SA SU  or  ALL"))
            }
            WizardStep.DAYS -> {
                val p = parseDays(input)
                if (p.isEmpty()) { err("Invalid. Use: M T W TH F SA SU or ALL"); return }
                draft.days = p; wizardStep = WizardStep.TIME_YN
                add(TermLine.Prompt("Time range? [y/n]:"))
            }
            WizardStep.TIME_YN -> when (input.lowercase()) {
                "y", "yes" -> { draft.hasTime = true; wizardStep = WizardStep.TIME_START
                    add(TermLine.Prompt("Start hour [0-23]:")) }
                "n", "no"  -> { draft.hasTime = false; wizardStep = WizardStep.REWARD
                    add(TermLine.Prompt("GC Reward:", "default 10")) }
                else       -> err("Enter y or n")
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
            time_range    = if (draft.hasTime) listOf(draft.startHour, draft.endHour) else listOf(0, 24),
            isHardLock    = draft.hardLock,
            auto_destruct = "9999-12-31"
        )
        questRepository.upsertQuest(q)
        add(TermLine.Blank)
        out("── quest created ─────────────────────", TermColors.Green)
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
        val q     = questRepository.getAllQuestsAsList()
            .find { it.title.equals(title, ignoreCase = true) }
        if (q == null) { err("not found: $title"); return }
        if (q.last_completed_on == today) { info("already done today"); return }
        questRepository.upsertQuest(q.copy(last_completed_on = today))
        userRepository.addCoins(q.reward, "Quest: ${q.title}")
        ok("${q.title}  +◈${q.reward} GC")
    }

    // ── System info ────────────────────────────────────────────────
    private fun neofetch(ctx: Context) {
        val pm    = ctx.packageManager
        val info  = pm.getPackageInfo(ctx.packageName, 0)
        val coins = userRepository.coinsState.value ?: 0
        val bm    = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val bat   = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val ram   = Runtime.getRuntime()
        val usedMb  = (ram.totalMemory() - ram.freeMemory()) / 1024 / 1024

        add(TermLine.Blank)
        out("  ██████╗ ██████╗  ", TermColors.Green)
        out("  ██╔══██╗██╔══██╗   krishna@questphone", TermColors.Green)
        out("  ██║  ██║██████╔╝   ─────────────────────────────────", TermColors.Green)
        out("  ██║  ██║██╔═══╝    OS     : Android ${Build.VERSION.RELEASE}", TermColors.White)
        out("  ██████╔╝██║        Device : ${Build.MANUFACTURER} ${Build.MODEL}", TermColors.White)
        out("  ╚═════╝ ╚═╝        App    : QuestPhone v${info.versionName}", TermColors.White)
        out("                     GC     : ◈$coins", TermColors.Yellow)
        out("                     Battery: $bat%", if (bat > 20) TermColors.Green else TermColors.Red)
        out("                     RAM    : ${usedMb}MB used", TermColors.White)
        out("                     API    : ${Build.VERSION.SDK_INT}", TermColors.White)
        out("                     ABI    : ${Build.SUPPORTED_ABIS.firstOrNull()}", TermColors.White)
        add(TermLine.Blank)
    }

    private fun showBattery(ctx: Context) {
        val bm  = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val mA  = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chrg= bm.isCharging
        val bar = "█".repeat(lvl / 10) + "░".repeat(10 - lvl / 10)
        val col = when { lvl > 50 -> TermColors.Green; lvl > 20 -> TermColors.Yellow; else -> TermColors.Red }
        out("[$bar] $lvl%  ${if (chrg) "⚡ charging" else "discharging"}  ${kotlin.math.abs(mA / 1000)}mA", col)
    }

    private fun showWifi(ctx: Context) {
        try {
            val wm   = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wm.connectionInfo
            val ip   = android.text.format.Formatter.formatIpAddress(info.ipAddress)
            out("SSID  : ${info.ssid}", TermColors.Cyan)
            out("IP    : $ip")
            out("RSSI  : ${info.rssi} dBm")
            out("Speed : ${info.linkSpeed} Mbps")
        } catch (e: Exception) { err("wifi info unavailable") }
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

    private fun sendNotification(ctx: Context, msg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel("terminal", "Terminal", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = androidx.core.app.NotificationCompat.Builder(ctx, "terminal")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("QuestPhone Terminal")
            .setContentText(msg)
            .setAutoCancel(true).build()
        nm.notify(System.currentTimeMillis().toInt(), n)
        ok("Notification sent: $msg")
    }

    private fun setAlarm(ctx: Context, time: String) {
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2) { err("Format: alarm HH:MM"); return }
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, parts[0])
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, parts[1])
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "QuestPhone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        ok("Alarm set for $time")
    }

    private fun setTimer(ctx: Context, mins: Int) {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, mins * 60)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "QuestPhone Timer")
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        ok("Timer: ${mins}min")
    }

    private fun parseDays(input: String): Set<DayOfWeek> {
        if (input.uppercase().trim() == "ALL") return DayOfWeek.entries.toSet()
        val map = mapOf(
            "M" to DayOfWeek.MON,  "T"  to DayOfWeek.TUE,
            "W" to DayOfWeek.WED,  "TH" to DayOfWeek.THU,
            "F" to DayOfWeek.FRI,  "SA" to DayOfWeek.SAT,
            "SU" to DayOfWeek.SUN
        )
        return input.uppercase().split(" ", ",").mapNotNull { map[it.trim()] }.toSet()
    }

    private fun showHelp() {
        add(TermLine.Blank)
        out("── shell ─────────────────────────────", TermColors.Cyan)
        info("Any shell cmd — uses Termux PATH automatically")
        info("pkg upgrade  apt update  pip install  git  curl  wget")
        add(TermLine.Blank)
        out("── navigation ────────────────────────", TermColors.Cyan)
        info("cd /quest  cd /app  cd /bank  cd /settings  cd ~")
        add(TermLine.Blank)
        out("── apps ──────────────────────────────", TermColors.Cyan)
        info("ls /app          list all apps with number")
        info("<number>         launch app by number from ls /app")
        info("open <name>      launch by name (yt wa ig tg...)")
        add(TermLine.Blank)
        out("── quests ────────────────────────────", TermColors.Cyan)
        info("quest add        add quest wizard")
        info("ls /quest        list quests")
        info("quest done <title>")
        info("quest delete <title>")
        add(TermLine.Blank)
        out("── system ────────────────────────────", TermColors.Cyan)
        info("neofetch  battery  wifi  stats  whoami  storage")
        info("notify <msg>  alarm HH:MM  timer <mins>")
        info("kill <pkg>  balance  debug  clear  exit")
        add(TermLine.Blank)
        out("── extra keys ────────────────────────", TermColors.Cyan)
        info("≡ = open session drawer")
        info("↑↓ = command history")
        info("TAB = autocomplete")
        add(TermLine.Blank)
    }
}
