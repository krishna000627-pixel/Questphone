package neth.iecal.questphone.app.screens.etc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(navController: NavController) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("Reading...") }
    var copyLabel by remember { mutableStateOf("Copy") }

    LaunchedEffect(Unit) {
        try {
            val file = File(context.filesDir, "crash_log.txt")
            logText = if (file.exists() && file.length() > 0) {
                val content = file.readText()
                // Show last 12000 chars
                if (content.length > 12000) "...(truncated)\n\n" + content.takeLast(12000)
                else content
            } else {
                "No crash log found.\n\nThe app has not crashed yet, or the log was cleared."
            }
        } catch (e: Exception) {
            logText = "Error reading log:\n${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crash Log", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { try { navController.popBackStack() } catch (_: Exception) {} }) {
                        Text("<", color = Color.White, fontSize = 18.sp)
                    }
                },
                actions = {
                    // Copy full log to clipboard
                    TextButton(onClick = {
                        try {
                            val fullLog = File(context.filesDir, "crash_log.txt")
                                .takeIf { it.exists() }?.readText() ?: logText
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", fullLog))
                            copyLabel = "Copied!"
                        } catch (_: Exception) {
                            copyLabel = "Failed"
                        }
                    }) {
                        Text(copyLabel, color = Color(0xFF00BCD4))
                    }
                    TextButton(onClick = {
                        try {
                            File(context.filesDir, "crash_log.txt").delete()
                            logText = "Log cleared."
                        } catch (_: Exception) {}
                    }) {
                        Text("Clear", color = Color(0xFFFF6B6B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (logText == "Reading...") {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF00BCD4)
                )
            } else {
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()
                Text(
                    text = logText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FF88),
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                        .padding(12.dp)
                )
            }
        }
    }
}
