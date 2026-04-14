package neth.iecal.questphone.app.screens.etc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

@Composable
fun PomodoroCard() {
    // Config
    val focusMins    = 25
    val shortBreak   = 5
    val longBreak    = 15
    val cyclesBeforeLong = 4

    var phase      by remember { mutableStateOf(PomodoroPhase.FOCUS) }
    var cycleCount by remember { mutableIntStateOf(0) }
    var running    by remember { mutableStateOf(false) }
    var totalSecs  by remember { mutableIntStateOf(focusMins * 60) }
    var secsLeft   by remember { mutableIntStateOf(focusMins * 60) }

    fun phaseColor() = when (phase) {
        PomodoroPhase.FOCUS       -> Color(0xFFE040FB)
        PomodoroPhase.SHORT_BREAK -> Color(0xFF00BCD4)
        PomodoroPhase.LONG_BREAK  -> Color(0xFF4CAF50)
    }
    fun phaseLabel() = when (phase) {
        PomodoroPhase.FOCUS       -> "⚔️ Focus"
        PomodoroPhase.SHORT_BREAK -> "☕ Short Break"
        PomodoroPhase.LONG_BREAK  -> "🌙 Long Break"
    }
    fun nextPhase() {
        if (phase == PomodoroPhase.FOCUS) {
            cycleCount++
            if (cycleCount % cyclesBeforeLong == 0) {
                phase = PomodoroPhase.LONG_BREAK
                totalSecs = longBreak * 60
            } else {
                phase = PomodoroPhase.SHORT_BREAK
                totalSecs = shortBreak * 60
            }
        } else {
            phase = PomodoroPhase.FOCUS
            totalSecs = focusMins * 60
        }
        secsLeft = totalSecs
        running = false
    }

    // Countdown tick
    LaunchedEffect(running, secsLeft) {
        if (running && secsLeft > 0) {
            delay(1000)
            secsLeft--
        } else if (running && secsLeft == 0) {
            nextPhase()
        }
    }

    val progress = if (totalSecs > 0) secsLeft.toFloat() / totalSecs else 0f
    val animProgress by animateFloatAsState(progress, tween(900, easing = LinearEasing), label = "p")

    // Pulse when running
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "ps"
    )

    val mins = secsLeft / 60
    val secs = secsLeft % 60
    val timeStr = "%02d:%02d".format(mins, secs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, phaseColor().copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Phase label
            Text(phaseLabel(), fontSize = 12.sp, color = phaseColor(), letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            // Big timer circle
            Box(
                modifier = Modifier
                    .scale(if (running) pulseScale else 1f)
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(phaseColor().copy(alpha = 0.18f), Color.Transparent)
                        )
                    )
                    .border(2.dp, phaseColor().copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeStr, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        "cycle ${cycleCount + 1}",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                color = phaseColor(),
                trackColor = phaseColor().copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(14.dp))

            // Controls
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Start / Pause
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (running) Color(0xFF1A1A1A) else phaseColor())
                        .clickable { running = !running }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (running) "⏸ Pause" else "▶ Start",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (running) phaseColor() else Color.Black
                    )
                }
                // Reset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                        .clickable {
                            running = false
                            secsLeft = totalSecs
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↺ Reset", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
                // Skip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
                        .clickable { nextPhase() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏭ Skip", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "25 min focus · 5 min break · long break every $cyclesBeforeLong cycles",
                fontSize = 10.sp,
                color = Color(0xFF444444)
            )
        }
    }
}
