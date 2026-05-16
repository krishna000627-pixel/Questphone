package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.tracker.Tracker
import nethical.questphone.data.tracker.TrackerType
import javax.inject.Inject

@HiltViewModel
class TrackerSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    val trackers get() = userRepository.getTrackers()
    fun add(t: Tracker) = userRepository.addTracker(t)
    fun update(t: Tracker) = userRepository.updateTracker(t)
    fun delete(id: String) = userRepository.deleteTracker(id)
}

// Preset templates
private val PRESETS = listOf(
    Tracker(name = "Lecture Backlog", emoji = "📚", type = TrackerType.BACKLOG, value = 5, note = "Pending lectures"),
    Tracker(name = "Days Until Exam", emoji = "📅", type = TrackerType.COUNTDOWN, value = 30, note = "Keep grinding"),
    Tracker(name = "Chapters Done", emoji = "✅", type = TrackerType.COUNTER, value = 0, target = 20),
    Tracker(name = "Today Done", emoji = "🎯", type = TrackerType.CHECKBOX, value = 0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSettingsScreen(
    navController: NavController,
    vm: TrackerSettingsViewModel = hiltViewModel()
) {
    var trackers by remember { mutableStateOf(vm.trackers.toList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTracker by remember { mutableStateOf<Tracker?>(null) }

    fun refresh() { trackers = vm.trackers.toList() }

    if (showAddDialog || editingTracker != null) {
        TrackerEditDialog(
            existing = editingTracker,
            onDismiss = { showAddDialog = false; editingTracker = null },
            onSave = { t ->
                if (editingTracker != null) vm.update(t) else vm.add(t)
                showAddDialog = false; editingTracker = null; refresh()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trackers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add Tracker", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preset chips
            item {
                Text("Quick Presets", fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PRESETS) { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1A1A))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(20.dp))
                                .clickable { vm.add(preset.copy(id = java.util.UUID.randomUUID().toString())); refresh() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("${preset.emoji} ${preset.name}", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }

            if (trackers.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No trackers yet. Tap + or use a preset.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }

            items(trackers, key = { it.id }) { tracker ->
                TrackerSettingsCard(
                    tracker = tracker,
                    onEdit = { editingTracker = tracker },
                    onDelete = { vm.delete(tracker.id); refresh() },
                    onValueChange = { newVal ->
                        vm.update(tracker.copy(value = newVal)); refresh()
                    }
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun TrackerSettingsCard(
    tracker: Tracker,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    val color = Color(tracker.color)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tracker.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(tracker.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (tracker.note.isNotEmpty())
                        Text(tracker.note, fontSize = 11.sp, color = Color.Gray)
                }
            }
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(R.drawable.baseline_info_24), null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                }
            }
        }

        // Value editor depending on type
        when (tracker.type) {
            TrackerType.COUNTDOWN, TrackerType.BACKLOG -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tracker.typeLabel(), fontSize = 11.sp, color = color)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { if (tracker.value > 0) onValueChange(tracker.value - 1) }, modifier = Modifier.size(28.dp)) {
                        Text("−", color = Color.White, fontSize = 18.sp)
                    }
                    Text("${tracker.value}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                    IconButton(onClick = { onValueChange(tracker.value + 1) }, modifier = Modifier.size(28.dp)) {
                        Text("+", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
            TrackerType.COUNTER -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${tracker.value}${if (tracker.target > 0) " / ${tracker.target}" else ""}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { if (tracker.value > 0) onValueChange(tracker.value - 1) }, modifier = Modifier.size(28.dp)) {
                        Text("−", color = Color.White, fontSize = 18.sp)
                    }
                    IconButton(onClick = { onValueChange(tracker.value + 1) }, modifier = Modifier.size(28.dp)) {
                        Text("+", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
            TrackerType.CHECKBOX -> {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (tracker.value == 1) color.copy(alpha = 0.15f) else Color(0xFF1A1A1A))
                        .clickable { onValueChange(if (tracker.value == 1) 0 else 1) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (tracker.value == 1) "✅ Done" else "⬜ Not done", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TrackerEditDialog(
    existing: Tracker?,
    onDismiss: () -> Unit,
    onSave: (Tracker) -> Unit
) {
    var name  by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "📌") }
    var note  by remember { mutableStateOf(existing?.note ?: "") }
    var value by remember { mutableStateOf(existing?.value ?: 0) }
    var target by remember { mutableStateOf(existing?.target ?: 0) }
    var type  by remember { mutableStateOf(existing?.type ?: TrackerType.COUNTDOWN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Tracker" else "New Tracker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(2) },
                        label = { Text("Icon") }, modifier = Modifier.width(72.dp), singleLine = true)
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                // Type selector
                Text("Type", fontSize = 12.sp, color = Color.Gray)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TrackerType.values().toList()) { t ->
                        val sel = t == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Color(0xFF1565C0) else Color(0xFF1A1A1A))
                                .clickable { type = t }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text(t.name.replace("_", " "), fontSize = 12.sp, color = Color.White) }
                    }
                }

                // Value
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Value:", fontSize = 13.sp, color = Color.Gray)
                    IconButton(onClick = { if (value > 0) value-- }, modifier = Modifier.size(28.dp)) { Text("−", color = Color.White) }
                    Text("$value", fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = { value++ }, modifier = Modifier.size(28.dp)) { Text("+", color = Color.White) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val t = existing?.copy(name = name, emoji = emoji, note = note, value = value, target = target, type = type)
                        ?: Tracker(name = name, emoji = emoji, note = note, value = value, target = target, type = type)
                    onSave(t)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun Tracker.typeLabel() = when (type) {
    TrackerType.COUNTDOWN   -> "$value days left"
    TrackerType.BACKLOG     -> "$value backlog"
    TrackerType.COUNTER     -> "$value done"
    TrackerType.CHECKBOX    -> if (value == 1) "Done" else "Pending"
}
