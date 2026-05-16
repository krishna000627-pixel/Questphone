package neth.iecal.questphone.app.screens.routine

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.time.LocalDate

// ── Main Routines Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(navController: NavController) {
    val context = LocalContext.current
    var routines by remember { mutableStateOf(RoutineDatabase.load(context)) }
    var activeRoutine by remember { mutableStateOf<Routine?>(null) }
    var showEditor by remember { mutableStateOf<Routine?>(null) }

    fun reload() { routines = RoutineDatabase.load(context) }

    // ── Active routine runner ─────────────────────────────────────────────────
    activeRoutine?.let { routine ->
        RoutineRunnerScreen(
            routine = routine,
            onComplete = {
                RoutineDatabase.markCompleted(context, routine.id)
                reload()
                activeRoutine = null
            },
            onBack = { activeRoutine = null }
        )
        return
    }

    // ── Editor ────────────────────────────────────────────────────────────────
    showEditor?.let { editing ->
        RoutineEditorScreen(
            initial = editing,
            onSave = { r ->
                RoutineDatabase.upsert(context, r)
                reload()
                showEditor = null
            },
            onDismiss = { showEditor = null }
        )
        return
    }

    // ── List ──────────────────────────────────────────────────────────────────
    val today = LocalDate.now().toString()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Routines", fontWeight = FontWeight.Black)
                        Text("Story-based daily quests", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = Routine() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Routine") }
            )
        }
    ) { padding ->
        if (routines.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("⚔️", fontSize = 56.sp)
                    Text("No routines yet", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Create your first story-based daily routine.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp))
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                items(routines, key = { it.id }) { routine ->
                    val completedToday = routine.lastCompletedDate == today
                    RoutineCard(
                        routine = routine,
                        completedToday = completedToday,
                        onToggleEnabled = {
                            RoutineDatabase.toggle(context, routine.id)
                            reload()
                        },
                        onStart = { activeRoutine = routine },
                        onEdit = { showEditor = routine },
                        onDelete = {
                            RoutineDatabase.delete(context, routine.id)
                            reload()
                        }
                    )
                }

                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

// ── Routine Card ──────────────────────────────────────────────────────────────

@Composable
private fun RoutineCard(
    routine: Routine,
    completedToday: Boolean,
    onToggleEnabled: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete \"${routine.name}\"?") },
            text = { Text("This routine and its story will be lost.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (routine.isEnabled) 1f else 0.55f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header row
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                // Emoji badge
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    Alignment.Center
                ) { Text(routine.emoji, fontSize = 26.sp) }

                Column(Modifier.weight(1f)) {
                    Text(routine.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${routine.steps.size} steps  ·  ${routine.totalCompletions} completions",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (completedToday) {
                        Text("✅ Done today", fontSize = 11.sp,
                            color = Color(0xFF66BB6A), fontWeight = FontWeight.Medium)
                    }
                }

                // On/Off toggle
                Switch(
                    checked = routine.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            // Story intro preview
            if (routine.storyIntro.isNotBlank()) {
                Text(
                    "\"${routine.storyIntro.take(120)}${if (routine.storyIntro.length > 120) "…" else ""}\"",
                    fontSize = 12.sp, fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // Steps preview (expandable)
            if (routine.steps.isNotEmpty()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        if (expanded) "▲ Hide steps" else "▼ See ${routine.steps.size} steps",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                    )
                }
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        routine.steps.forEachIndexed { i, step ->
                            Row(
                                Modifier.fillMaxWidth(),
                                Arrangement.spacedBy(8.dp),
                                Alignment.CenterVertically
                            ) {
                                Text(
                                    "${i + 1}.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(20.dp)
                                )
                                Text(
                                    step.title,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (step.durationMinutes > 0) {
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${step.durationMinutes}m",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action buttons
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                // Start button — grayed out if completed today or disabled
                Button(
                    onClick = onStart,
                    enabled = routine.isEnabled && !completedToday,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (completedToday)
                            MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (completedToday) Icons.Default.Check else Icons.Default.PlayArrow,
                        null, Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (completedToday) "Completed" else "Begin Quest",
                        fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                }
                OutlinedButton(onClick = { showDelete = true }) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── Routine Runner ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineRunnerScreen(
    routine: Routine,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    var steps by remember {
        mutableStateOf(routine.steps.map { it.copy(completed = false) })
    }
    var showIntro by remember { mutableStateOf(routine.storyIntro.isNotBlank()) }
    val allDone = steps.all { it.completed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.emoji)
                        Text(routine.name, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Progress bar
            val progress = steps.count { it.completed }.toFloat() / steps.size.coerceAtLeast(1)
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Progress", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${steps.count { it.completed }} / ${steps.size}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Story intro card — dismissable
            AnimatedVisibility(showIntro && routine.storyIntro.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📖 The Story", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(routine.storyIntro, fontStyle = FontStyle.Italic,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        TextButton(
                            onClick = { showIntro = false },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Begin ▶", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // Steps list
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(steps) { idx, step ->
                    val isNext = !step.completed && steps.take(idx).all { it.completed }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            steps = steps.toMutableList().apply {
                                this[idx] = step.copy(completed = !step.completed)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                step.completed -> MaterialTheme.colorScheme.primaryContainer
                                isNext -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            Arrangement.spacedBy(12.dp),
                            Alignment.CenterVertically
                        ) {
                            // Step number / checkmark
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(
                                        if (step.completed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                Alignment.Center
                            ) {
                                if (step.completed) {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text("${idx + 1}", fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    step.title, fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    textDecoration = if (step.completed) TextDecoration.LineThrough else null,
                                    color = if (step.completed)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (step.description.isNotBlank()) {
                                    Text(step.description, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic, lineHeight = 16.sp)
                                }
                                if (step.durationMinutes > 0 && !step.completed) {
                                    Text("⏱ ${step.durationMinutes} min", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // Complete button
            if (allDone) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("⚔️  Quest Complete! Mark Done", fontWeight = FontWeight.Black,
                        fontSize = 15.sp)
                }
            }
        }
    }
}

// ── Editor ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    initial: Routine,
    onSave: (Routine) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var storyIntro by remember { mutableStateOf(initial.storyIntro) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var steps by remember { mutableStateOf<List<RoutineStep>>(initial.steps) }
    var isEnabled by remember { mutableStateOf(initial.isEnabled) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial.name.isBlank()) "New Routine" else "Edit Routine",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(initial.copy(
                                    name = name.trim(),
                                    storyIntro = storyIntro.trim(),
                                    emoji = emoji,
                                    steps = steps.filter { it.title.isNotBlank() },
                                    isEnabled = isEnabled
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
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Emoji
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showEmojiPicker = !showEmojiPicker },
                    Alignment.Center
                ) { Text(emoji, fontSize = 30.sp) }
                Column {
                    Text("Icon", fontWeight = FontWeight.SemiBold)
                    Text("Tap to change", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(showEmojiPicker) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RoutineDatabase.EMOJI_OPTIONS) { e ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(
                                    if (e == emoji) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { emoji = e; showEmojiPicker = false },
                            Alignment.Center
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Routine Name *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = storyIntro, onValueChange = { storyIntro = it },
                label = { Text("Story / Intro (optional)") },
                placeholder = { Text("Set the scene. Why does this routine matter? What does it represent in the quest of your life?") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                minLines = 3, maxLines = 10
            )

            // Enable toggle
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text("Enable this routine", fontWeight = FontWeight.SemiBold)
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }

            HorizontalDivider()

            // Steps section
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Steps", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    steps = steps + RoutineStep()
                }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add step")
                }
            }

            if (steps.isEmpty()) {
                Text("Add at least one step to your routine.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            steps.forEachIndexed { idx, step ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Step ${idx + 1}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(
                                onClick = {
                                    steps = steps.toMutableList().apply { removeAt(idx) }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        OutlinedTextField(
                            value = step.title,
                            onValueChange = { v ->
                                steps = steps.toMutableList().apply { this[idx] = step.copy(title = v) }
                            },
                            label = { Text("Step title *") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = step.description,
                            onValueChange = { v ->
                                steps = steps.toMutableList().apply { this[idx] = step.copy(description = v) }
                            },
                            label = { Text("Flavour text / description") },
                            placeholder = { Text("Story notes for this step…") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        OutlinedTextField(
                            value = if (step.durationMinutes == 0) "" else step.durationMinutes.toString(),
                            onValueChange = { v ->
                                steps = steps.toMutableList().apply {
                                    this[idx] = step.copy(durationMinutes = v.toIntOrNull() ?: 0)
                                }
                            },
                            label = { Text("Duration (minutes, optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
