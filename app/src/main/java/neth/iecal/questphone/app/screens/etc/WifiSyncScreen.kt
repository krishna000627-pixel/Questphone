package neth.iecal.questphone.app.screens.etc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import neth.iecal.questphone.R
import neth.iecal.questphone.core.sync.SyncState
import neth.iecal.questphone.core.sync.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSyncScreen(
    navController: NavController,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val serverRunning by viewModel.serverRunning.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val deviceIp by viewModel.deviceIp.collectAsState()
    var targetIp by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_info_24),
                            contentDescription = "Back"
                        )
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // -- This Device ------------------------------------------
            SectionCard(title = "This Device") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                "Server Status",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                if (serverRunning) "● Running" else "○ Stopped",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (serverRunning) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                        Button(
                            onClick = {
                                if (serverRunning) viewModel.stopServer()
                                else viewModel.startServer()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serverRunning) Color(0xFF333333) else Color(0xFF1A1A1A),
                                contentColor = if (serverRunning) Color(0xFFFF5252) else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (serverRunning) "Stop Server" else "Start Server")
                        }
                    }

                    if (serverRunning) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF111111))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📡 ", fontSize = 16.sp)
                            Column {
                                Text("Your IP Address", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    "$deviceIp:45678",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF80CBC4)
                                )
                            }
                        }
                        Text(
                            "Share this IP with your other device",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // -- Other Device -----------------------------------------
            SectionCard(title = "Other Device") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("Target IP (e.g. 192.168.1.5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.syncFrom(targetIp.trim()) },
                            enabled = targetIp.isNotBlank() && syncState !is SyncState.Syncing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Text("⬇ Pull from device")
                        }
                        Button(
                            onClick = { viewModel.pushTo(targetIp.trim()) },
                            enabled = targetIp.isNotBlank() && syncState !is SyncState.Syncing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4527A0))
                        ) {
                            Text("⬆ Push to device")
                        }
                    }
                }
            }

            // -- Status -----------------------------------------------
            AnimatedContent(targetState = syncState, label = "sync_status") { state ->
                when (state) {
                    is SyncState.Idle -> {}
                    is SyncState.Syncing -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Syncing...", color = Color.White)
                    }
                    is SyncState.Success -> StatusBanner(state.message, Color(0xFF2E7D32))
                    is SyncState.Error   -> StatusBanner(state.message, Color(0xFFC62828))
                }
            }

            Spacer(Modifier.height(8.dp))

            // -- How to use -------------------------------------------
            SectionCard(title = "How to use") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HowToStep("1", "Both phones must be on the same WiFi")
                    HowToStep("2", "On the receiving phone: tap Start Server")
                    HowToStep("3", "On the sending phone: enter the IP shown above")
                    HowToStep("4", "Tap Pull (to receive data) or Push (to send data)")
                    HowToStep("5", "Newer data always wins — safe to sync both ways")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0D0D0D))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
        content()
    }
}

@Composable
private fun StatusBanner(message: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(message, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HowToStep(step: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, fontSize = 10.sp, color = Color.Gray)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFFBBBBBB))
    }
}
