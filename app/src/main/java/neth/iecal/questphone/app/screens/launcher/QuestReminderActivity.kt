package neth.iecal.questphone.app.screens.launcher

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neth.iecal.questphone.app.theme.LauncherTheme
import neth.iecal.questphone.app.theme.customThemes.PitchBlackTheme

/**
 * A translucent overlay shown for 10 seconds when the user opens any app.
 * Shows the quest that is active right now (passed via Intent).
 * Tapping anywhere dismisses it immediately and the target app opens.
 */
class QuestReminderActivity : ComponentActivity() {

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val questTitle = intent.getStringExtra(EXTRA_QUEST_TITLE) ?: "No active quest"
        val questTime  = intent.getStringExtra(EXTRA_QUEST_TIME)  ?: ""
        val targetPkg  = intent.getStringExtra(EXTRA_TARGET_PKG)

        setContent {
            var progress by remember { mutableFloatStateOf(1f) }
            var secondsLeft by remember { mutableStateOf(10) }

            timer = object : CountDownTimer(10_000, 100) {
                override fun onTick(ms: Long) {
                    progress = ms / 10_000f
                    secondsLeft = (ms / 1000).toInt() + 1
                }
                override fun onFinish() {
                    launchTarget(targetPkg)
                }
            }.start()

            LauncherTheme(PitchBlackTheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f))
                        .clickable { launchTarget(targetPkg) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0D0D0D))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("⚔️ Active Quest", fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.sp)

                        Text(
                            questTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        if (questTime.isNotEmpty()) {
                            Text(
                                "🕐 $questTime",
                                fontSize = 13.sp,
                                color = Color(0xFF80CBC4)
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            color = Color(0xFFE091FF),
                            trackColor = Color(0xFF222222),
                            strokeCap = StrokeCap.Round
                        )

                        Text(
                            "Opening in $secondsLeft s...",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }

    private fun launchTarget(pkg: String?) {
        timer?.cancel()
        if (!pkg.isNullOrEmpty()) {
            try {
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                }
            } catch (_: Exception) {}
        }
        finish()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_QUEST_TITLE = "quest_title"
        const val EXTRA_QUEST_TIME  = "quest_time"
        const val EXTRA_TARGET_PKG  = "target_pkg"
    }
}
