package neth.iecal.questphone.app.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.sync.RenderSyncManager
import neth.iecal.questphone.backed.sync.RenderSyncPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@HiltViewModel
class RenderSyncViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()

    fun pushNow(ctx: android.content.Context) {
        viewModelScope.launch {
            _isWorking.value = true
            _status.value = "Pushing..."
            _status.value = when (val r = RenderSyncManager.autoPush(ctx, userRepository, questRepository)) {
                is RenderSyncManager.SyncResult.Success -> "✅ Pushed successfully"
                is RenderSyncManager.SyncResult.Error -> "❌ ${r.message}"
                is RenderSyncManager.SyncResult.Disabled -> "⚠️ Sync is disabled"
            }
            _isWorking.value = false
        }
    }

    fun pullNow(ctx: android.content.Context) {
        viewModelScope.launch {
            _isWorking.value = true
            _status.value = "Pulling from server..."
            _status.value = when (val r = RenderSyncManager.pull(ctx, userRepository, questRepository)) {
                is RenderSyncManager.SyncResult.Success -> "✅ Restored from server"
                is RenderSyncManager.SyncResult.Error -> "❌ ${r.message}"
                is RenderSyncManager.SyncResult.Disabled -> "⚠️ Sync is disabled"
            }
            _isWorking.value = false
        }
    }

    fun testConnection(ctx: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isWorking.value = true
            _status.value = "Testing..."
            _status.value = when (val r = RenderSyncManager.testConnection(RenderSyncPrefs.SERVER_URL, RenderSyncPrefs.SYNC_TOKEN)) {
                is RenderSyncManager.SyncResult.Success -> "✅ Server reachable"
                is RenderSyncManager.SyncResult.Error -> "❌ ${r.message}"
                else -> null
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
    val clipboard = LocalClipboardManager.current
    val status by vm.status.collectAsState()
    val isWorking by vm.isWorking.collectAsState()

    var enabled by remember { mutableStateOf(RenderSyncPrefs.isEnabled(ctx)) }
    var ownCode by remember { mutableStateOf(RenderSyncPrefs.getOwnId(ctx)) }
    var activeCode by remember { mutableStateOf(RenderSyncPrefs.getUserId(ctx)) }
    var isLinked by remember { mutableStateOf(RenderSyncPrefs.isLinked(ctx)) }
    var linkInput by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf("") }
    var linkSuccess by remember { mutableStateOf(false) }
    var codeCopied by remember { mutableStateOf(false) }
    var showLinkConfirm by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var showQrCode by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    val lastSync = RenderSyncPrefs.getLastSyncAt(ctx)
    val lastSyncStr = if (lastSync == 0L) "Never"
    else SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(lastSync))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-Sync", fontWeight = FontWeight.Bold) },
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

            // ── Status toggle ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111111))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (enabled) "● Auto-Sync ON" else "○ Auto-Sync OFF",
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) Color(0xFF69F0AE) else Color(0xFF666666)
                    )
                    Text("Last sync: $lastSyncStr", fontSize = 12.sp, color = Color(0xFF666666))
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; RenderSyncPrefs.setEnabled(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF1565C0))
                )
            }

            // ── Push / Pull / Test ─────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.pushNow(ctx) }, enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) { Text("⬆ Push Now") }
                Button(
                    onClick = { vm.pullNow(ctx) }, enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))
                ) { Text("⬇ Pull Now") }
            }
            OutlinedButton(onClick = { vm.testConnection(ctx) }, enabled = !isWorking,
                modifier = Modifier.fillMaxWidth()) { Text("🔌 Test Connection") }

            if (isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())

            status?.let {
                Surface(
                    color = if (it.startsWith("✅")) Color(0xFF1B5E20) else Color(0xFF311B1B),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, Modifier.padding(14.dp),
                        color = if (it.startsWith("✅")) Color(0xFF69F0AE) else Color(0xFFEF9A9A))
                }
            }

            HorizontalDivider(color = Color(0xFF222222))

            // ── My Sync Code ───────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111111)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("My Sync Code", fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (isLinked) Surface(color = Color(0xFF1565C0), shape = RoundedCornerShape(6.dp)) {
                        Text("LINKED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Text(
                    "Share your code with another device to sync together.",
                    fontSize = 12.sp, color = Color(0xFF666666)
                )

                // Own code — always shown
                Text("Your code", fontSize = 11.sp, color = Color(0xFF666666))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A0A0A))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .clickable { clipboard.setText(AnnotatedString(ownCode)); codeCopied = true }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ownCode, fontFamily = FontFamily.Monospace, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6), letterSpacing = 3.sp)
                    Text(if (codeCopied) "Copied ✓" else "Tap to copy", fontSize = 12.sp,
                        color = if (codeCopied) Color(0xFF69F0AE) else Color(0xFF666666))
                }

                // QR actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showQrCode = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64B5F6))
                    ) { Text("Show QR", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { showQrScanner = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64B5F6))
                    ) { Text("Scan QR", fontSize = 13.sp) }
                }

                // Active slot when linked
                if (isLinked) {
                    Text("Active slot (linked)", fontSize = 11.sp, color = Color(0xFF666666))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0A0A0A))
                            .border(1.dp, Color(0xFF1565C0), RoundedCornerShape(8.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(activeCode, fontFamily = FontFamily.Monospace, fontSize = 20.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFF81C784), letterSpacing = 3.sp)
                        TextButton(onClick = { showUnlinkConfirm = true }) {
                            Text("Unlink", color = Color(0xFFEF9A9A), fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Link Another Device ────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111111)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Link a Device", fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Enter the sync code from your other device to share the same data slot.",
                    fontSize = 12.sp, color = Color(0xFF666666))
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = {
                        linkInput = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12)
                        linkError = ""; linkSuccess = false
                    },
                    label = { Text("Enter sync code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 18.sp, letterSpacing = 3.sp
                    )
                )
                if (linkError.isNotBlank()) Text(linkError, color = Color(0xFFEF9A9A), fontSize = 12.sp)
                if (linkSuccess) Text("✅ Linked! Tap Pull Now to get the data.",
                    color = Color(0xFF69F0AE), fontSize = 12.sp)
                Button(
                    onClick = {
                        when {
                            linkInput.length < 8 -> linkError = "Code must be at least 8 characters"
                            linkInput == ownCode -> linkError = "That's your own code"
                            linkInput == activeCode -> linkError = "Already linked to this code"
                            else -> showLinkConfirm = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) { Text("Link Device") }
            }

            HorizontalDivider(color = Color(0xFF222222))

            // ── Profile Settings shortcut ──────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
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
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // QR code display dialog
    if (showQrCode) {
        val qrBitmap = remember(ownCode) {
            try {
                val writer = QRCodeWriter()
                val hints = mapOf(EncodeHintType.MARGIN to 1)
                val matrix = writer.encode("QP:$ownCode", BarcodeFormat.QR_CODE, 512, 512, hints)
                val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
                for (x in 0 until 512) for (y in 0 until 512)
                    bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                bmp
            } catch (_: Exception) { null }
        }
        Dialog(onDismissRequest = { showQrCode = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF111111),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("My Sync QR", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    qrBitmap?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Text(
                        ownCode,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64B5F6),
                        letterSpacing = 3.sp
                    )
                    Text("Let the other device scan this", fontSize = 12.sp, color = Color(0xFF666666))
                    TextButton(onClick = { showQrCode = false }) { Text("Close") }
                }
            }
        }
    }

    // QR scanner
    if (showQrScanner) {
        Dialog(onDismissRequest = { showQrScanner = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black,
                modifier = Modifier.fillMaxWidth().height(520.dp)
            ) {
                QrScanScreen(
                    onCodeScanned = { code ->
                        showQrScanner = false
                        linkInput = code
                        when {
                            code == ownCode -> { /* own code — ignore */ }
                            code == activeCode -> { /* already linked */ }
                            else -> {
                                RenderSyncPrefs.linkDevice(ctx, code)
                                activeCode = code
                                isLinked = true
                                linkSuccess = true
                                vm.pullNow(ctx)
                            }
                        }
                    },
                    onBack = { showQrScanner = false }
                )
            }
        }
    }

    // Link confirm
    if (showLinkConfirm) {
        AlertDialog(
            onDismissRequest = { showLinkConfirm = false },
            title = { Text("Link Device?") },
            text = { Text("Your sync slot will switch to \"$linkInput\". Tap Pull Now after linking to get that device's data. Your current data here will be overwritten on pull.") },
            confirmButton = {
                TextButton(onClick = {
                    RenderSyncPrefs.linkDevice(ctx, linkInput)
                    activeCode = linkInput
                    isLinked = true
                    linkInput = ""
                    linkSuccess = true
                    showLinkConfirm = false
                    vm.pullNow(ctx)
                }) { Text("Link", color = Color(0xFF64B5F6)) }
            },
            dismissButton = {
                TextButton(onClick = { showLinkConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Unlink confirm
    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text("Unlink?") },
            text = { Text("You'll go back to your own sync slot ($ownCode).") },
            confirmButton = {
                TextButton(onClick = {
                    RenderSyncPrefs.unlink(ctx)
                    activeCode = ownCode
                    isLinked = false
                    showUnlinkConfirm = false
                }) { Text("Unlink", color = Color(0xFFEF9A9A)) }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
