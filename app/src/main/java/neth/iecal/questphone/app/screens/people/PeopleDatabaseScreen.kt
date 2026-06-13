package neth.iecal.questphone.app.screens.people

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ── Internal nav ──────────────────────────────────────────────────────────────

private sealed class PeopleNav {
    object Home : PeopleNav()
    data class Detail(val person: PersonEntry) : PeopleNav()
    data class Editor(val person: PersonEntry?) : PeopleNav()
}

// ── Root ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleDatabaseScreen(navController: NavController) {
    val ctx = LocalContext.current
    var people by remember { mutableStateOf(PeopleDatabase.load(ctx)) }
    var search by remember { mutableStateOf("") }
    var nav by remember { mutableStateOf<PeopleNav>(PeopleNav.Home) }
    var style by remember { mutableStateOf(PeopleDatabase.getListStyle(ctx)) }

    fun reload() { people = PeopleDatabase.load(ctx) }

    val cur = nav
    if (cur is PeopleNav.Detail) {
        PersonDetailScreen(
            person = cur.person,
            onEdit = { nav = PeopleNav.Editor(cur.person) },
            onDelete = { PeopleDatabase.delete(ctx, cur.person.id); reload(); nav = PeopleNav.Home },
            onBack = { nav = PeopleNav.Home }
        )
        return
    }
    if (cur is PeopleNav.Editor) {
        PersonEditorScreen(
            initial = cur.person,
            onSave = { p -> PeopleDatabase.upsert(ctx, p); reload(); nav = PeopleNav.Home },
            onDismiss = { nav = PeopleNav.Home }
        )
        return
    }

    val filtered = people.filter { p ->
        search.isBlank() ||
        p.name.contains(search, ignoreCase = true) ||
        p.relation.contains(search, ignoreCase = true) ||
        p.notes.contains(search, ignoreCase = true) ||
        p.tags.any { t -> t.contains(search, ignoreCase = true) } ||
        p.customFields.any { f ->
            f.label.contains(search, ignoreCase = true) ||
            f.value.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("People", fontWeight = FontWeight.Black)
                        Text("${people.size} person${if (people.size != 1) "s" else ""}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val next = when (style) {
                            PeopleListStyle.CARD -> PeopleListStyle.COMPACT
                            PeopleListStyle.COMPACT -> PeopleListStyle.GRIMOIRE
                            else -> PeopleListStyle.CARD
                        }
                        style = next
                        PeopleDatabase.setListStyle(ctx, next)
                    }) {
                        Icon(
                            when (style) {
                                PeopleListStyle.COMPACT -> Icons.AutoMirrored.Filled.List
                                PeopleListStyle.GRIMOIRE -> Icons.Default.Book
                                else -> Icons.Default.ViewModule
                            },
                            contentDescription = "Switch style",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav = PeopleNav.Editor(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Person") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search name, notes, tags…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty())
                        IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = when (style) {
                    PeopleListStyle.COMPACT -> "Compact View"
                    PeopleListStyle.GRIMOIRE -> "Grimoire View"
                    else -> "Card View"
                },
                fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("👥", fontSize = 56.sp)
                        Text(if (search.isBlank()) "No people yet" else "No results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            if (search.isBlank()) "Add yourself, friends, family — anyone important."
                            else "Try different keywords.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        if (style == PeopleListStyle.COMPACT) 4.dp else 8.dp
                    )
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(count = filtered.size, key = { i: Int -> filtered[i].id }) { i: Int ->
                        val person = filtered[i]
                        when (style) {
                            PeopleListStyle.COMPACT -> PersonCompactRow(person,
                                onClick = { nav = PeopleNav.Detail(person) },
                                onEdit = { nav = PeopleNav.Editor(person) },
                                onDelete = { PeopleDatabase.delete(ctx, person.id); reload() })
                            PeopleListStyle.GRIMOIRE -> PersonGrimoireEntry(person,
                                onClick = { nav = PeopleNav.Detail(person) },
                                onEdit = { nav = PeopleNav.Editor(person) },
                                onDelete = { PeopleDatabase.delete(ctx, person.id); reload() })
                            else -> PersonCard(person,
                                onClick = { nav = PeopleNav.Detail(person) },
                                onEdit = { nav = PeopleNav.Editor(person) },
                                onDelete = { PeopleDatabase.delete(ctx, person.id); reload() })
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

// ── Style 1: Card ─────────────────────────────────────────────────────────────

@Composable
private fun PersonCard(person: PersonEntry, onClick: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit) {
    var del by remember { mutableStateOf(false) }
    if (del) DeleteDialog(person.name, { onDelete(); del = false }, { del = false })
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(),
            Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
                Text(person.emoji, fontSize = 24.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(person.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (person.relation.isNotBlank()) RelationChip(person.relation)
                }
                if (person.notes.isNotBlank()) {
                    val p = person.notes.lines().first().take(70)
                    Text(if (person.notes.length > 70) "$p…" else p,
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
                if (person.tags.isNotEmpty())
                    Text(person.tags.take(4).joinToString(" · ") { "#$it" },
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column {
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { del = true }, Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ── Style 2: Compact ──────────────────────────────────────────────────────────

@Composable
private fun PersonCompactRow(person: PersonEntry, onClick: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit) {
    var del by remember { mutableStateOf(false) }
    if (del) DeleteDialog(person.name, { onDelete(); del = false }, { del = false })
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        Arrangement.spacedBy(10.dp), Alignment.CenterVertically
    ) {
        Text(person.emoji, fontSize = 20.sp)
        Text(person.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        if (person.relation.isNotBlank()) RelationChip(person.relation)
        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Edit, null, Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { del = true }, Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, null, Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
    HorizontalDivider(thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

// ── Style 3: Grimoire ─────────────────────────────────────────────────────────

@Composable
private fun PersonGrimoireEntry(person: PersonEntry, onClick: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit) {
    var del by remember { mutableStateOf(false) }
    if (del) DeleteDialog(person.name, { onDelete(); del = false }, { del = false })
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Row(Modifier.weight(1f), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    Text(person.emoji, fontSize = 22.sp)
                    Column {
                        Text(person.name, fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp, fontFamily = FontFamily.Serif)
                        if (person.relation.isNotBlank())
                            Text("— ${person.relation}", fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(13.dp))
                    }
                    IconButton(onClick = { del = true }, Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }
            }
            if (person.notes.isNotBlank()) {
                val p = person.notes.lines().first().take(80)
                Text("\"${if (person.notes.length > 80) "$p…" else p}\"",
                    fontSize = 12.sp, fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Serif)
            }
            if (person.tags.isNotEmpty())
                Text(person.tags.take(5).joinToString("  ✦  "),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun RelationChip(relation: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
        Text(relation, Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Remove $name?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Detail ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonDetailScreen(person: PersonEntry, onEdit: () -> Unit,
    onDelete: () -> Unit, onBack: () -> Unit) {
    var del by remember { mutableStateOf(false) }
    if (del) DeleteDialog(person.name, { onDelete(); del = false }, { del = false })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { del = true }) {
                        Icon(Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(90.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                        Alignment.Center) { Text(person.emoji, fontSize = 42.sp) }
                    Text(person.name, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineSmall)
                    if (person.relation.isNotBlank()) RelationChip(person.relation)
                }
            }
            if (person.tags.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    person.tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("#$tag", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            if (person.birthday.isNotBlank()) item { DetailRow("🎂", "Birthday", person.birthday) }
            if (person.phone.isNotBlank()) item { DetailRow("📞", "Phone", person.phone) }
            if (person.email.isNotBlank()) item { DetailRow("📧", "Email", person.email) }
            if (person.notes.isNotBlank()) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notes", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider()
                        Text(person.notes, lineHeight = 22.sp,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val validFields = person.customFields.filter { f ->
                f.label.isNotBlank() && f.value.isNotBlank()
            }
            items(count = validFields.size) { i -> DetailRow("📌", validFields[i].label, validFields[i].value) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun DetailRow(icon: String, label: String, value: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(14.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Column {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium)
                Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

// ── Editor ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonEditorScreen(initial: PersonEntry?, onSave: (PersonEntry) -> Unit,
    onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var relation by remember { mutableStateOf(initial?.relation ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "👤") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var birthday by remember { mutableStateOf(initial?.birthday ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var tagsText by remember { mutableStateOf(initial?.tags?.joinToString(", ") ?: "") }
    var customFields by remember { mutableStateOf<List<CustomField>>(initial?.customFields ?: emptyList()) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showOptional by remember { mutableStateOf(
        initial != null && (initial.birthday.isNotBlank() ||
            initial.phone.isNotBlank() || initial.email.isNotBlank())) }

    // capture list contents into local vals — avoids any lazy items() conflict
    val emojiList = PeopleDatabase.EMOJI_OPTIONS
    val relationList = PeopleDatabase.RELATION_OPTIONS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Add Person" else "Edit ${initial.name}",
                    fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
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
                                    customFields = customFields.filter { f ->
                                        f.label.isNotBlank() && f.value.isNotBlank()
                                    }
                                ))
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { showEmojiPicker = !showEmojiPicker }, Alignment.Center) {
                    Text(emoji, fontSize = 30.sp)
                }
                Column {
                    Text("Avatar", fontWeight = FontWeight.SemiBold)
                    Text("Tap to change", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(showEmojiPicker) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(count = emojiList.size) { i: Int ->
                        val e = emojiList[i]
                        Box(Modifier.size(44.dp).clip(CircleShape)
                            .background(if (e == emoji) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { emoji = e; showEmojiPicker = false }, Alignment.Center) {
                            Text(e, fontSize = 22.sp)
                        }
                    }
                }
            }
            HorizontalDivider()

            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Name *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Relation", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(count = relationList.size) { i: Int ->
                        val r = relationList[i]
                        FilterChip(selected = relation == r,
                            onClick = { relation = if (relation == r) "" else r },
                            label = { Text(r, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(value = relation, onValueChange = { relation = it },
                    label = { Text("Custom relation") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Study partner, Gym buddy…") })
            }

            OutlinedTextField(value = notes, onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Anything about this person…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                minLines = 5, maxLines = 20)

            OutlinedTextField(value = tagsText, onValueChange = { tagsText = it },
                label = { Text("Tags") },
                placeholder = { Text("college, close, cricket  (comma separated)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            TextButton(onClick = { showOptional = !showOptional },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (showOptional) "▲ Hide contact details"
                     else "▼ Add contact details (phone, email, birthday)", fontSize = 13.sp)
            }
            AnimatedVisibility(showOptional) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = birthday, onValueChange = { birthday = it },
                        label = { Text("Birthday") }, placeholder = { Text("yyyy-MM-dd") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it },
                        label = { Text("Phone") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it },
                        label = { Text("Email") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Extra Fields", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { customFields = customFields + CustomField() }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add field")
                }
            }
            if (customFields.isEmpty()) {
                Text("Add any field — social handle, city, school…",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            customFields.forEachIndexed { idx: Int, field: CustomField ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Field ${idx + 1}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(
                                onClick = { customFields = customFields.toMutableList().also { it.removeAt(idx) } },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        OutlinedTextField(value = field.label,
                            onValueChange = { v: String ->
                                customFields = customFields.toMutableList().also { it[idx] = field.copy(label = v) }
                            },
                            label = { Text("Label") },
                            placeholder = { Text("e.g. Instagram, School, City…") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = field.value,
                            onValueChange = { v: String ->
                                customFields = customFields.toMutableList().also { it[idx] = field.copy(value = v) }
                            },
                            label = { Text("Value") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
