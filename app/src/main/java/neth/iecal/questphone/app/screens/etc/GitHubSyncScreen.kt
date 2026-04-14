package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.core.sync.GitHubSyncManager
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class GitHubSyncViewModel @Inject constructor(
    val mgr: GitHubSyncManager
) : ViewModel() {
    val syncState = mgr.syncState
    val lastSync = mgr.lastSync
    fun configure(token: String, repo: String) = mgr.configure(token, repo)
    fun push() = viewModelScope.launch { mgr.push() }
    fun pull() = viewModelScope.launch { mgr.pull() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubSyncScreen(
    navController: NavController,
    vm: GitHubSyncViewModel = hiltViewModel()
) {
    val syncState by vm.syncState.collectAsState()
    val lastSync  by vm.lastSync.collectAsState()

    var token    by remember { mutableStateOf(vm.mgr.getToken()) }
    var repo     by remember { mutableStateOf(vm.mgr.getRepo()) }
    var showToken by remember { mutableStateOf(false) }
    var saved    by remember { mutableStateOf(vm.mgr.isConfigured()) }

    val fmt = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
    val lastSyncStr = if (lastSync > 0L) fmt.format(Date(lastSync)) else "Never"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Sync", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── How it works ──────────────────────────────────────────
            SyncCard(title = "ℹ️ How it works") {
                Text(
                    "Your data is stored as a single JSON file in a GitHub repo you own. " +
                    "Auto-syncs after every quest completion. If offline, syncs when internet returns. " +
                    "Restore is blocked if data is older than 30 days.",
                    fontSize = 13.sp, color = Color(0xFFAAAAAA)
                )
            }

            // ── Setup ─────────────────────────────────────────────────
            SyncCard(title = "🔑 GitHub Configuration") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "1. Create a private GitHub repo (e.g. my-questphone-backup)\n" +
                        "2. Generate a Personal Access Token with 'repo' scope at github.com/settings/tokens",
                        fontSize = 12.sp, color = Color.Gray
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; saved = false },
                        label = { Text("Personal Access Token") },
                        visualTransformation = if (showToken) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showToken = !showToken }) {
                                Text(if (showToken) "Hide" else "Show", fontSize = 11.sp)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it; saved = false },
                        label = { Text("Repo (owner/repo-name)") },
                        placeholder = { Text("krishna000627/questphone-backup") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            vm.configure(token, repo)
                            saved = true
                        },
                        enabled = token.isNotBlank() && repo.contains("/"),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("💾 Save Configuration") }

                    if (saved) {
                        Text("✅ Configured — auto-sync is active", fontSize = 12.sp, color = Color(0xFF4CAF50))
                    }
                }
            }

            // ── Manual sync ───────────────────────────────────────────
            if (saved) {
                SyncCard(title = "🔄 Manual Sync") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.push() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) { Text("⬆ Push Now") }
                            Button(
                                onClick = { vm.pull() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                            ) { Text("⬇ Pull & Restore") }
                        }

                        // Status
                        val (statusColor, statusText) = when (val s = syncState) {
                            is GitHubSyncManager.SyncStatus.Idle    -> Color.Gray to "Waiting for changes…"
                            is GitHubSyncManager.SyncStatus.Syncing -> Color(0xFFFFCA28) to s.msg
                            is GitHubSyncManager.SyncStatus.Success -> Color(0xFF4CAF50) to s.message
                            is GitHubSyncManager.SyncStatus.Error   -> Color(0xFFEF5350) to "⚠ ${s.message}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("●", fontSize = 10.sp, color = statusColor)
                            Text(statusText, fontSize = 12.sp, color = statusColor)
                        }

                        Text("Last sync: $lastSyncStr", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // ── Warning ───────────────────────────────────────────────
            SyncCard(title = "⚠️ Pull Warning") {
                Text(
                    "Pull overwrites your local data with the remote backup. " +
                    "Only use Pull on a freshly installed device or after data loss. " +
                    "Data older than 30 days cannot be restored.",
                    fontSize = 12.sp, color = Color(0xFFFF6D00)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SyncCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        content()
    }
}
