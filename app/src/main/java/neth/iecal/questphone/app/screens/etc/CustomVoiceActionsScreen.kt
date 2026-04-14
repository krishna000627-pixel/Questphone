package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import nethical.questphone.core.core.utils.managers.reloadApps
import nethical.questphone.data.CustomVoiceAction
import neth.iecal.questphone.R

/**
 * Custom Voice Actions sub-page — Fix #5.
 * User can say "open study app" → launcher opens a chosen app.
 * Actions only fire when launcher is in the foreground (Jarvis checks this).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomVoiceActionsScreen(
    navController: NavController,
    settingsVm: LauncherSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var actions by remember { mutableStateOf(settingsVm.getCustomVoiceActions().toList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var phraseInput by remember { mutableStateOf("") }
    var selectedPkg by remember { mutableStateOf("") }
    var selectedPkgName by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf<List<nethical.questphone.data.AppInfo>>(emptyList()) }
    var appSearch by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        reloadApps(context.packageManager, context).onSuccess { apps ->
            allApps = apps.sortedBy { it.name }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Select App") },
            text = {
                Column {
                    OutlinedTextField(
                        value = appSearch, onValueChange = { appSearch = it },
                        label = { Text("Search") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(allApps.filter { it.name.contains(appSearch, ignoreCase = true) }) { app ->
                            TextButton(
                                onClick = {
                                    selectedPkg = app.packageName
                                    selectedPkgName = app.name
                                    showAppPicker = false
                                    appSearch = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(app.name, modifier = Modifier.fillMaxWidth(), color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAppPicker = false }) { Text("Cancel") } }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; phraseInput = ""; selectedPkg = ""; selectedPkgName = "" },
            title = { Text("Add Voice Command") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("When you say this phrase with the wake word, the app will open.", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        label = { Text("Phrase (e.g. open study app)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                    ) {
                        Text(if (selectedPkgName.isBlank()) "Select App →" else "App: $selectedPkgName", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (phraseInput.isNotBlank() && selectedPkg.isNotBlank()) {
                            val action = CustomVoiceAction(phrase = phraseInput.trim(), packageName = selectedPkg)
                            settingsVm.addVoiceAction(action)
                            actions = settingsVm.getCustomVoiceActions().toList()
                            showAddDialog = false
                            phraseInput = ""; selectedPkg = ""; selectedPkgName = ""
                        }
                    },
                    enabled = phraseInput.isNotBlank() && selectedPkg.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; phraseInput = ""; selectedPkg = ""; selectedPkgName = "" }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎙️ Custom Voice Commands", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add command", tint = Color(0xFF4CAF50))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "These commands only work when the launcher is in the foreground. Say your Jarvis wake word + phrase to launch the app.",
                    fontSize = 12.sp, color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (actions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No commands yet. Tap + to add one.", color = Color.Gray)
                    }
                }
            }

            items(actions, key = { it.phrase }) { action ->
                val appName = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(action.packageName, 0)
                    ).toString()
                } catch (_: Exception) { action.packageName }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("\"${action.phrase}\"", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("→ $appName", color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        settingsVm.removeVoiceAction(action.phrase)
                        actions = settingsVm.getCustomVoiceActions().toList()
                    }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFF666666))
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
