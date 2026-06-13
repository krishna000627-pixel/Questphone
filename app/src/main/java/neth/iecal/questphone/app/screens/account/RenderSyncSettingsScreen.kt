package neth.iecal.questphone.app.screens.account

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.sync.RenderSyncManager
import neth.iecal.questphone.backed.sync.RenderSyncPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RenderSyncViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()

    fun testAndSave(ctx: android.content.Context, url: String, token: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isWorking.value = true
            _status.value = "Testing connection..."
            val result = RenderSyncManager.testConnection(url, token)
            when (result) {
                is RenderSyncManager.SyncResult.Success -> {
                    RenderSyncPrefs.save(ctx, url, token, enabled)
                    _status.value = "✅ Connected! Settings saved."
                }
                is RenderSyncManager.SyncResult.Error -> {
                    _status.value = "❌ ${result.message}"
                }
                else -> {}
            }
            _isWorking.value = false
        }
    }

    fun pushNow(ctx: android.content.Context) {
        viewModelScope.launch {
            _isWorking.value = true
            _status.value = "Pushing..."
            val result = RenderSyncManager.autoPush(ctx, userRepository, questRepository)
            _status.value = when (result) {
                is RenderSyncManager.SyncResult.Success -> "✅ Pushed successfully."
                is RenderSyncManager.SyncResult.Error -> "❌ ${result.message}"
                is RenderSyncManager.SyncResult.Disabled -> "⚠️ Sync is disabled."
            }
            _isWorking.value = false
        }
    }

    fun pullNow(ctx: android.content.Context) {
        viewModelScope.launch {
            _isWorking.value = true
            _status.value = "Pulling from server..."
            val result = RenderSyncManager.pull(ctx, userRepository, questRepository)
            _status.value = when (result) {
                is RenderSyncManager.SyncResult.Success -> "✅ Restored from server."
                is RenderSyncManager.SyncResult.Error -> "❌ ${result.message}"
                is RenderSyncManager.SyncResult.Disabled -> "⚠️ Sync is disabled."
            }
            _isWorking.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderSyncSettingsScreen(
    navController: NavController,
    vm: RenderSyncViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val status by vm.status.collectAsState()
    val isWorking by vm.isWorking.collectAsState()

    var serverUrl by remember { mutableStateOf(RenderSyncPrefs.getServerUrl(ctx)) }
    var syncToken by remember { mutableStateOf(RenderSyncPrefs.getSyncToken(ctx)) }
    var enabled by remember { mutableStateOf(RenderSyncPrefs.isEnabled(ctx)) }
    var showGuide by remember { mutableStateOf(serverUrl.isBlank()) }

    val lastSync = RenderSyncPrefs.getLastSyncAt(ctx)
    val lastSyncStr = if (lastSync == 0L) "Never"
    else SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(lastSync))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Render Auto-Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Status chip ────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (enabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        if (enabled) "● AUTO-SYNC ON" else "○ AUTO-SYNC OFF",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (enabled) Color(0xFF69F0AE) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
                Text("Last sync: $lastSyncStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── Setup guide ────────────────────────────────────────
            OutlinedButton(
                onClick = { showGuide = !showGuide },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (showGuide) "Hide Setup Guide" else "📖 Show Setup Guide") }

            AnimatedVisibility(visible = showGuide) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Setup Guide", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)

                        GuideStep("1", "Create GitHub repo",
                            "Go to github.com → New repository → name it: Questphone-Sync → Public → Create")

                        GuideStep("2", "Push server code",
                            "Run in Termux:\n~/push_sync.sh")

                        GuideStep("3", "Create Render Web Service",
                            "Go to dashboard.render.com → New → Web Service → Connect GitHub → Select Questphone-Sync")

                        GuideStep("4", "Render settings",
                            "Runtime: Node\nBuild command: npm install\nStart command: node index.js\nInstance: Free")

                        GuideStep("5", "Add environment variable",
                            "In Render dashboard → Environment → Add:\nKey: SYNC_TOKEN\nValue: any secret string you choose (remember it)")

                        GuideStep("6", "Get your server URL",
                            "After deploy, Render gives you a URL like:\nhttps://questphone-sync.onrender.com\nCopy it and paste below")

                        GuideStep("7", "Enter details below",
                            "Paste server URL and your SYNC_TOKEN below, tap Save & Test")
                    }
                }
            }

            // ── Config fields ──────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Configuration", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://questphone-sync.onrender.com") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = syncToken,
                        onValueChange = { syncToken = it },
                        label = { Text("Sync Token") },
                        placeholder = { Text("Your SYNC_TOKEN env var value") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Auto-sync on every change", fontWeight = FontWeight.Medium)
                            Text("Quests, settings, My Life, people",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }

                    Button(
                        onClick = { vm.testAndSave(ctx, serverUrl, syncToken, enabled) },
                        enabled = !isWorking && serverUrl.isNotBlank() && syncToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save & Test Connection") }
                }
            }

            // ── Manual push/pull ───────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Manual Sync", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { vm.pushNow(ctx) },
                            enabled = !isWorking,
                            modifier = Modifier.weight(1f)
                        ) { Text("⬆ Push Now") }
                        OutlinedButton(
                            onClick = { vm.pullNow(ctx) },
                            enabled = !isWorking,
                            modifier = Modifier.weight(1f)
                        ) { Text("⬇ Pull Now") }
                    }
                    Text("Push = upload your data to server.\nPull = restore data from server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isWorking) {
                Row(Modifier.fillMaxWidth(), Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Working…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            status?.let {
                val isError = it.startsWith("❌")
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, Modifier.padding(14.dp),
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // ── Profile Settings ───────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(neth.iecal.questphone.app.navigation.RootRoute.ProfileSettings.route) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Profile Settings", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge)
                        Text("Edit name, class, skills, stats & more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Text(body,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
