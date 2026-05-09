package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.sync.FreeSyncClient
import neth.iecal.questphone.core.sync.SyncResult
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _status = MutableStateFlow<String>("")
    val status = _status.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun push(ctx: android.content.Context) {
        viewModelScope.launch {
            _loading.value = true
            _status.value = "Pushing..."
            val result = FreeSyncClient.push(ctx, userRepository.userInfo)
            _status.value = when (result) {
                is SyncResult.Success -> "✅ ${result.message}"
                is SyncResult.Error   -> "❌ ${result.message}"
                else                  -> "Sync disabled"
            }
            _loading.value = false
        }
    }

    fun pull(ctx: android.content.Context) {
        viewModelScope.launch {
            _loading.value = true
            _status.value = "Pulling..."
            val (result, data) = FreeSyncClient.pull(ctx)
            when (result) {
                is SyncResult.Success -> {
                    if (data != null) {
                        // Merge: keep local coins if higher
                        val merged = data.copy(
                            coins = maxOf(data.coins, userRepository.userInfo.coins)
                        )
                        // userRepository.overwriteUserInfo(merged)
                        _status.value = "✅ Pulled successfully"
                    }
                }
                is SyncResult.Error -> _status.value = "❌ ${result.message}"
                else -> _status.value = "Sync disabled"
            }
            _loading.value = false
        }
    }

    fun deleteRemote(ctx: android.content.Context) {
        viewModelScope.launch {
            _loading.value = true
            val result = FreeSyncClient.deleteRemote(ctx)
            _status.value = when (result) {
                is SyncResult.Success -> "✅ ${result.message}"
                is SyncResult.Error   -> "❌ ${result.message}"
                else -> ""
            }
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(navController: NavController, vm: CloudSyncViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val loading by vm.loading.collectAsState()

    var email    by remember { mutableStateOf(FreeSyncClient.getSyncEmail(context)) }
    var password by remember { mutableStateOf("") }
    var server   by remember { mutableStateOf(FreeSyncClient.getServer(context)) }
    val enabled = FreeSyncClient.isEnabled(context)
    val lastSync = FreeSyncClient.getLastSyncTime(context)
    val lastSyncStr = if (lastSync > 0)
        SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(lastSync))
    else "Never"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync", fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // How it works
            SyncInfoCard()

            // Config
            Column(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0D0D0D))
                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
                .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Account", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

                OutlinedTextField(value = email, onValueChange = { email = it },
                    label = { Text("Gmail / Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp))

                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp))

                OutlinedTextField(value = server, onValueChange = { server = it },
                    label = { Text("Server URL (e.g. https://mysite.epizy.com)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp))

                Button(onClick = {
                    FreeSyncClient.saveConfig(context, email, password, server)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save Config")
                }

                if (enabled) {
                    Text("Last sync: $lastSyncStr", fontSize = 11.sp, color = Color.Gray)
                }
            }

            // Actions
            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.push(context) },
                        enabled = !loading, modifier = Modifier.weight(1f)) {
                        Text("⬆ Push")
                    }
                    Button(onClick = { vm.pull(context) },
                        enabled = !loading, modifier = Modifier.weight(1f)) {
                        Text("⬇ Pull")
                    }
                }
                OutlinedButton(onClick = { vm.deleteRemote(context) },
                    enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text("🗑 Delete from Server", color = Color(0xFFEF5350))
                }
            }

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (status.isNotEmpty()) {
                Text(status, fontSize = 13.sp,
                    color = if (status.startsWith("✅")) Color(0xFF4CAF50) else Color(0xFFEF5350))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SyncInfoCard() {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF0D1A0D))
        .border(1.dp, Color(0xFF1A3A1A), RoundedCornerShape(14.dp))
        .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("☁️ How Cloud Sync Works", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text("• Upload sync.php to any free PHP host (InfinityFree, ezyro, etc.)", fontSize = 12.sp, color = Color.Gray)
        Text("• Enter your email, a password, and the server URL above", fontSize = 12.sp, color = Color.Gray)
        Text("• Push to upload · Pull to download on another device", fontSize = 12.sp, color = Color.Gray)
        Text("• Server auto-deletes data after 7 days to save space", fontSize = 12.sp, color = Color.Gray)
        Text("• All data is tied to your email+password only", fontSize = 12.sp, color = Color.Gray)
    }
}
