package neth.iecal.questphone.app.screens.etc

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.workers.BulkRenameWorker
import neth.iecal.questphone.core.ai.GemmaRepository
import javax.inject.Inject

data class AppRenameEntry(
    val packageName: String,
    val realName: String,
    val suggestedName: String = "",
    val status: RenameStatus = RenameStatus.PENDING
)

enum class RenameStatus { PENDING, LOADING, DONE, SKIPPED, ERROR }

@HiltViewModel
class BulkAiRenameViewModel @Inject constructor(
    val userRepository: UserRepository,
    val gemmaRepository: GemmaRepository
) : ViewModel() {

    private val _entries = MutableStateFlow<List<AppRenameEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()

    private val _mode = MutableStateFlow("rpg")   // "rpg" | "friendly" | "personality"
    val mode = _mode.asStateFlow()

    fun setMode(m: String) { _mode.value = m }

    fun loadApps(pm: PackageManager) {
        viewModelScope.launch {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(intent, 0)
                .mapNotNull { ri ->
                    val name = ri.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val pkg = ri.activityInfo.packageName
                    // Skip already renamed
                    val existingRename = userRepository.userInfo.appRenames[pkg]
                    if (existingRename != null) return@mapNotNull null
                    AppRenameEntry(packageName = pkg, realName = name)
                }
                .sortedBy { it.realName.lowercase() }
            _entries.value = apps
        }
    }

    fun runBulkRename() {
        viewModelScope.launch {
            _isRunning.value = true
            _progress.value = 0
            val current = _entries.value.toMutableList()
            val mode = _mode.value
            val personality = userRepository.userInfo.kaiPersonality
            val rpgEnabled = userRepository.userInfo.rpgModeEnabled

            current.forEachIndexed { i, entry ->
                if (entry.status == RenameStatus.SKIPPED) {
                    _progress.value = i + 1
                    return@forEachIndexed
                }
                // Update status to loading
                current[i] = entry.copy(status = RenameStatus.LOADING)
                _entries.value = current.toList()

                try {
                    val styleHint = when (mode) {
                        "rpg" -> "RPG/fantasy theme. Example: Notes→Tome, Camera→Vision Orb, YouTube→Bard Stage, Maps→Cartographer"
                        "friendly" -> "warm friendly modern"
                        else -> "$personality style, creative, short"
                    }
                    val prompt = "App: ${entry.realName}. Give a $styleHint display name. Reply with the name ONLY. No explanation. No punctuation. Max 3 words."
                    val result = gemmaRepository.quickChat(prompt)
                    val suggestion = result.getOrNull()
                        ?.replace(Regex("""["'*`]"""), "")
                        ?.trim()?.take(25) ?: ""

                    val isLeakedPrompt = suggestion.contains("User") ||
                        suggestion.contains("wants") ||
                        suggestion.contains("display") ||
                        suggestion.length > 30 ||
                        suggestion.isBlank()
                    if (!isLeakedPrompt) {
                        current[i] = entry.copy(suggestedName = suggestion, status = RenameStatus.DONE)
                    } else {
                        current[i] = entry.copy(status = RenameStatus.ERROR)
                    }
                } catch (_: Exception) {
                    current[i] = entry.copy(status = RenameStatus.ERROR)
                }

                _entries.value = current.toList()
                _progress.value = i + 1
                delay(300) // avoid rate limiting
            }
            _isRunning.value = false
        }
    }

    fun toggleSkip(packageName: String) {
        _entries.value = _entries.value.map {
            if (it.packageName == packageName) {
                it.copy(status = if (it.status == RenameStatus.SKIPPED) RenameStatus.PENDING else RenameStatus.SKIPPED)
            } else it
        }
    }

    fun editSuggestion(packageName: String, newName: String) {
        _entries.value = _entries.value.map {
            if (it.packageName == packageName) it.copy(suggestedName = newName) else it
        }
    }

    fun runInBackground(context: android.content.Context) {
        val apps = _entries.value.filter { it.status != RenameStatus.SKIPPED }
        BulkRenameWorker.enqueue(
            context,
            _mode.value,
            apps.map { it.packageName },
            apps.map { it.realName }
        )
    }

    fun cancelBackground(context: android.content.Context) {
        BulkRenameWorker.cancel(context)
    }

    fun applyAll() {
        _entries.value.filter {
            it.status == RenameStatus.DONE && it.suggestedName.isNotBlank()
        }.forEach {
            userRepository.setAppRename(it.packageName, it.suggestedName)
        }
        userRepository.saveUserInfo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAiRenameScreen(navController: NavController, vm: BulkAiRenameViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val progress by vm.progress.collectAsState()
    val mode by vm.mode.collectAsState()
    var applied by remember { mutableStateOf(false) }
    var editingPkg by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadApps(context.packageManager) }

    if (editingPkg != null) {
        AlertDialog(
            onDismissRequest = { editingPkg = null },
            title = { Text("Edit Name") },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.editSuggestion(editingPkg!!, editValue)
                    editingPkg = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingPkg = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("✨ AI Bulk Rename", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (!isRunning && entries.any { it.status == RenameStatus.DONE }) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = { vm.applyAll(); applied = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(
                            if (applied) "✅ Applied!" else "Apply All Renames",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mode selector
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rename Style", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("rpg" to "⚔️ RPG", "friendly" to "😊 Friendly", "personality" to "🤖 Kai Style")
                            .forEach { (id, label) ->
                                FilterChip(
                                    selected = mode == id,
                                    onClick = { vm.setMode(id) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    enabled = !isRunning
                                )
                            }
                    }
                }
            }

            // Run button
            item {
                if (!isRunning) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { vm.runBulkRename(); applied = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            enabled = entries.isNotEmpty()
                        ) {
                            Text("✨ Rename ${entries.count { it.status != RenameStatus.SKIPPED }} Apps (live)")
                        }
                        OutlinedButton(
                            onClick = { vm.runInBackground(context); navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            enabled = entries.isNotEmpty()
                        ) {
                            Text("⬛ Run in Background — auto-applies, you can leave")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { if (entries.isNotEmpty()) progress.toFloat() / entries.size else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Text("Renaming $progress / ${entries.size}…",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // App list
            itemsIndexed(entries, key = { _, e -> e.packageName }) { _, entry ->
                val icon = remember(entry.packageName) {
                    runCatching {
                        context.packageManager.getApplicationIcon(entry.packageName).toBitmap().asImageBitmap()
                    }.getOrNull()
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (entry.status) {
                            RenameStatus.DONE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            RenameStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            RenameStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        Arrangement.spacedBy(10.dp), Alignment.CenterVertically
                    ) {
                        // Icon
                        if (icon != null) {
                            Image(icon, null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                        } else {
                            Box(Modifier.size(36.dp).background(
                                MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)))
                        }

                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.realName, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                color = if (entry.status == RenameStatus.SKIPPED)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.onSurface)

                            when (entry.status) {
                                RenameStatus.LOADING -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                    Text("Kai thinking…", fontSize = 11.sp, color = Color.Gray)
                                }
                                RenameStatus.DONE -> Row(
                                    Modifier.clickable {
                                        editValue = entry.suggestedName
                                        editingPkg = entry.packageName
                                    },
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("→ ${entry.suggestedName}", fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("✏️", fontSize = 10.sp)
                                }
                                RenameStatus.ERROR -> Text("⚠ Kai couldn't suggest a name",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                RenameStatus.SKIPPED -> Text("Skipped", fontSize = 11.sp, color = Color.Gray)
                                else -> {}
                            }
                        }

                        // Skip toggle
                        TextButton(
                            onClick = { vm.toggleSkip(entry.packageName) },
                            enabled = !isRunning
                        ) {
                            Text(if (entry.status == RenameStatus.SKIPPED) "Undo" else "Skip",
                                fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
