package neth.iecal.questphone.core.focus

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import neth.iecal.questphone.app.theme.LauncherTheme
import neth.iecal.questphone.app.theme.customThemes.PitchBlackTheme

/**
 * Full-screen non-bypassable overlay shown during Focus Mode
 * when a non-study app is opened. Counts down 10 seconds then closes.
 * Non-dismissable — no back button, no tap.
 */
class BacklogWallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent back button
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { /* blocked */ }
            }
        )

        val blockedApp = intent.getStringExtra(EXTRA_BLOCKED_APP) ?: "that app"

        setContent {
            var secsLeft by remember { mutableIntStateOf(10) }

            LaunchedEffect(Unit) {
                while (secsLeft > 0) {
                    delay(1000)
                    secsLeft--
                }
                finish()
            }

            val pulse = rememberInfiniteTransition(label = "p")
            val scale by pulse.animateFloat(
                initialValue = 0.95f, targetValue = 1.05f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "s"
            )

            LauncherTheme(PitchBlackTheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        // Pulsing warning circle
                        Box(
                            modifier = Modifier
                                .scale(scale)
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFFEF5350).copy(alpha = 0.25f), Color.Transparent)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚔️", fontSize = 56.sp)
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            "FOCUS MODE ACTIVE",
                            fontSize = 13.sp,
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Complete your quests\nbefore using apps",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "07:50 – 16:00 · Mon–Fri",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(32.dp))

                        // Countdown
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1A1A))
                                .padding(horizontal = 28.dp, vertical = 12.dp)
                        ) {
                            Text(
                                "Returning in $secsLeft s",
                                fontSize = 16.sp,
                                color = Color(0xFFEF5350),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_BLOCKED_APP = "blocked_app"
    }
}
