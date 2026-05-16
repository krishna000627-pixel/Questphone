package neth.iecal.questphone.app.screens.people

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ── People DB Style Themes ────────────────────────────────────────────────────
// Three distinct aesthetic identities for the People DB screen.
// The active style is persisted via SharedPreferences.

enum class PeopleDbStyle(
    val displayName: String,
    val subtitle: String,
    val emoji: String,
    val accent: Color,
    val headerBg: Color,
    val cardBg: Color,
    val chipBg: Color,
    val chipText: Color
) {
    /**
     * GRIMOIRE — Dark mystical tome. Matches QuestPhone's RPG soul.
     * Deep indigo + arcane gold. Each person is a soul bound to the registry.
     */
    GRIMOIRE(
        displayName = "Grimoire",
        subtitle = "Souls of the Realm",
        emoji = "📖",
        accent = Color(0xFFB39DDB),
        headerBg = Color(0xFF1A1025),
        cardBg = Color(0xFF201530),
        chipBg = Color(0xFF4A3570),
        chipText = Color(0xFFE1C4FF)
    ),

    /**
     * CHRONICLE — Clean field-journal aesthetic. Warm amber on dark charcoal.
     * Each person is a recorded entry in your personal history.
     */
    CHRONICLE(
        displayName = "Chronicle",
        subtitle = "Field Notes",
        emoji = "📜",
        accent = Color(0xFFFFB74D),
        headerBg = Color(0xFF1C1A16),
        cardBg = Color(0xFF252118),
        chipBg = Color(0xFF3D3520),
        chipText = Color(0xFFFFE0A0)
    ),

    /**
     * ROSTER — Bold, modern command roster. Teal on near-black.
     * Clean lines, no flourish. Everyone has a file. Nothing is forgotten.
     */
    ROSTER(
        displayName = "Roster",
        subtitle = "Command Register",
        emoji = "🗂️",
        accent = Color(0xFF4DD0E1),
        headerBg = Color(0xFF0D1B1E),
        cardBg = Color(0xFF102025),
        chipBg = Color(0xFF1B3840),
        chipText = Color(0xFFB0F0F8)
    )
}

private const val STYLE_PREF = "people_db_style"

private fun loadStyle(context: android.content.Context): PeopleDbStyle {
    val name = context.getSharedPreferences(STYLE_PREF, android.content.Context.MODE_PRIVATE)
        .getString(STYLE_PREF, PeopleDbStyle.GRIMOIRE.name)
    return try { PeopleDbStyle.valueOf(name!!) } catch (_: Exception) { PeopleDbStyle.GRIMOIRE }
}

private fun saveStyle(context: android.content.Context, style: PeopleDbStyle) {
    context.getSharedPreferences(STYLE_PREF, android.content.Context.MODE_PRIVATE)
        .edit().putString(STYLE_PREF, style.name).apply()
}

// ── Main List Screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleDatabaseScreen(navController: NavController) {
    val context = LocalContext.current
    var people by remember { mutableStateOf(PeopleDatabase.load(context)) }
    var search by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf<PeopleScreen>(PeopleScreen.List) }
    var style by remember { mutableStateOf(loadStyle(context)) }
    var showStylePicker by remember { mutableStateOf(false) }

    fun reload() { people = PeopleDatabase.load(context) }

    when (val s = screen) {
        is PeopleScreen.List -> { /* shown below */ }
        is PeopleScreen.Detail -> {
            PersonDetailScreen(
                person = s.person,
                style = style,
                onEdit = { screen = PeopleScreen.Editor(s.person) },
                onDelete = {
                    PeopleDatabase.delete(context, s.person.id)
                    reload()
                    screen = PeopleScreen.List
                },
                onBack = { screen = PeopleScreen.List }
            )
            return
        }
        is PeopleScreen.Editor -> {
            PersonEditorScreen(
                initial = s.person,
                style = style,
                onSave = { person ->
                    PeopleDatabase.upsert(context, person)
                    reload()
                    screen = PeopleScreen.List
                },
                onDismiss = { screen = PeopleScreen.List }
            )
            return
        }
    }

    val filtered = people.filter { p ->
        search.isBlank() ||
        p.name.contains(search, true) ||
        p.relation.contains(search, true) ||
        p.notes.contains(search, true) ||
        p.tags.any { it.contains(search, true) } ||
        p.customFields.any { it.label.contains(search, true) || it.value.contains(search, true) }
    }

    // Style picker sheet
    if (showStylePicker) {
        AlertDialog(
            onDismissRequest = { showStylePicker = false },
            title = { Text("Choose Style") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PeopleDbStyle.entries.forEach { s ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    style = s
                                    saveStyle(context, s)
                                    showStylePicker = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (s == style) s.chipBg else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                Arrangement.spacedBy(12.dp),
                                Alignment.CenterVertically
                            ) {
                                Text(s.emoji, fontSize = 24.sp)
                                Column(Modifier.weight(1f)) {
                                    Text(s.displayName, fontWeight = FontWeight.Bold)
                                    Text(s.subtitle, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (s == style) {
                                    Icon(Icons.Default.Check, null,
                                        tint = style.accent)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStylePicker = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        containerColor = style.headerBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = style.headerBg,
                    titleContentColor = Color.White
                ),
                title = {
                    Column {
                        Text(
                            "${style.emoji} People",
                            fontWeight = FontWeight.Black,
                            color = style.accent
                        )
                        Text(
                            "${people.size} in ${style.subtitle}",
                            fontSize = 11.sp,
                            color = style.accent.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showStylePicker = true }) {
                        Icon(Icons.Default.Palette, "Theme", tint = style.accent)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { screen = PeopleScreen.Editor(null) },
                containerColor = style.accent,
                contentColor = style.headerBg,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Person", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(style.headerBg)
                .padding(padding)
        ) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search name, notes, tags…", color = style.accent.copy(0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = style.accent) },
                trailingIcon = {
                    if (search.isNotEmpty())
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Clear, null, tint = style.accent)
                        }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = style.accent,
                    unfocusedBorderColor = style.accent.copy(0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = style.accent
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("👥", fontSize = 56.sp)
                        Text(
                            if (search.isBlank()) "No people yet" else "No results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            if (search.isBlank()) "Add yourself, friends, family — anyone important to you."
                            else "Try different keywords.",
                            color = style.accent.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(filtered, key = { it.id }) { person ->
                        PersonCard(
                            person = person,
                            style = style,
                            onClick = { screen = PeopleScreen.Detail(person) },
                            onEdit = { screen = PeopleScreen.Editor(person) },
                            onDelete = {
                                PeopleDatabase.delete(context, person.id)
                                reload()
                            }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

// ── Navigation state ─────────────────────────────────────────────────────────

sealed class PeopleScreen {
    data object List : PeopleScreen()
    data class Detail(val person: PersonEntry) : PeopleScreen()
    data class Editor(val person: PersonEntry?) : PeopleScreen()
}

// ── Person Card ───────────────────────────────────────────────────────────────

@Composable
private fun PersonCard(
    person: PersonEntry,
    style: PeopleDbStyle,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove ${person.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = style.cardBg)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            Arrangement.spacedBy(12.dp), Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(style.chipBg),
                Alignment.Center
            ) { Text(person.emoji, fontSize = 24.sp) }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(person.name, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = Color.White)
                    if (person.relation.isNotBlank()) {
                        Surface(color = style.chipBg, shape = RoundedCornerShape(50)) {
                            Text(
                                person.relation,
                                Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                fontSize = 10.sp, color = style.chipText,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (person.notes.isNotBlank()) {
                    Text(
                        person.notes.lines().first().take(70) +
                                if (person.notes.length > 70) "…" else "",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
                if (person.tags.isNotEmpty()) {
                    Text(
                        person.tags.take(4).joinToString(" · ") { "#$it" },
                        fontSize = 10.sp, color = style.accent
                    )
                }
            }

            Column {
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(15.dp),
                        tint = style.accent.copy(alpha = 0.7f))
                }
                IconButton(onClick = { showDeleteConfirm = true }, Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ── Detail Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonDetailScreen(
    person: PersonEntry,
    style: PeopleDbStyle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove ${person.name}?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = style.headerBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = style.headerBg),
                title = { Text(person.name, fontWeight = FontWeight.Bold, color = style.accent) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = style.accent)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(90.dp).clip(CircleShape).background(style.chipBg),
                        Alignment.Center
                    ) { Text(person.emoji, fontSize = 42.sp) }
                    Text(person.name, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    if (person.relation.isNotBlank()) {
                        Surface(color = style.chipBg, shape = RoundedCornerShape(50)) {
                            Text(
                                person.relation,
                                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                fontWeight = FontWeight.SemiBold, color = style.chipText
                            )
                        }
                    }
                }
            }

            if (person.tags.isNotEmpty()) item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    person.tags.forEach { tag ->
                        Surface(color = style.chipBg, shape = RoundedCornerShape(50)) {
                            Text("#$tag", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp, color = style.accent,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (person.birthday.isNotBlank()) item {
                StyledDetailRow("🎂", "Birthday", person.birthday, style)
            }
            if (person.phone.isNotBlank()) item {
                StyledDetailRow("📞", "Phone", person.phone, style)
            }
            if (person.email.isNotBlank()) item {
                StyledDetailRow("📧", "Email", person.email, style)
            }

            if (person.notes.isNotBlank()) item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = style.cardBg)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notes", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall, color = style.accent)
                        HorizontalDivider(color = style.accent.copy(0.2f))
                        Text(person.notes, lineHeight = 22.sp,
                            style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            if (person.customFields.isNotEmpty()) {
                items(person.customFields.filter { it.label.isNotBlank() && it.value.isNotBlank() }) { f ->
                    StyledDetailRow("📌", f.label, f.value, style)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun StyledDetailRow(icon: String, label: String, value: String, style: PeopleDbStyle) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = style.cardBg)
    ) {
        Row(Modifier.padding(14.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Column {
                Text(label, fontSize = 11.sp, color = style.accent, fontWeight = FontWeight.Medium)
                Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            }
        }
    }
}

// ── Editor Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonEditorScreen(
    initial: PersonEntry?,
    style: PeopleDbStyle,
    onSave: (PersonEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var relation by remember { mutableStateOf(initial?.relation ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "👤") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var birthday by remember { mutableStateOf(initial?.birthday ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var tagsText by remember { mutableStateOf(initial?.tags?.joinToString(", ") ?: "") }

    // FIX: explicit type annotation so Kotlin doesn't infer MutableList<Nothing>
    var customFields by remember {
        mutableStateOf<List<CustomField>>(initial?.customFields ?: emptyList())
    }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showOptional by remember { mutableStateOf(
        initial != null && (initial.birthday.isNotBlank() || initial.phone.isNotBlank() || initial.email.isNotBlank())
    ) }

    Scaffold(
        containerColor = style.headerBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = style.headerBg),
                title = {
                    Text(
                        if (initial == null) "Add Person" else "Edit ${initial.name}",
                        fontWeight = FontWeight.Bold,
                        color = style.accent
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val tags = tagsText.split(",")
                                    .map { it.trim() }.filter { it.isNotBlank() }
                                onSave(PersonEntry(
                                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(), relation = relation.trim(),
                                    emoji = emoji, notes = notes.trim(),
                                    birthday = birthday.trim(), phone = phone.trim(),
                                    email = email.trim(), tags = tags,
                                    customFields = customFields.filter {
                                        it.label.isNotBlank() && it.value.isNotBlank()
                                    }
                                ))
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = style.accent)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(style.headerBg)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Emoji picker ─────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(style.chipBg)
                        .clickable { showEmojiPicker = !showEmojiPicker },
                    Alignment.Center
                ) { Text(emoji, fontSize = 30.sp) }
                Column {
                    Text("Avatar", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Tap to change", fontSize = 12.sp, color = style.accent.copy(0.6f))
                }
            }

            AnimatedVisibility(showEmojiPicker) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PeopleDatabase.EMOJI_OPTIONS) { e ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (e == emoji) style.accent else style.chipBg)
                                .clickable { emoji = e; showEmojiPicker = false },
                            Alignment.Center
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }

            HorizontalDivider(color = style.accent.copy(0.2f))

            // ── Name ─────────────────────────────────────────────────────
            StyledTextField(value = name, onValueChange = { name = it },
                label = "Name *", style = style)

            // ── Relation chips ────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Relation", fontWeight = FontWeight.SemiBold,
                    color = Color.White, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PeopleDatabase.RELATION_OPTIONS) { r ->
                        FilterChip(
                            selected = relation == r,
                            onClick = { relation = if (relation == r) "" else r },
                            label = { Text(r, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = style.accent,
                                selectedLabelColor = style.headerBg
                            )
                        )
                    }
                }
                StyledTextField(value = relation, onValueChange = { relation = it },
                    label = "Custom relation", style = style,
                    placeholder = "e.g. Study partner, Gym buddy…")
            }

            // ── Notes ─────────────────────────────────────────────────────
            StyledTextField(
                value = notes, onValueChange = { notes = it },
                label = "Notes", style = style,
                placeholder = "Personality, backstory, how you met, goals, memories…",
                minLines = 5, maxLines = 20
            )

            // ── Tags ──────────────────────────────────────────────────────
            StyledTextField(value = tagsText, onValueChange = { tagsText = it },
                label = "Tags", style = style,
                placeholder = "college, physics, close (comma separated)")

            // ── Optional fields toggle ────────────────────────────────────
            TextButton(
                onClick = { showOptional = !showOptional },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (showOptional) "▲ Hide contact details"
                    else "▼ Add contact details (phone, email, birthday)",
                    fontSize = 13.sp, color = style.accent
                )
            }

            AnimatedVisibility(showOptional) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StyledTextField(value = birthday, onValueChange = { birthday = it },
                        label = "Birthday", style = style, placeholder = "yyyy-MM-dd")
                    StyledTextField(value = phone, onValueChange = { phone = it },
                        label = "Phone", style = style)
                    StyledTextField(value = email, onValueChange = { email = it },
                        label = "Email", style = style)
                }
            }

            // ── Custom fields ─────────────────────────────────────────────
            HorizontalDivider(color = style.accent.copy(0.2f))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Extra Fields", fontWeight = FontWeight.SemiBold, color = Color.White)
                TextButton(onClick = {
                    // FIX: using List<CustomField> — no mutableListOf() type inference issue
                    customFields = customFields + CustomField()
                }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = style.accent)
                    Spacer(Modifier.width(4.dp))
                    Text("Add field", color = style.accent)
                }
            }

            if (customFields.isEmpty()) {
                Text(
                    "Add any field you want — social handle, city, school, favourite food, anything.",
                    fontSize = 12.sp, color = style.accent.copy(0.5f)
                )
            }

            // FIX: forEachIndexed inside @Composable context — works correctly now that
            // customFields is properly typed as List<CustomField>
            customFields.forEachIndexed { idx, field ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = style.cardBg)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Field ${idx + 1}", fontSize = 11.sp, color = style.accent)
                            IconButton(
                                onClick = {
                                    customFields = customFields.toMutableList().apply { removeAt(idx) }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        StyledTextField(
                            value = field.label,
                            onValueChange = { v ->
                                customFields = customFields.toMutableList().apply {
                                    this[idx] = field.copy(label = v)
                                }
                            },
                            label = "Label", style = style,
                            placeholder = "e.g. Instagram, School, City…"
                        )
                        StyledTextField(
                            value = field.value,
                            onValueChange = { v ->
                                customFields = customFields.toMutableList().apply {
                                    this[idx] = field.copy(value = v)
                                }
                            },
                            label = "Value", style = style
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    style: PeopleDbStyle,
    placeholder: String = "",
    minLines: Int = 1,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = style.accent.copy(0.8f)) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = Color.White.copy(0.3f)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = style.accent,
            unfocusedBorderColor = style.accent.copy(0.3f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = style.accent,
            focusedLabelColor = style.accent,
            unfocusedLabelColor = style.accent.copy(0.6f)
        ),
        minLines = minLines, maxLines = maxLines,
        modifier = Modifier.fillMaxWidth()
            .then(if (minLines > 1) Modifier.heightIn(min = 140.dp) else Modifier)
    )
}
