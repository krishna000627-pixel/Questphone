package neth.iecal.questphone.app.screens.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.ContextCompat
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.mylife.KundaliData
import neth.iecal.questphone.app.screens.mylife.MyLifeStorage
import neth.iecal.questphone.app.screens.people.PeopleDatabase
import nethical.questphone.data.UserInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ── Response model ────────────────────────────────────────────────────────────

data class JarvisResponse(
    val text: String,
    val action: JarvisAction? = null
)

sealed class JarvisAction {
    data class LaunchApp(val packageName: String) : JarvisAction()
    data class Navigate(val route: String) : JarvisAction()
    data class OpenUrl(val url: String) : JarvisAction()
    data class CopyToClipboard(val text: String) : JarvisAction()
    data class ShareText(val text: String) : JarvisAction()
}

// ── Engine ────────────────────────────────────────────────────────────────────

class JarvisEngine(
    private val ctx: Context,
    private val userInfo: UserInfo,
    private val navCallback: (String) -> Unit,
    private val launchAppCallback: (String) -> Unit
) {

    // ── Intent matching ───────────────────────────────────────────────────────

    fun process(input: String): JarvisResponse {
        val raw = input.trim().lowercase()
        val tokens = raw.split(" ", ",", ".", "?", "!").filter { it.isNotBlank() }

        // 1. Check custom commands first
        val custom = matchCustomCommand(raw, tokens)
        if (custom != null) return custom

        // 2. Built-in command matching
        return matchBuiltIn(raw, tokens)
    }

    private fun matchCustomCommand(raw: String, tokens: List<String>): JarvisResponse? {
        val commands = JarvisStorage.loadCommands(ctx).filter { it.isEnabled }
        for (cmd in commands) {
            val matched = cmd.triggers.any { trigger ->
                val trigLower = trigger.lowercase()
                if (JarvisStorage.loadJarvisPrefs(ctx).fuzzyMatch) {
                    raw.contains(trigLower) || fuzzyScore(raw, trigLower) > 0.75
                } else {
                    raw == trigLower || raw.contains(trigLower)
                }
            }
            if (matched) {
                JarvisStorage.incrementUsage(ctx, cmd.id)
                val action = when (cmd.action) {
                    CommandAction.LAUNCH_APP -> JarvisAction.LaunchApp(cmd.actionParam)
                    CommandAction.NAVIGATE -> JarvisAction.Navigate(cmd.actionParam)
                    CommandAction.OPEN_URL -> JarvisAction.OpenUrl(cmd.actionParam)
                    CommandAction.CLIPBOARD -> JarvisAction.CopyToClipboard(cmd.actionParam)
                    CommandAction.SHARE -> JarvisAction.ShareText(cmd.actionParam)
                    CommandAction.RANDOM -> null
                    else -> null
                }
                val response = if (cmd.action == CommandAction.RANDOM) {
                    val opts = cmd.actionParam.split("|")
                    opts.random()
                } else cmd.response.ifBlank { "Done." }
                return JarvisResponse(response, action)
            }
        }
        return null
    }

    private fun matchBuiltIn(raw: String, tokens: List<String>): JarvisResponse {
        val personality = JarvisStorage.loadJarvisPrefs(ctx).personality

        // ── Time & Date ───────────────────────────────────────────────────────
        if (raw.containsAny("what time", "current time", "time now", "time is it")) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return respond("It's $time.", personality)
        }
        if (raw.containsAny("what date", "today's date", "what day", "date today")) {
            val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
            return respond(date, personality)
        }
        if (raw.containsAny("what year", "current year")) {
            return respond("It's ${Calendar.getInstance().get(Calendar.YEAR)}.", personality)
        }
        if (raw.containsAny("week number", "which week")) {
            val week = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
            return respond("Week $week of the year.", personality)
        }
        if (raw.containsAny("midnight", "how long until reset", "time until reset")) {
            val cal = Calendar.getInstance()
            val mins = (23 - cal.get(Calendar.HOUR_OF_DAY)) * 60 + (59 - cal.get(Calendar.MINUTE))
            return respond("${mins / 60}h ${mins % 60}m until midnight.", personality)
        }

        // ── Streak & XP ───────────────────────────────────────────────────────
        if (raw.containsAny("my streak", "current streak", "streak count", "streak is")) {
            return respond("Your streak is ${userInfo.streak.currentStreak} days. Longest: ${userInfo.streak.longestStreak} days.", personality)
        }
        if (raw.containsAny("longest streak", "best streak", "record streak")) {
            return respond("Your longest streak was ${userInfo.streak.longestStreak} days.", personality)
        }
        if (raw.containsAny("my level", "current level", "what level")) {
            return respond("You're at Level ${userInfo.level}.", personality)
        }
        if (raw.containsAny("my xp", "how much xp", "experience points")) {
            val needed = nethical.questphone.data.xpToLevelUp(userInfo.level)
            return respond("XP: ${userInfo.xp} / $needed to Level ${userInfo.level + 1}.", personality)
        }

        // ── Economy ───────────────────────────────────────────────────────────
        if (raw.containsAny("how many coins", "my coins", "coin balance", "coins do i have")) {
            return respond("You have ${userInfo.coins} coins.", personality)
        }
        if (raw.containsAny("how many diamonds", "my diamonds", "diamond balance")) {
            return respond("You have ${userInfo.diamonds} diamonds.", personality)
        }

        // ── Stats ─────────────────────────────────────────────────────────────
        if (raw.containsAny("my stats", "show stats", "all stats", "stat points")) {
            val s = userInfo.statPoints
            return respond(
                "STR: ${s.value1}  |  INT: ${s.value2}  |  Focus: ${s.value3}  |  Discipline: ${s.value4}",
                personality
            )
        }
        if (raw.containsAny("strength", "str stat")) {
            return respond("Strength: ${userInfo.statPoints.value1} pts", personality)
        }
        if (raw.containsAny("intelligence", "int stat")) {
            return respond("Intelligence: ${userInfo.statPoints.value2} pts", personality)
        }
        if (raw.containsAny("focus stat", "my focus")) {
            return respond("Focus: ${userInfo.statPoints.value3} pts", personality)
        }
        if (raw.containsAny("discipline", "discipline stat")) {
            return respond("Discipline: ${userInfo.statPoints.value4} pts", personality)
        }
        if (raw.containsAny("highest stat", "best stat", "strongest stat")) {
            val s = userInfo.statPoints
            val names = listOf("Strength" to s.value1, "Intelligence" to s.value2, "Focus" to s.value3, "Discipline" to s.value4)
            val best = names.maxByOrNull { it.second }!!
            return respond("Your highest stat is ${best.first} at ${best.second} pts.", personality)
        }
        if (raw.containsAny("weakest stat", "lowest stat", "needs work")) {
            val s = userInfo.statPoints
            val names = listOf("Strength" to s.value1, "Intelligence" to s.value2, "Focus" to s.value3, "Discipline" to s.value4)
            val worst = names.minByOrNull { it.second }!!
            return respond("${worst.first} needs the most work — only ${worst.second} pts.", personality)
        }
        if (raw.containsAny("what should i focus on", "what to improve", "which stat")) {
            val s = userInfo.statPoints
            val names = listOf("Strength" to s.value1, "Intelligence" to s.value2, "Focus" to s.value3, "Discipline" to s.value4)
            val worst = names.minByOrNull { it.second }!!
            return respond("Focus on ${worst.first} — it's your weakest at ${worst.second} pts. Add quests that reward it.", personality)
        }

        // ── My Life ───────────────────────────────────────────────────────────
        if (raw.containsAny("my dasha", "current dasha", "mahadasha", "which dasha")) {
            val cur = KundaliData.dashas.firstOrNull { it.isCurrent }
            return if (cur != null)
                respond("You're in ${cur.planet} Mahadasha (${cur.from} – ${cur.to}). ${cur.note.take(200)}", personality)
            else respond("Current Mahadasha not found in chart data.", personality)
        }
        if (raw.containsAny("when dasha end", "dasha end", "dasha ends")) {
            val cur = KundaliData.dashas.firstOrNull { it.isCurrent }
            return if (cur != null) respond("Your ${cur.planet} Mahadasha ends in ${cur.to}.", personality)
            else respond("Dasha end date not found.", personality)
        }
        if (raw.containsAny("my lagna", "ascendant", "lagna is")) {
            return respond("Your Lagna is ${KundaliData.lagna}.", personality)
        }
        if (raw.containsAny("my rashi", "moon sign", "rashi is")) {
            return respond("Your Rashi (Moon Sign) is ${KundaliData.rashiMoon}.", personality)
        }
        if (raw.containsAny("my nakshatra", "birth star", "janma nakshatra")) {
            return respond("Your Janma Nakshatra is ${KundaliData.birthStar}.", personality)
        }
        if (raw.containsAny("my dharma", "life purpose", "my purpose")) {
            val ml = MyLifeStorage.load(ctx)
            return if (ml.profile.dharma.isNotBlank())
                respond("Your dharma: \"${ml.profile.dharma}\"", personality)
            else respond("You haven't set your life purpose yet. Go to My Life → Settings to add it.", personality)
        }
        if (raw.containsAny("5 year", "five year", "my vision", "long term goal")) {
            val ml = MyLifeStorage.load(ctx)
            return if (ml.profile.fiveYearGoal.isNotBlank())
                respond("5-Year Vision: \"${ml.profile.fiveYearGoal}\"", personality)
            else respond("No 5-year vision set yet.", personality)
        }
        if (raw.containsAny("my struggles", "current struggles", "how many struggles")) {
            val ml = MyLifeStorage.load(ctx)
            val active = ml.struggles.filter { !it.isOvercome }
            return if (active.isEmpty()) respond("No active struggles recorded.", personality)
            else respond("${active.size} active struggle${if (active.size != 1) "s" else ""}:\n${active.take(5).mapIndexed { i, s -> "${i+1}. ${s.title}" }.joinToString("\n")}", personality)
        }
        if (raw.containsAny("my mantras", "mantra list", "list mantras")) {
            val ml = MyLifeStorage.load(ctx)
            return if (ml.mantras.isEmpty()) respond("No mantras saved yet.", personality)
            else respond("Your mantras:\n${ml.mantras.take(5).mapIndexed { i, m -> "${i+1}. ${m.title}" }.joinToString("\n")}", personality)
        }
        if (raw.containsAny("read mantra", "say mantra", "first mantra")) {
            val ml = MyLifeStorage.load(ctx)
            val m = ml.mantras.firstOrNull()
            return if (m != null) respond("${m.title}\n${m.text}", personality)
            else respond("No mantras saved.", personality)
        }
        if (raw.containsAny("today journal", "journal today", "today's entry")) {
            val ml = MyLifeStorage.load(ctx)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val entry = ml.journal.firstOrNull { it.date == today }
            return if (entry != null) respond("Today's journal:\n${entry.content.take(300)}", personality)
            else respond("No journal entry for today yet.", personality)
        }
        if (raw.containsAny("last journal", "recent journal", "latest journal")) {
            val ml = MyLifeStorage.load(ctx)
            val entry = ml.journal.firstOrNull()
            return if (entry != null) respond("${entry.date}:\n${entry.content.take(300)}", personality)
            else respond("No journal entries yet.", personality)
        }
        if (raw.containsAny("my gotra", "gotra")) {
            return respond("Your gotra is Vasistha (वसिष्ठ) — descending from Maharishi Vasistha, one of the Saptarishis.", personality)
        }
        if (raw.containsAny("kuldevi", "family goddess", "kul devi")) {
            return respond("Your Kuldevi is Sharda Mata (शारदा माता) — Goddess of knowledge and creative speech.", personality)
        }
        if (raw.containsAny("life path", "numerology", "my number")) {
            return respond("Your Life Path Number is 7 — The Seeker. Deep thinker, spiritual seeker, philosopher.", personality)
        }
        if (raw.containsAny("motivate me", "motivation", "inspire me")) {
            val ml = MyLifeStorage.load(ctx)
            val dharma = ml.profile.dharma.takeIf { it.isNotBlank() } ?: "your purpose"
            val streak = userInfo.streak.currentStreak
            return respond("Your dharma: \"$dharma\"\nStreak: $streak days. You are in Vasistha gotra — the lineage of cosmic clarity. Keep moving.", personality)
        }
        if (raw.containsAny("my lineage", "vasistha", "my ancestors")) {
            return respond("Vasistha Gotra — your lineage descends from Maharishi Vasistha, the cosmic guru of Yoga Vasistha. Sharda Mata is your Kuldevi.", personality)
        }
        if (raw.containsAny("am i in good dasha", "is my dasha good", "dasha quality")) {
            val cur = KundaliData.dashas.firstOrNull { it.isCurrent }
            return if (cur != null) respond("You're in ${cur.planet} Mahadasha. ${cur.note.take(150)}", personality)
            else respond("Dasha data not found.", personality)
        }

        // ── People DB ─────────────────────────────────────────────────────────
        if (raw.containsAny("how many people", "people count", "allies count", "shadow army size")) {
            val count = PeopleDatabase.load(ctx).size
            return respond("Your shadow army has $count ${if (count == 1) "person" else "people"}.", personality)
        }
        if (raw.containsAny("my best friends", "best friends list", "who are my friends")) {
            val people = PeopleDatabase.load(ctx).filter { it.relation.contains("friend", ignoreCase = true) }
            return if (people.isEmpty()) respond("No friends tagged in your People DB yet.", personality)
            else respond("Friends: ${people.take(5).joinToString(", ") { it.name }}", personality)
        }
        if (raw.containsAny("my family", "family members", "family list")) {
            val people = PeopleDatabase.load(ctx).filter { it.relation.contains("family", ignoreCase = true) || it.relation.contains("father", ignoreCase = true) || it.relation.contains("mother", ignoreCase = true) }
            return if (people.isEmpty()) respond("No family members in People DB yet.", personality)
            else respond("Family: ${people.take(5).joinToString(", ") { "${it.name} (${it.relation})" }}", personality)
        }
        if (raw.startsWith("find ") || raw.startsWith("who is ") || raw.startsWith("search ")) {
            val query = raw.removePrefix("find ").removePrefix("who is ").removePrefix("search ").trim()
            val person = PeopleDatabase.load(ctx).firstOrNull { it.name.contains(query, ignoreCase = true) }
            return if (person != null)
                respond("${person.emoji} ${person.name} — ${person.relation}${if (person.notes.isNotBlank()) "\n${person.notes.take(100)}" else ""}", personality)
            else respond("No one named \"$query\" found in your People DB.", personality)
        }
        if (raw.containsAny("shadow army", "my shadows", "list people", "list allies")) {
            val people = PeopleDatabase.load(ctx)
            return if (people.isEmpty()) respond("Your shadow army is empty.", personality)
            else respond("Shadow Army (${people.size}):\n${people.take(8).joinToString("\n") { "${it.emoji} ${it.name} — ${it.relation}" }}", personality)
        }

        // ── Device info ───────────────────────────────────────────────────────
        if (raw.containsAny("battery", "battery level", "how much battery")) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return respond("Battery: $level%", personality)
        }
        if (raw.containsAny("storage", "free space", "available storage")) {
            val stat = StatFs(android.os.Environment.getExternalStorageDirectory().path)
            val free = stat.availableBytes / (1024 * 1024 * 1024)
            val total = stat.totalBytes / (1024 * 1024 * 1024)
            return respond("Storage: ${free}GB free / ${total}GB total", personality)
        }
        if (raw.containsAny("android version", "os version", "device info")) {
            return respond("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) — ${Build.MANUFACTURER} ${Build.MODEL}", personality)
        }
        if (raw.containsAny("device name", "phone model", "what phone")) {
            return respond("${Build.MANUFACTURER} ${Build.MODEL}", personality)
        }

        // ── Navigation ────────────────────────────────────────────────────────
        if (raw.containsAny("go home", "home screen", "take me home", "back home")) {
            navCallback(RootRoute.HomeScreen.route)
            return respond("Going home.", personality)
        }
        if (raw.containsAny("go to store", "open store", "item shop", "show store")) {
            navCallback(RootRoute.Customize.route)
            return respond("Opening Store.", personality)
        }
        if (raw.containsAny("go to stats", "open stats", "show stats hub", "stats hub")) {
            navCallback(RootRoute.StatsHub.route)
            return respond("Opening Stats Hub.", personality)
        }
        if (raw.containsAny("go to people", "open allies", "open people", "allies")) {
            navCallback(RootRoute.PeopleDatabase.route)
            return respond("Opening People DB.", personality)
        }
        if (raw.containsAny("my life", "open my life", "go to my life")) {
            navCallback(RootRoute.MyLife.route)
            return respond("Opening My Life.", personality)
        }
        if (raw.containsAny("open settings", "launcher settings", "go to settings")) {
            navCallback(RootRoute.LauncherSettings.route)
            return respond("Opening Settings.", personality)
        }
        if (raw.containsAny("open focus timer", "focus timer", "pomodoro", "start pomodoro")) {
            navCallback(RootRoute.FocusTimer.route)
            return respond("Opening Focus Timer.", personality)
        }
        if (raw.containsAny("open kai", "talk to kai", "open ai", "kai chat")) {
            navCallback(RootRoute.GemmaChat.route)
            return respond("Opening Kai.", personality)
        }
        if (raw.containsAny("rpg settings", "open rpg", "go to rpg")) {
            navCallback(RootRoute.RpgSettings.route)
            return respond("Opening RPG Settings.", personality)
        }

        // ── App launching ─────────────────────────────────────────────────────
        if (raw.startsWith("open ") || raw.startsWith("launch ") || raw.startsWith("start ")) {
            val appName = raw.removePrefix("open ").removePrefix("launch ").removePrefix("start ").trim()
            val pkg = findAppPackage(appName)
            if (pkg != null) {
                launchAppCallback(pkg)
                return respond("Opening ${appName.capitalizeWords()}.", personality)
            }
            return respond("Couldn't find \"$appName\" on your device.", personality)
        }

        // ── Productivity ──────────────────────────────────────────────────────
        if (raw.containsAny("how productive", "productivity today", "today score")) {
            return respond("Check your stats and quest completion in the Stats Hub.", personality,
                JarvisAction.Navigate(RootRoute.StatsHub.route))
        }
        if (raw.containsAny("start study session", "study session", "start studying")) {
            navCallback(RootRoute.FocusTimer.route)
            return respond("Study session initiated. Focus timer is opening.", personality)
        }

        // ── Solo Leveling special ─────────────────────────────────────────────
        if (raw.containsAny("gate warning", "dungeon", "arise", "system message")) {
            val warnings = listOf(
                "⚠️ SYSTEM ALERT: A Gate has appeared nearby. Danger level: B-Rank. Proceed with caution, Hunter.",
                "⚠️ The System has detected unresolved quests. Daily missions must be cleared before midnight.",
                "⚠️ ARISE. Your shadows grow restless. Complete today's missions to strengthen the army.",
                "⚠️ WARNING: Consecutive missed days detected. Lockdown Escalation may activate.",
                "⚠️ SYSTEM: A new dungeon has broken. Only the worthy may enter. Are you ready, Player?"
            )
            return respond(warnings.random(), personality)
        }
        if (raw.containsAny("boss status", "weekly boss", "boss hp")) {
            navCallback(RootRoute.PlayHub.route)
            return respond("Checking Weekly Boss status.", personality)
        }
        if (raw.containsAny("rival status", "shadow rival", "rival score")) {
            navCallback(RootRoute.PlayHub.route)
            return respond("Checking Shadow Rival.", personality)
        }
        if (raw.containsAny("level up", "close to level up", "how close level")) {
            val needed = nethical.questphone.data.xpToLevelUp(userInfo.level)
            val pct = (userInfo.xp * 100 / needed.coerceAtLeast(1))
            return respond("Level ${userInfo.level}: $pct% complete. ${needed - userInfo.xp} XP to Level ${userInfo.level + 1}.", personality)
        }
        if (raw.containsAny("roll dice", "dice", "random number")) {
            return respond("🎲 You rolled a ${(1..6).random()}.", personality)
        }
        if (raw.containsAny("flip coin", "coin flip", "heads or tails")) {
            return respond(if ((0..1).random() == 0) "🪙 Heads!" else "🪙 Tails!", personality)
        }
        if (raw.containsAny("random quest", "quest idea", "suggest quest")) {
            val ideas = listOf(
                "Read for 30 minutes without phone",
                "Do 50 push-ups today",
                "Write in your journal",
                "Meditate for 10 minutes",
                "Study one chapter completely",
                "No social media for 3 hours",
                "Drink 2 litres of water",
                "Solve 10 math problems",
                "Write your dharma 10 times",
                "Wake up before sunrise tomorrow"
            )
            return respond("Quest suggestion: ${ideas.random()}", personality)
        }
        if (raw.containsAny("daily affirmation", "affirmation", "positive message")) {
            val affirmations = listOf(
                "You carry the Vasistha lineage. Clarity is your birthright.",
                "Every quest completed is a step toward your dharma.",
                "The System chose you. There are no weak hunters, only untrained ones.",
                "Your Moon in Purva Phalguni — beauty and creativity flow through you.",
                "Saturn rewards patience. Your time is coming.",
                "You are a Level ${userInfo.level} hunter. Keep ascending."
            )
            return respond(affirmations.random(), personality)
        }

        // ── Math ──────────────────────────────────────────────────────────────
        if (raw.containsAny("calculate", "what is ", "compute")) {
            val expr = raw.removePrefix("calculate").removePrefix("what is").removePrefix("compute").trim()
            return try {
                val result = evalSimpleMath(expr)
                respond("= $result", personality)
            } catch (_: Exception) {
                respond("Couldn't calculate that. Try something like 'calculate 25 * 4'.", personality)
            }
        }

        // ── Help ──────────────────────────────────────────────────────────────
        if (raw.containsAny("help", "what can you do", "commands list", "show commands")) {
            return respond(
                "I can help with:\n" +
                "• Time/date — \"what time is it\"\n" +
                "• Stats — \"my stats\", \"my streak\", \"my coins\"\n" +
                "• My Life — \"my dasha\", \"my dharma\", \"my struggles\"\n" +
                "• People — \"shadow army\", \"find [name]\"\n" +
                "• Navigate — \"open stats\", \"go to store\"\n" +
                "• Apps — \"open youtube\", \"launch instagram\"\n" +
                "• Device — \"battery\", \"storage\"\n" +
                "• Fun — \"roll dice\", \"flip coin\", \"daily affirmation\"\n" +
                "• Math — \"calculate 12 * 8\"\n" +
                "• Custom commands — set up your own in the Commands tab",
                personality
            )
        }

        // ── Fallback ──────────────────────────────────────────────────────────
        return JarvisResponse(
            when (personality) {
                "system" -> "⚠️ SYSTEM: Command not recognized. Specify a valid instruction, Player."
                "sensei" -> "Unknown command. Be precise."
                "stoic" -> "\"The obstacle is the way.\" — I didn't understand that. Try 'help' for commands."
                else -> "I didn't understand that. Type 'help' to see what I can do, or set up a custom command."
            }
        )
    }

    // ── Personality wrapper ───────────────────────────────────────────────────

    private fun respond(text: String, personality: String, action: JarvisAction? = null): JarvisResponse {
        val wrapped = when (personality) {
            "system" -> "【SYSTEM】$text"
            "sensei" -> text
            "stoic" -> text
            else -> text
        }
        return JarvisResponse(wrapped, action)
    }

    // ── App finder ────────────────────────────────────────────────────────────

    private fun findAppPackage(query: String): String? {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(0)
        val q = query.lowercase().trim()
        // Exact name match first
        apps.firstOrNull { pm.getApplicationLabel(it).toString().lowercase() == q }?.let { return it.packageName }
        // Contains match
        apps.firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(q) }?.let { return it.packageName }
        // Common aliases
        val aliases = mapOf(
            "yt" to "com.google.android.youtube",
            "youtube" to "com.google.android.youtube",
            "insta" to "com.instagram.android",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera",
            "gallery" to "com.android.gallery3d",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "gmail" to "com.google.android.gm",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.android.deskclock",
            "calculator" to "com.android.calculator2",
            "contacts" to "com.android.contacts",
            "phone" to "com.android.dialer",
            "settings" to "com.android.settings",
            "snapchat" to "com.snapchat.android",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient"
        )
        return aliases[q]
    }

    // ── Simple math evaluator ─────────────────────────────────────────────────

    private fun evalSimpleMath(expr: String): String {
        val e = expr.trim()
        val addIdx = e.lastIndexOf('+')
        val subIdx = e.lastIndexOf('-', e.length - 1).takeIf { it > 0 }
        val mulIdx = e.lastIndexOf('*')
        val divIdx = e.lastIndexOf('/')
        val modIdx = e.lastIndexOf('%')

        return when {
            addIdx > 0 -> {
                val a = e.substring(0, addIdx).trim().toDouble()
                val b = e.substring(addIdx + 1).trim().toDouble()
                formatNum(a + b)
            }
            subIdx != null && subIdx > 0 -> {
                val a = e.substring(0, subIdx).trim().toDouble()
                val b = e.substring(subIdx + 1).trim().toDouble()
                formatNum(a - b)
            }
            mulIdx > 0 -> {
                val a = e.substring(0, mulIdx).trim().toDouble()
                val b = e.substring(mulIdx + 1).trim().toDouble()
                formatNum(a * b)
            }
            divIdx > 0 -> {
                val a = e.substring(0, divIdx).trim().toDouble()
                val b = e.substring(divIdx + 1).trim().toDouble()
                if (b == 0.0) "Cannot divide by zero" else formatNum(a / b)
            }
            modIdx > 0 -> {
                val a = e.substring(0, modIdx).trim().toDouble()
                val b = e.substring(modIdx + 1).trim().toDouble()
                formatNum(a % b)
            }
            else -> e.trim().toDouble().toString()
        }
    }

    private fun formatNum(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.4f".format(d).trimEnd('0').trimEnd('.')

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun String.containsAny(vararg phrases: String): Boolean =
        phrases.any { this.contains(it) }

    private fun fuzzyScore(a: String, b: String): Double {
        if (a == b) return 1.0
        val longer = if (a.length > b.length) a else b
        val shorter = if (a.length > b.length) b else a
        if (longer.isEmpty()) return 1.0
        val distance = editDistance(longer, shorter)
        return (longer.length - distance).toDouble() / longer.length
    }

    private fun editDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) for (j in 1..s2.length) {
            dp[i][j] = if (s1[i-1] == s2[j-1]) dp[i-1][j-1]
            else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
        }
        return dp[s1.length][s2.length]
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
