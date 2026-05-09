package neth.iecal.questphone.app.screens.etc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.data.BaseIntegrationId
import nethical.questphone.data.DayOfWeek
import nethical.questphone.core.core.utils.getCurrentDate
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// -- Data ----------------------------------------------------------------------

data class QuestImportPreview(
    val title: String,
    val reward: Int,
    val days: Set<DayOfWeek>,
    val timeStart: Int,
    val timeEnd: Int,
    val instructions: String,
    val isHardLock: Boolean,
    val stat1: Int = 0,
    val stat2: Int = 0,
    val stat3: Int = 0,
    val stat4: Int = 0
)

// -- ViewModel -----------------------------------------------------------------

@HiltViewModel
class JsonQuestConverterViewModel @Inject constructor(
    private val questRepository: QuestRepository
) : ViewModel() {

    private val _previews = MutableStateFlow<List<QuestImportPreview>>(emptyList())
    val previews = _previews.asStateFlow()

    private val _parseError = MutableStateFlow<String?>(null)
    val parseError = _parseError.asStateFlow()

    private val _importCount = MutableStateFlow(0)
    val importCount = _importCount.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    /** Parse pasted JSON. Max 50 quests per import. */
    fun parseJson(raw: String) {
        _parseError.value = null
        _previews.value = emptyList()
        _importCount.value = 0

        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            _parseError.value = "Nothing to parse — paste some JSON first."
            return
        }

        try {
            val arr = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONArray().apply { put(JSONObject(trimmed)) }
                else -> throw Exception("JSON must start with [ (array) or { (single object)")
            }

            val limit = minOf(arr.length(), 50)
            val parsed = mutableListOf<QuestImportPreview>()

            for (i in 0 until limit) {
                val obj = arr.getJSONObject(i)

                // Days — flexible: full name, 3-letter, or number (1=Mon … 7=Sun)
                val days = mutableSetOf<DayOfWeek>()
                val daysArr = obj.optJSONArray("days")
                if (daysArr != null) {
                    for (d in 0 until daysArr.length()) {
                        val raw = daysArr.getString(d).trim().uppercase()
                        DayOfWeek.entries.firstOrNull {
                            it.name == raw || it.name.startsWith(raw.take(3))
                        }?.let { days.add(it) }
                    }
                }
                if (days.isEmpty()) days.addAll(DayOfWeek.entries) // default: every day

                parsed.add(
                    QuestImportPreview(
                        title = obj.optString("title", "Quest ${i + 1}").ifBlank { "Quest ${i + 1}" },
                        reward = obj.optInt("reward", 5).coerceIn(1, 999),
                        days = days,
                        timeStart = obj.optInt("timeStart", 0).coerceIn(0, 23),
                        timeEnd = obj.optInt("timeEnd", 24).coerceIn(1, 24),
                        instructions = obj.optString("instructions", ""),
                        isHardLock = obj.optBoolean("isHardLock", false),
                        stat1 = obj.optInt("stat1", 0).coerceIn(0, 99),
                        stat2 = obj.optInt("stat2", 0).coerceIn(0, 99),
                        stat3 = obj.optInt("stat3", 0).coerceIn(0, 99),
                        stat4 = obj.optInt("stat4", 0).coerceIn(0, 99)
                    )
                )
            }

            _previews.value = parsed

            val warning = buildString {
                if (arr.length() > 50) append("Only first 50 of ${arr.length()} quests shown. ")
                if (parsed.any { it.days == DayOfWeek.entries.toSet() && arr.getJSONObject(parsed.indexOf(it)).optJSONArray("days") == null })
                    append("Quests without 'days' default to all days.")
            }
            if (warning.isNotBlank()) _parseError.value = "⚠️ $warning"

        } catch (e: Exception) {
            _parseError.value = "❌ Parse error: ${e.message}"
        }
    }

    fun importAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            var count = 0
            _previews.value.forEach { p ->
                questRepository.upsertQuest(
                    CommonQuestInfo(
                        title = p.title,
                        reward = p.reward,
                        integration_id = BaseIntegrationId.SWIFT_MARK,
                        selected_days = p.days,
                        time_range = listOf(p.timeStart, p.timeEnd),
                        instructions = p.instructions,
                        isHardLock = p.isHardLock,
                        statReward1 = p.stat1,
                        statReward2 = p.stat2,
                        statReward3 = p.stat3,
                        statReward4 = p.stat4,
                        created_on = getCurrentDate()
                    )
                )
                count++
            }
            _importCount.value = count
            _previews.value = emptyList()
            _isImporting.value = false
        }
    }

    fun clearResults() {
        _previews.value = emptyList()
        _parseError.value = null
        _importCount.value = 0
    }
}

// -- Constants -----------------------------------------------------------------

private val EXAMPLE_MINIMAL = """
[
  {"title": "Study Physics", "reward": 10}
]
""".trim()

private val EXAMPLE_FULL = """
[
  {
    "title": "Study Physics",
    "reward": 10,
    "days": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
    "timeStart": 9,
    "timeEnd": 17,
    "instructions": "Complete 2 chapters. Focus on electrostatics.",
    "isHardLock": false,
    "stat1": 0,
    "stat2": 3,
    "stat3": 2,
    "stat4": 1
  },
  {
    "title": "Morning Exercise",
    "reward": 5,
    "days": ["MONDAY","WEDNESDAY","FRIDAY"],
    "timeStart": 6,
    "timeEnd": 8,
    "instructions": "30 min workout. No excuses.",
    "isHardLock": true,
    "stat1": 3,
    "stat2": 0,
    "stat3": 1,
    "stat4": 2
  },
  {
    "title": "Read 20 Pages",
    "reward": 8,
    "days": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
    "timeStart": 21,
    "timeEnd": 23,
    "instructions": "Any book counts. Track pages.",
    "isHardLock": false,
    "stat1": 0,
    "stat2": 2,
    "stat3": 1,
    "stat4": 1
  }
]
""".trim()

private val DAY_NAMES = listOf(
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
)

// -- UI ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonQuestConverterScreen(
    navController: NavController,
    vm: JsonQuestConverterViewModel = hiltViewModel()
) {
    val previews by vm.previews.collectAsState()
    val parseError by vm.parseError.collectAsState()
    val importCount by vm.importCount.collectAsState()
    val isImporting by vm.isImporting.collectAsState()

    var jsonInput by remember { mutableStateOf("") }
    var tutorialExpanded by remember { mutableStateOf(false) }
    var fieldExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(importCount) {
        if (importCount > 0) jsonInput = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🗂️ JSON Quest Converter", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {

            // -- Import success banner --------------------------------------
            if (importCount > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A1A0A))
                            .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✅", fontSize = 22.sp)
                        Column {
                            Text(
                                "Imported $importCount quest${if (importCount == 1) "" else "s"}!",
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Find them in Quest Analytics → All Quests.",
                                color = Color(0xFF4CAF50), fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.clearResults() }) {
                            Text("Dismiss", color = Color(0xFF4CAF50), fontSize = 11.sp)
                        }
                    }
                }
            }

            // -- What this does --------------------------------------------
            item {
                Text(
                    "Paste a JSON array below to bulk-import up to 50 quests at once. Each quest becomes a SwiftMark quest you can tick off daily.",
                    fontSize = 13.sp, color = Color(0xFF888888), lineHeight = 19.sp
                )
            }

            // -- TUTORIAL --------------------------------------------------
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF080E08))
                        .border(1.dp, Color(0xFF1E3A1E), RoundedCornerShape(14.dp))
                ) {
                    // Collapsible header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tutorialExpanded = !tutorialExpanded }
                            .padding(14.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(
                            "📖 Tutorial — How to use this",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF66BB6A)
                        )
                        Icon(
                            imageVector = if (tutorialExpanded) Icons.Default.KeyboardArrowUp
                                          else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color(0xFF66BB6A)
                        )
                    }

                    if (tutorialExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Step 1
                            TutorialStep(
                                number = "1",
                                title = "Write your quests as JSON",
                                body = "Create a JSON array ( [ … ] ) where each item is one quest. The only required field is \"title\" — everything else has a sensible default."
                            )

                            // Minimal example
                            CodeBlock(EXAMPLE_MINIMAL, label = "Minimal (just title)")

                            // Step 2
                            TutorialStep(
                                number = "2",
                                title = "Add optional fields for more control",
                                body = "You can customise reward coins, which days the quest appears, the time window, instructions, and whether it hard-locks your phone."
                            )

                            CodeBlock(EXAMPLE_FULL, label = "Full example (3 quests)")

                            // Step 3
                            TutorialStep(
                                number = "3",
                                title = "Paste → Preview → Import",
                                body = "Paste your JSON in the box below, tap Preview to check, then tap Import All. Quests appear instantly in Quest Analytics."
                            )

                            // Tips box
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0D1A0D))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("💡 Tips", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                listOf(
                                    "Use Kai (AI Chat) or ChatGPT to generate JSON: just describe your goals",
                                    "Prompt: \"Make QuestPhone JSON with: title, reward, days, timeStart, timeEnd, instructions, isHardLock, stat1, stat2, stat3, stat4\"",
                                    "stat1=Strength, stat2=Intelligence, stat3=Focus, stat4=Discipline",
                                    "Study quests: give stat2+stat3. Workout quests: give stat1+stat4. All zeros is fine too",
                                    "Missing 'days' means quest shows every day. Missing time means all day (0-24)",
                                    "isHardLock: true blocks phone until quest done each day",
                                    "Max 50 quests per import"
                                ).forEach { tip ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("•", color = Color(0xFF444444), fontSize = 12.sp)
                                        Text(tip, fontSize = 11.sp, color = Color(0xFF777777), lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -- FIELD REFERENCE -------------------------------------------
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A0A14))
                        .border(1.dp, Color(0xFF1E1E3A), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fieldExpanded = !fieldExpanded }
                            .padding(14.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(
                            "🔧 All JSON Fields Reference",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7986CB)
                        )
                        Icon(
                            imageVector = if (fieldExpanded) Icons.Default.KeyboardArrowUp
                                          else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color(0xFF7986CB)
                        )
                    }

                    if (fieldExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FieldRow("title", "String", "required", "The quest name shown in the launcher")
                            FieldRow("reward", "Int", "default: 5", "Coins earned on completion (1–999)")
                            FieldRow(
                                "days", "Array<String>", "default: all days",
                                "Which weekdays the quest appears. Values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY (or first 3 letters: MON, TUE…)"
                            )
                            FieldRow("timeStart", "Int 0–23", "default: 0", "Hour when the quest becomes active (24h format)")
                            FieldRow("timeEnd", "Int 1–24", "default: 24", "Hour when the quest's time window closes")
                            FieldRow("instructions", "String", "default: \"\"", "Detailed notes shown inside the quest view")
                            FieldRow(
                                "isHardLock", "Boolean", "default: false",
                                "If true, non-study apps are blocked until this quest is completed each day"
                            )
                            FieldRow(
                                "stat1", "Int", "default: 0",
                                "Strength points rewarded on completion. Grows your Strength stat."
                            )
                            FieldRow(
                                "stat2", "Int", "default: 0",
                                "Intelligence points rewarded on completion."
                            )
                            FieldRow(
                                "stat3", "Int", "default: 0",
                                "Focus points rewarded on completion."
                            )
                            FieldRow(
                                "stat4", "Int", "default: 0",
                                "Discipline points rewarded on completion."
                            )

                            Spacer(Modifier.height(4.dp))

                            // Valid day values
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF080810))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Valid day values",
                                    fontSize = 11.sp, color = Color(0xFF7986CB), fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    DAY_NAMES.joinToString(" | "),
                                    fontSize = 10.sp, color = Color(0xFF888888),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "Short form also works: MON | TUE | WED | THU | FRI | SAT | SUN",
                                    fontSize = 10.sp, color = Color(0xFF666666),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // -- COPY TEMPLATES --------------------------------------------
            item {
                val context = LocalContext.current
                var copiedLabel by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF080810))
                        .border(1.dp, Color(0xFF1A1A3A), RoundedCornerShape(14.dp))
                ) {
                    Text(
                        "Copy Templates",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7986CB),
                        modifier = Modifier.padding(14.dp)
                    )

                    val templates = listOf(
                        "Minimal (1 quest)" to EXAMPLE_MINIMAL,
                        "Full example (3 quests)" to EXAMPLE_FULL,
                        "AI prompt" to """
Convert this list into QuestPhone JSON using exactly these fields:
[
  {
    "title": "Quest Name",
    "reward": 10,
    "days": ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
    "timeStart": 9,
    "timeEnd": 17,
    "instructions": "What to do",
    "isHardLock": false,
    "stat1": 0,
    "stat2": 2,
    "stat3": 1,
    "stat4": 1
  }
]
stat1=Strength, stat2=Intelligence, stat3=Focus, stat4=Discipline (0-5 each).
Days must be uppercase full names. timeStart/timeEnd are 0-24 hours.
isHardLock true = blocks phone until quest is done.
My quest list:
[PASTE YOUR QUESTS HERE]""".trim(),
                        "Weekly study plan" to """
[
  {"title":"Study Session 1","reward":15,"days":["MONDAY","WEDNESDAY","FRIDAY"],"timeStart":9,"timeEnd":12,"instructions":"Deep focus 3 hrs. No phone.","isHardLock":true,"stat1":0,"stat2":3,"stat3":2,"stat4":1},
  {"title":"Study Session 2","reward":15,"days":["TUESDAY","THURSDAY"],"timeStart":10,"timeEnd":13,"instructions":"Review notes from previous days.","isHardLock":true,"stat1":0,"stat2":3,"stat3":2,"stat4":1},
  {"title":"Evening Revision","reward":8,"days":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],"timeStart":20,"timeEnd":22,"instructions":"30 min recap before sleep.","isHardLock":false,"stat1":0,"stat2":2,"stat3":1,"stat4":1},
  {"title":"Weekend Practice","reward":20,"days":["SATURDAY","SUNDAY"],"timeStart":10,"timeEnd":14,"instructions":"Mock tests or problem sets.","isHardLock":false,"stat1":0,"stat2":3,"stat3":2,"stat4":2}
]""".trim(),
                        "Morning routine" to """
[
  {"title":"Wake Up & No Phone","reward":5,"days":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],"timeStart":6,"timeEnd":7,"instructions":"No screen for first 30 mins.","isHardLock":true},
  {"title":"Exercise","reward":10,"days":["MONDAY","WEDNESDAY","FRIDAY"],"timeStart":6,"timeEnd":8,"instructions":"30 min workout.","isHardLock":true},
  {"title":"Read 20 Pages","reward":8,"days":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],"timeStart":21,"timeEnd":23,"instructions":"Any book. Track pages.","isHardLock":false}
]""".trim()
                    )

                    templates.forEach { (label, code) ->
                        val isCopied = copiedLabel == label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("quest_template", code))
                                    copiedLabel = label
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    code.lines().take(2).joinToString(" ").take(60) + "…",
                                    fontSize = 10.sp,
                                    color = Color(0xFF444444),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                if (isCopied) "Copied!" else "Copy",
                                fontSize = 12.sp,
                                color = if (isCopied) Color(0xFF4CAF50) else Color(0xFF7986CB),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isCopied) Color(0xFF0A1A0A)
                                        else Color(0xFF0D0D1A)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        HorizontalDivider(color = Color(0xFF111122), thickness = 0.5.dp)
                    }

                    // Load template into editor button
                    TextButton(
                        onClick = { /* jsonInput = EXAMPLE_FULL — auto-load full example */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "After copying, paste it in the box below ↓",
                            fontSize = 11.sp,
                            color = Color(0xFF444466)
                        )
                    }
                }
            }

            // -- JSON Input ------------------------------------------------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste JSON here",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        placeholder = { Text("[ { \"title\": \"Study Physics\", ... } ]", color = Color(0xFF444444)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 300.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    )
                }
            }

            // -- Action buttons --------------------------------------------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.parseJson(jsonInput) },
                        enabled = jsonInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14143A))
                    ) { Text("🔍 Preview") }

                    if (jsonInput.isBlank()) {
                        OutlinedButton(
                            onClick = { jsonInput = EXAMPLE_FULL },
                            modifier = Modifier.weight(1f)
                        ) { Text("Load Example", fontSize = 12.sp) }
                    } else {
                        OutlinedButton(
                            onClick = { jsonInput = ""; vm.clearResults() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear", fontSize = 12.sp) }
                    }
                }
            }

            // -- Parse error / warning -------------------------------------
            if (parseError != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (parseError!!.startsWith("❌")) Color(0xFF1A0808)
                                else Color(0xFF1A1400)
                            )
                            .border(
                                1.dp,
                                if (parseError!!.startsWith("❌")) Color(0xFF5A1A1A) else Color(0xFF3A3000),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            parseError!!,
                            fontSize = 12.sp,
                            color = if (parseError!!.startsWith("❌")) Color(0xFFEF9A9A)
                                    else Color(0xFFFFCC80),
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // -- Preview list ----------------------------------------------
            if (previews.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${previews.size} quest${if (previews.size == 1) "" else "s"} ready",
                                color = Color.White, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Review below then import",
                                color = Color.Gray, fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { vm.importAll() },
                            enabled = !isImporting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B4020))
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF4CAF50),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("✅ Import All", color = Color(0xFF4CAF50))
                            }
                        }
                    }
                }

                items(previews, key = { it.title + it.timeStart }) { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D0D0D))
                            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                            Text(
                                p.title,
                                color = Color.White, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f), fontSize = 14.sp
                            )
                            Text("${p.reward} 🪙", color = Color(0xFFFFAB40), fontSize = 12.sp)
                        }
                        Text(
                            "⏰ ${p.timeStart}:00 – ${p.timeEnd}:00",
                            fontSize = 11.sp, color = Color.Gray
                        )
                        Text(
                            "📅 ${p.days.joinToString(", ") { it.name.take(3) }}",
                            fontSize = 11.sp, color = Color.Gray
                        )
                        if (p.instructions.isNotBlank()) {
                            Text(
                                "📝 ${p.instructions}",
                                fontSize = 11.sp, color = Color(0xFF777777),
                                lineHeight = 16.sp
                            )
                        }
                        if (p.isHardLock) {
                            Text("🔒 Hard Lock", fontSize = 11.sp, color = Color(0xFFEF9A9A))
                        }
                    }
                }
            }
        }
    }
}

// -- Helper composables --------------------------------------------------------

@Composable
private fun TutorialStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1B3A1B)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color(0xFF66BB6A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(body, color = Color(0xFF888888), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun CodeBlock(code: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 10.sp, color = Color(0xFF555555), fontFamily = FontFamily.Monospace)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF060810))
                .border(1.dp, Color(0xFF1A1A2A), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                code,
                fontSize = 10.5.sp,
                color = Color(0xFFAAAAAA),
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun FieldRow(field: String, type: String, default: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D0D16))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\"$field\"",
                fontSize = 12.sp, color = Color(0xFF7986CB),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
            )
            Text(type, fontSize = 10.sp, color = Color(0xFF555566), fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(default, fontSize = 10.sp, color = Color(0xFF444455), fontFamily = FontFamily.Monospace)
        }
        Text(description, fontSize = 11.sp, color = Color(0xFF777777), lineHeight = 16.sp)
    }
}
