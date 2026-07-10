package neth.iecal.questphone.app.screens.locker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neth.iecal.questphone.core.locker.AppLockerManager

class AppLockerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "locked_pkg"
        const val EXTRA_APP_NAME = "locked_app_name"

        fun launch(ctx: Context, pkg: String, appName: String) {
            val intent = Intent(ctx, AppLockerActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, pkg)
                putExtra(EXTRA_APP_NAME, appName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ctx.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: pkg

        setContent {
            AppLockerPinScreen(
                appName = appName,
                onCorrectPin = {
                    // Set bypass so AppBlockerService won't re-intercept
                    neth.iecal.questphone.core.locker.AppLockerManager.setLockerLaunch(applicationContext)
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                    finish()
                },
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
fun AppLockerPinScreen(
    appName: String,
    onCorrectPin: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val correctPin = AppLockerManager.getPin(ctx)

    LaunchedEffect(entered) {
        error = false
        if (entered.length == correctPin.length && correctPin.isNotBlank()) {
            if (entered == correctPin) {
                onCorrectPin()
            } else {
                error = true
                entered = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                appName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Enter PIN to unlock",
                fontSize = 13.sp,
                color = Color(0xFF666666)
            )
        }

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(correctPin.length.coerceAtLeast(4)) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                error -> Color(0xFFE53935)
                                i < entered.length -> Color.White
                                else -> Color(0xFF333333)
                            }
                        )
                )
            }
        }

        // Number pad
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(72.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A1A1A))
                                    .border(1.dp, Color(0xFF2A2A2A), CircleShape)
                                    .clickable {
                                        if (key == "⌫") {
                                            if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                        } else {
                                            if (entered.length < 8) entered += key
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    key,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Cancel",
            fontSize = 14.sp,
            color = Color(0xFF555555),
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(16.dp)
        )
    }
}
