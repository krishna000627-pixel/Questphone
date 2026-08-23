package neth.iecal.questphone.backed.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun UpdateBanner(
    update: UpdateInfo,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A2E))
            .padding(12.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "⬆️ Update Available — v${update.versionName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFFFAB40)
                    )
                    if (update.changelog.isNotBlank()) {
                        Text(
                            update.changelog,
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E),
                            maxLines = 2
                        )
                    }
                }
                TextButton(onClick = {
                    AppUpdater.skipVersion(ctx, update.versionCode)
                    AppUpdater.clearCache(ctx)
                    onDismiss()
                }) {
                    Text("Skip", fontSize = 11.sp, color = Color(0xFF616161))
                }
            }

            Spacer(Modifier.height(8.dp))

            if (downloading) {
                Column {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFFAB40),
                        trackColor = Color(0xFF2A2A2A)
                    )
                    Text(
                        "Downloading... $progress%",
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Button(
                    onClick = {
                        downloading = true
                        scope.launch {
                            try {
                                AppUpdater.downloadAndInstall(ctx, update.downloadUrl) { p ->
                                    progress = p
                                }
                            } catch (_: Exception) {
                                downloading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFAB40))
                ) {
                    Text("Download & Install", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
