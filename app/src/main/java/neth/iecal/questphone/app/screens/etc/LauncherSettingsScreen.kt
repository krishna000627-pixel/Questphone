package neth.iecal.questphone.app.screens.etc

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.focus.ACTION_START
import neth.iecal.questphone.core.focus.ACTION_STOP
import neth.iecal.questphone.core.focus.JarvisListenerService
import neth.iecal.questphone.core.focus.isFocusSchedulingEnabled
import neth.iecal.questphone.core.focus.isStrangerMode
import neth.iecal.questphone.core.focus.setFocusSchedulingEnabled
import neth.iecal.questphone.core.focus.setStrangerMode
import neth.iecal.questphone.core.sync.SyncState
import neth.iecal.questphone.core.sync.SyncViewModel
import javax.inject.Inject

@HiltViewModel
class LauncherSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    fun getHiddenPackages(): Set<String> = userRepository.getHiddenPackages()
    fun unhidePackage(pkg: String) {
        userRepository.unhidePackage(pkg)
        val blocked = userRepository.getBlockedPackages().toMutableSet()
        blocked.remove(pkg)
        userRepository.updateBlockedAppsSet(blocked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSettingsScreen(
    navController: NavController,
    settingsVm: LauncherSettingsViewModel = hiltViewModel(),
    syncVm: SyncViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val serverRunning by syncVm.serverRunning.collectAsState()
    val syncState by syncVm.syncState.collectAsState()
    val deviceIp by syncVm.deviceIp.collectAsState()
    var targetIp by remember { mutableStateOf("") }
    var hiddenPackages = remember { mutableStateOf(settingsVm.getHiddenPackages()) }
    var showUnhideDialog by remember { mutableStateOf("") }

    var focusEnabled by remember { mutableStateOf(context.isFocusSchedulingEnabled()) }
    var strangerMode by remember { mutableStateOf(context.isStrangerMode()) }
    var jarvisActive by remember { mutableStateOf(false) }

    if (showUnhideDialog.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showUnhideDialog = "" },
            title = { Text("Unhide App") },
            text = { Text("Unhide $showUnhideDialog? It will reappear in the launcher.") },
            confirmButton = {
                TextButton(onClick = {
                    settingsVm.unhidePackage(showUnhideDialog)
                    hiddenPackages.value = settingsVm.getHiddenPackages()
                    showUnhideDialog = ""
                }) { Text("Unhide") }
            },
            dismissButton = { TextButton(onClick = { showUnhideDialog = "" }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Launcher Settings", fontWeight = FontWeight.Bold) },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ── Focus Mode ───────────────────────────────────────────
            item {
                SettingCard(title = "⚔️ Focus Gatekeeper", subtitle = "Mon–Fri 07:50–16:00 · Blocks non-study apps") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Auto-schedule Focus", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text("PhysicsWallah always allowed", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = focusEnabled,
                            onCheckedChange = {
                                focusEnabled = it
                                context.setFocusSchedulingEnabled(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE040FB), checkedTrackColor = Color(0xFFE040FB).copy(alpha = 0.3f))
                        )
                    }
                }
            }

            // ── Stranger Mode ────────────────────────────────────────
            item {
                SettingCard(title = "🕵️ Stranger Mode", subtitle = "Shows only whitelisted apps in drawer") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Stranger Mode", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Configure whitelist in Study Apps", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = strangerMode,
                            onCheckedChange = {
                                strangerMode = it
                                context.setStrangerMode(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00BCD4), checkedTrackColor = Color(0xFF00BCD4).copy(alpha = 0.3f))
                        )
                    }
                }
            }

            // ── Jarvis ───────────────────────────────────────────────
            item {
                SettingCard(title = "🤖 Jarvis Voice", subtitle = "Say 'Hello Jarvis' to trigger Google Assistant") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Jarvis Listener", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Activates mic when launcher visible", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = jarvisActive,
                            onCheckedChange = { on ->
                                jarvisActive = on
                                val intent = Intent(context, JarvisListenerService::class.java).apply {
                                    action = if (on) ACTION_START else ACTION_STOP
                                }
                                context.startService(intent)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50), checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.3f))
                        )
                    }
                }
            }

            // ── WiFi Sync ────────────────────────────────────────────
            item {
                SettingCard(title = "📡 WiFi Sync", subtitle = "Sync data between devices on same network") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (serverRunning) "● Running on $deviceIp:45678" else "○ Server Stopped",
                                color = if (serverRunning) Color(0xFF4CAF50) else Color.Gray, fontSize = 13.sp)
                            TextButton(onClick = { if (serverRunning) syncVm.stopServer() else syncVm.startServer() }) {
                                Text(if (serverRunning) "Stop" else "Start")
                            }
                        }
                        OutlinedTextField(value = targetIp, onValueChange = { targetIp = it },
                            label = { Text("Target Device IP") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(onClick = { syncVm.syncFrom(targetIp.trim()) },
                                enabled = targetIp.isNotBlank(), modifier = Modifier.weight(1f)) { Text("⬇ Pull") }
                            androidx.compose.material3.Button(onClick = { syncVm.pushTo(targetIp.trim()) },
                                enabled = targetIp.isNotBlank(), modifier = Modifier.weight(1f)) { Text("⬆ Push") }
                        }
                        val msg = when (val s = syncState) {
                            is SyncState.Success -> s.message
                            is SyncState.Error   -> s.message
                            is SyncState.Syncing -> "Syncing..."
                            else -> ""
                        }
                        if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // ── Hidden Apps ──────────────────────────────────────────
            item {
                SettingCard(title = "🙈 Hidden Apps", subtitle = "Long-press any app in drawer to hide it") {
                    if (hiddenPackages.value.isEmpty()) {
                        Text("No hidden apps.", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
            items(hiddenPackages.value.toList()) { pkg ->
                val appName = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { pkg }
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF111111))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(10.dp))
                    .clickable { showUnhideDialog = pkg }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(appName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(pkg, fontSize = 10.sp, color = Color.Gray)
                    }
                    Text("Tap to unhide", fontSize = 11.sp, color = Color(0xFF4CAF50))
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF0D0D0D))
        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
        .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        content()
    }
}
