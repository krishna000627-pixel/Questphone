package neth.iecal.questphone.app.screens.mylife

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

private sealed class SettingsNav {
    object Passcode : SettingsNav()
    object Home     : SettingsNav()
    object Struggles: SettingsNav()
    object Mantras  : SettingsNav()
    object Profile  : SettingsNav()
}

@Composable
fun MyLifeSettingsScreen(navController: NavController) {
    val ctx = LocalContext.current
    var nav by remember { mutableStateOf<SettingsNav>(SettingsNav.Passcode) }
    var data by remember { mutableStateOf(MyLifeStorage.load(ctx)) }

    fun reload() { data = MyLifeStorage.load(ctx) }

    when (nav) {
        is SettingsNav.Passcode -> SettingsPasscodeGate(
            onBack  = { navController.popBackStack() },
            onSuccess = { nav = SettingsNav.Home }
        )
        is SettingsNav.Home -> SettingsHome(
            onBack        = { navController.popBackStack() },
            onStruggles   = { nav = SettingsNav.Struggles },
            onMantras     = { nav = SettingsNav.Mantras },
            onProfile     = { nav = SettingsNav.Profile }
        )
        is SettingsNav.Struggles -> StrugglesEditor(
            struggles = data.struggles,
            onBack    = { nav = SettingsNav.Home },
            onSave    = { list ->
                MyLifeStorage.saveStruggles(ctx, list)
                reload()
                nav = SettingsNav.Home
            }
        )
        is SettingsNav.Mantras -> MantrasEditor(
            mantras = data.mantras,
            onBack  = { nav = SettingsNav.Home },
            onSave  = { list ->
                MyLifeStorage.saveMantras(ctx, list)
                reload()
                nav = SettingsNav.Home
            }
        )
        is SettingsNav.Profile -> ProfileEditor(
            profile = data.profile,
            onBack  = { nav = SettingsNav.Home },
            onSave  = { profile ->
                MyLifeStorage.saveProfile(ctx, profile)
                reload()
                nav = SettingsNav.Home
            }
        )
    }
}

// ─── Passcode Gate (settings) ─────────────────────────────────────────────────

@Composable
private fun SettingsPasscodeGate(onBack: () -> Unit, onSuccess: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error  by remember { mutableStateOf(false) }
    var shake  by remember { mutableStateOf(false) }

    LaunchedEffect(shake) {
        if (shake) { kotlinx.coroutines.delay(600); shake = false; error = false; input = "" }
    }

    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("⚙", fontSize = 36.sp, color = ML_InkBrown)
            Spacer(Modifier.height(8.dp))
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, color = ML_InkBrown, letterSpacing = 2.sp)
            Text("Enter passcode to edit", fontSize = 11.sp, color = ML_FadedInk,
                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            Spacer(Modifier.height(36.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier.size(14.dp).clip(RoundedCornerShape(7.dp))
                            .background(when {
                                error -> ML_Vermillion
                                i < input.length -> ML_InkBrown
                                else -> ML_DividerColor
                            })
                    )
                }
            }
            if (error) {
                Spacer(Modifier.height(6.dp))
                Text("Incorrect", fontSize = 11.sp, color = ML_Vermillion, fontFamily = FontFamily.Serif)
            }
            Spacer(Modifier.height(28.dp))

            listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("←","0","↩"))
                .forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFF8E8))
                                    .border(1.dp, ML_Border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        when (key) {
                                            "←" -> { if (input.isNotEmpty()) input = input.dropLast(1) }
                                            "↩" -> {
                                                if (input == MyLifeStorage.PASSCODE) onSuccess()
                                                else { error = true; shake = true }
                                            }
                                            else -> {
                                                if (input.length < 4) {
                                                    input += key
                                                    if (input.length == 4) {
                                                        if (input == MyLifeStorage.PASSCODE) onSuccess()
                                                        else { error = true; shake = true }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key, fontSize = 20.sp, fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium, color = ML_InkBrown)
                            }
                        }
                    }
                }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = ML_FadedInk)) {
                Text("← Back", fontFamily = FontFamily.Serif)
            }
        }
    }
}

// ─── Settings Home ────────────────────────────────────────────────────────────

@Composable
private fun SettingsHome(
    onBack: () -> Unit,
    onStruggles: () -> Unit,
    onMantras: () -> Unit,
    onProfile: () -> Unit
) {
    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar("My Life Settings", onBack)

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "What would you like to edit?",
                    fontSize = 13.sp, color = ML_FadedInk,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.height(4.dp))

                SettingsOption(
                    icon = "⚔",
                    title = "Struggles",
                    subtitle = "Add, edit, or mark struggles as overcome",
                    onClick = onStruggles
                )
                SettingsOption(
                    icon = "🕉",
                    title = "Mantras & Prayers",
                    subtitle = "Add mantras in Sanskrit or Devanagari script",
                    onClick = onMantras
                )
                SettingsOption(
                    icon = "📜",
                    title = "Profile Details",
                    subtitle = "Edit your name, dharma, goals and family",
                    onClick = onProfile
                )

                Spacer(Modifier.height(24.dp))
                Text(
                    "॥ सत्यमेव जयते ॥",
                    fontSize = 12.sp, color = ML_FadedInk,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SettingsOption(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFAEDD0))
            .border(0.5.dp, ML_Border.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, color = ML_InkBrown)
            Text(subtitle, fontSize = 11.sp, color = ML_FadedInk,
                fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        }
        Text("›", fontSize = 20.sp, color = ML_Border)
    }
}

// ─── Struggles Editor ─────────────────────────────────────────────────────────

@Composable
private fun StrugglesEditor(
    struggles: List<StruggleEntry>,
    onBack: () -> Unit,
    onSave: (List<StruggleEntry>) -> Unit
) {
    var list by remember { mutableStateOf(struggles.toMutableList()) }
    var showAdd by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDesc  by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = ML_Parchment,
            titleContentColor = ML_InkBrown,
            textContentColor = ML_InkMid,
            title = { Text("Remove struggle?", fontFamily = FontFamily.Serif) },
            text  = { Text("This cannot be undone.", fontFamily = FontFamily.Serif, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    list = list.filter { it.id != deleteTarget }.toMutableList()
                    deleteTarget = null
                }) { Text("Remove", color = ML_Vermillion, fontFamily = FontFamily.Serif) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", fontFamily = FontFamily.Serif, color = ML_FadedInk)
                }
            }
        )
    }

    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar("Struggles", onBack = {
                onSave(list.filter { it.title.isNotBlank() })
            })

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Your struggles are seen here only. Edit or add freely.",
                        fontSize = 11.sp, color = ML_FadedInk,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (showAdd) {
                    item {
                        ScrollCard {
                            Text("New Struggle", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif, color = ML_InkBrown)
                            Spacer(Modifier.height(10.dp))
                            ScrollTextField(newTitle, { newTitle = it }, "Title *")
                            Spacer(Modifier.height(8.dp))
                            ScrollTextField(newDesc, { newDesc = it }, "Description (optional)", maxLines = 4)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (newTitle.isNotBlank()) {
                                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                            list = (list + StruggleEntry(
                                                id = UUID.randomUUID().toString(),
                                                title = newTitle.trim(),
                                                description = newDesc.trim(),
                                                dateAdded = today
                                            )).toMutableList()
                                            newTitle = ""; newDesc = ""; showAdd = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ML_Vermillion),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Add", fontFamily = FontFamily.Serif) }
                                OutlinedButton(
                                    onClick = { showAdd = false; newTitle = ""; newDesc = "" },
                                    border = BorderStroke(1.dp, ML_Border),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel", fontFamily = FontFamily.Serif, color = ML_FadedInk) }
                            }
                        }
                    }
                }

                itemsIndexed(list, key = { _, s -> s.id }) { idx, struggle ->
                    StruggleEditCard(
                        struggle = struggle,
                        onToggleOvercome = {
                            list = list.toMutableList().also { l ->
                                l[idx] = l[idx].copy(isOvercome = !l[idx].isOvercome)
                            }
                        },
                        onDelete = { deleteTarget = struggle.id },
                        onUpdateTitle = { t ->
                            list = list.toMutableList().also { l -> l[idx] = l[idx].copy(title = t) }
                        },
                        onUpdateDesc = { d ->
                            list = list.toMutableList().also { l -> l[idx] = l[idx].copy(description = d) }
                        }
                    )
                }

                if (list.isEmpty() && !showAdd) {
                    item {
                        Text("No struggles recorded.", fontSize = 12.sp, color = ML_FadedInk,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }

            // FAB area
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = { showAdd = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ML_InkBrown),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = ML_Gold)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Struggle", fontFamily = FontFamily.Serif, color = ML_Gold)
                }
            }
        }
    }
}

@Composable
private fun StruggleEditCard(
    struggle: StruggleEntry,
    onToggleOvercome: () -> Unit,
    onDelete: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateDesc: (String) -> Unit
) {
    var editingTitle by remember(struggle.id) { mutableStateOf(struggle.title) }
    var editingDesc  by remember(struggle.id) { mutableStateOf(struggle.description) }

    ScrollCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                ScrollTextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it; onUpdateTitle(it) },
                    label = "Title"
                )
                Spacer(Modifier.height(6.dp))
                ScrollTextField(
                    value = editingDesc,
                    onValueChange = { editingDesc = it; onUpdateDesc(it) },
                    label = "Description",
                    maxLines = 3
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = ML_Vermillion, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = struggle.isOvercome,
                onCheckedChange = { onToggleOvercome() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4A7C4A),
                    uncheckedColor = ML_Border
                )
            )
            Text(
                if (struggle.isOvercome) "Overcome ✓" else "Mark as overcome",
                fontSize = 11.sp, color = if (struggle.isOvercome) Color(0xFF4A7C4A) else ML_FadedInk,
                fontFamily = FontFamily.Serif
            )
            if (struggle.dateAdded.isNotBlank()) {
                Spacer(Modifier.weight(1f))
                Text(struggle.dateAdded, fontSize = 9.sp, color = ML_FadedInk, fontFamily = FontFamily.Serif)
            }
        }
    }
}

// ─── Mantras Editor ───────────────────────────────────────────────────────────

@Composable
private fun MantrasEditor(
    mantras: List<MantraEntry>,
    onBack: () -> Unit,
    onSave: (List<MantraEntry>) -> Unit
) {
    var list by remember { mutableStateOf(mantras.toMutableList()) }
    var showAdd by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newText  by remember { mutableStateOf("") }
    var newNote  by remember { mutableStateOf("") }

    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar("Mantras & Prayers", onBack = {
                onSave(list.filter { it.title.isNotBlank() && it.text.isNotBlank() })
            })

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Add mantras in Sanskrit (संस्कृत), Devanagari (देवनागरी), or any script. They appear in the Spiritual section.",
                        fontSize = 11.sp, color = ML_FadedInk,
                        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (showAdd) {
                    item {
                        ScrollCard {
                            Text("New Mantra / Prayer", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif, color = ML_InkBrown)
                            Spacer(Modifier.height(10.dp))
                            ScrollTextField(newTitle, { newTitle = it }, "Title (e.g. Maha Mrityunjaya)")
                            Spacer(Modifier.height(8.dp))
                            ScrollTextField(newText, { newText = it }, "Mantra text (Devanagari/Sanskrit)", maxLines = 6)
                            Spacer(Modifier.height(8.dp))
                            ScrollTextField(newNote, { newNote = it }, "Note / meaning (optional)", maxLines = 3)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (newTitle.isNotBlank() && newText.isNotBlank()) {
                                            list = (list + MantraEntry(
                                                id = UUID.randomUUID().toString(),
                                                title = newTitle.trim(),
                                                text = newText.trim(),
                                                note = newNote.trim()
                                            )).toMutableList()
                                            newTitle = ""; newText = ""; newNote = ""; showAdd = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ML_Vermillion),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Add", fontFamily = FontFamily.Serif) }
                                OutlinedButton(
                                    onClick = { showAdd = false; newTitle = ""; newText = ""; newNote = "" },
                                    border = BorderStroke(1.dp, ML_Border),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel", fontFamily = FontFamily.Serif, color = ML_FadedInk) }
                            }
                        }
                    }
                }

                itemsIndexed(list, key = { _, m -> m.id }) { idx, mantra ->
                    MantraEditCard(
                        mantra = mantra,
                        onDelete = { list = list.filter { it.id != mantra.id }.toMutableList() },
                        onUpdateTitle = { t -> list = list.toMutableList().also { l -> l[idx] = l[idx].copy(title = t) } },
                        onUpdateText  = { t -> list = list.toMutableList().also { l -> l[idx] = l[idx].copy(text  = t) } },
                        onUpdateNote  = { n -> list = list.toMutableList().also { l -> l[idx] = l[idx].copy(note  = n) } }
                    )
                }

                if (list.isEmpty() && !showAdd) {
                    item {
                        Text("No mantras yet.", fontSize = 12.sp, color = ML_FadedInk,
                            fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = { showAdd = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ML_InkBrown),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = ML_Gold)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Mantra", fontFamily = FontFamily.Serif, color = ML_Gold)
                }
            }
        }
    }
}

@Composable
private fun MantraEditCard(
    mantra: MantraEntry,
    onDelete: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateText:  (String) -> Unit,
    onUpdateNote:  (String) -> Unit
) {
    var title by remember(mantra.id) { mutableStateOf(mantra.title) }
    var text  by remember(mantra.id) { mutableStateOf(mantra.text) }
    var note  by remember(mantra.id) { mutableStateOf(mantra.note) }

    ScrollCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🕉", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                ScrollTextField(title, { title = it; onUpdateTitle(it) }, "Title")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = ML_Vermillion, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onUpdateText(it) },
            label = { Text("Mantra text", fontFamily = FontFamily.Serif, fontSize = 11.sp, color = ML_FadedInk) },
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ML_Gold,
                unfocusedBorderColor = ML_Border,
                focusedTextColor = ML_InkBrown,
                unfocusedTextColor = ML_InkBrown,
                cursorColor = ML_Gold,
                focusedContainerColor = Color(0xFFFFFAF0),
                unfocusedContainerColor = Color(0xFFFFFAF0)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Serif, fontSize = 16.sp, color = ML_InkBrown, lineHeight = 26.sp
            )
        )
        Spacer(Modifier.height(8.dp))
        ScrollTextField(note, { note = it; onUpdateNote(it) }, "Note / meaning (optional)", maxLines = 3)
    }
}

// ─── Profile Editor ───────────────────────────────────────────────────────────

@Composable
private fun ProfileEditor(
    profile: MyLifeProfile,
    onBack: () -> Unit,
    onSave: (MyLifeProfile) -> Unit
) {
    var name         by remember { mutableStateOf(profile.name) }
    var dharma       by remember { mutableStateOf(profile.dharma) }
    var fatherName   by remember { mutableStateOf(profile.fatherName) }
    var motherName   by remember { mutableStateOf(profile.motherName) }
    var trait        by remember { mutableStateOf(profile.dominantTrait) }
    var spiritual    by remember { mutableStateOf(profile.spiritualDescription) }
    var goal         by remember { mutableStateOf(profile.fiveYearGoal) }

    ScrollSurface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar("Edit Profile", onBack = {
                onSave(profile.copy(
                    name = name.trim(),
                    dharma = dharma.trim(),
                    fatherName = fatherName.trim(),
                    motherName = motherName.trim(),
                    dominantTrait = trait.trim(),
                    spiritualDescription = spiritual.trim(),
                    fiveYearGoal = goal.trim()
                ))
            })

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Tap ← Save to save all changes. Leave a field blank to clear it.",
                    fontSize = 11.sp, color = ML_FadedInk,
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic
                )
                ScrollCard {
                    SectionLabel("Identity")
                    ScrollTextField(name, { name = it }, "Full Name")
                    Spacer(Modifier.height(8.dp))
                    ScrollTextField(trait, { trait = it }, "Dominant Trait")
                }
                ScrollCard {
                    SectionLabel("Family")
                    ScrollTextField(fatherName, { fatherName = it }, "Father's Name")
                    Spacer(Modifier.height(8.dp))
                    ScrollTextField(motherName, { motherName = it }, "Mother's Name")
                }
                ScrollCard {
                    SectionLabel("Dharma & Vision")
                    ScrollTextField(dharma, { dharma = it }, "Life Purpose / Dharma", maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    ScrollTextField(goal, { goal = it }, "5-Year Vision", maxLines = 4)
                }
                ScrollCard {
                    SectionLabel("Spiritual Description")
                    ScrollTextField(spiritual, { spiritual = it }, "Your spiritual nature (own words)", maxLines = 5)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, fontSize = 11.sp, color = ML_SaffronDeep,
        fontFamily = FontFamily.Serif, letterSpacing = 1.sp)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = ML_DividerColor, thickness = 0.5.dp)
    Spacer(Modifier.height(8.dp))
}

// ─── Shared: Top Bar ──────────────────────────────────────────────────────────

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFEDD89C))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ML_InkBrown)
            }
            Text(
                title,
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif, color = ML_InkBrown,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(48.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(ML_DividerColor))
    }
}
