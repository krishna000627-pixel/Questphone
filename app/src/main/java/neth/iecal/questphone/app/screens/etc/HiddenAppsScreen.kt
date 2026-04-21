package neth.iecal.questphone.app.screens.etc

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import neth.iecal.questphone.R
import neth.iecal.questphone.core.services.AppBlockerService
import neth.iecal.questphone.core.services.INTENT_ACTION_REFRESH_APP_BLOCKER

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsScreen(
    navController: NavController,
    settingsVm: LauncherSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hiddenPackages by remember { mutableStateOf(settingsVm.getHiddenPackages()) }
    var showUnhideDialog by remember { mutableStateOf("") }

    if (showUnhideDialog.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showUnhideDialog = "" },
            title = { Text("Unhide App") },
            text = {
                val name = try { context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(showUnhideDialog, 0)).toString()
                } catch (_: Exception) { showUnhideDialog }
                Text("Show \"$name\" in the launcher again?")
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsVm.unhidePackage(showUnhideDialog)
                    hiddenPackages = settingsVm.getHiddenPackages()
                    // Reload AppBlockerService so change takes effect without force-stop
                    context.sendBroadcast(Intent(INTENT_ACTION_REFRESH_APP_BLOCKER))
                    showUnhideDialog = ""
                }) { Text("Unhide", color = Color(0xFF4CAF50)) }
            },
            dismissButton = {
                TextButton(onClick = { showUnhideDialog = "" }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🙈 Hidden Apps", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Long-press any app in the drawer to hide it. Hidden apps don't appear in the launcher but are not blocked.",
                    fontSize = 12.sp, color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (hiddenPackages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No hidden apps yet.", color = Color.Gray)
                    }
                }
            }

            items(hiddenPackages.toList()) { pkg ->
                val appName = try {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (_: Exception) { pkg }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                        .clickable { showUnhideDialog = pkg }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(pkg, fontSize = 10.sp, color = Color(0xFF555555))
                    }
                    Text("Tap to unhide", fontSize = 11.sp, color = Color(0xFF4CAF50))
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
