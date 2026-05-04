package neth.iecal.questphone.app.screens.launcher.dialogs

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun UnlockAppDialog(
    coins: Int,
    onDismiss: () -> Unit,
    onConfirm: (coinsSpent: Int) -> Unit,
    pkgName: String,
    minutesPerFiveCoins: Int
) {
    val context = LocalContext.current
    val maxSpendableCoins = coins - (coins % 5)
    var coinsToSpend by remember { mutableIntStateOf(5) }
    var secsLeft by remember { mutableIntStateOf(10) }

    // Countdown — auto-dismiss at 0
    LaunchedEffect(Unit) {
        while (secsLeft > 0) {
            delay(1000)
            secsLeft--
        }
        onDismiss()
    }

    val appName = remember(pkgName) {
        try { context.packageManager.getApplicationInfo(pkgName, 0)
            .loadLabel(context.packageManager).toString() }
        catch (_: Exception) { pkgName }
    }

    // Breathing animation
    val breath = rememberInfiniteTransition(label = "breath")
    val breathScale by breath.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "bs"
    )
    // Countdown ring pulse
    val ring = rememberInfiniteTransition(label = "ring")
    val ringAlpha by ring.animateFloat(
        initialValue = 0.15f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "ra"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp)
        ) {

            // Breathing circle
            Box(
                modifier = Modifier
                    .scale(breathScale)
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF00BCD4).copy(alpha = ringAlpha),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$secsLeft",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Thin,
                    color = Color(0xFF00BCD4)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "PAUSE",
                fontSize = 11.sp,
                color = Color(0xFF00BCD4).copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Do you really want to open\n$appName?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Take a breath. Is this worth your time?",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Light
            )

            Spacer(Modifier.height(40.dp))

            // Coin selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { if (coinsToSpend > 5) coinsToSpend -= 5 },
                    enabled = coinsToSpend > 5
                ) {
                    Text(
                        "−5",
                        fontSize = 20.sp,
                        color = if (coinsToSpend > 5) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.2f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Text(
                        "$coinsToSpend",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Thin,
                        color = Color.White
                    )
                    Text(
                        "coins → ${coinsToSpend / 5 * minutesPerFiveCoins} min",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }

                TextButton(
                    onClick = { if (coinsToSpend + 5 <= maxSpendableCoins) coinsToSpend += 5 },
                    enabled = coinsToSpend + 5 <= maxSpendableCoins
                ) {
                    Text(
                        "+5",
                        fontSize = 20.sp,
                        color = if (coinsToSpend + 5 <= maxSpendableCoins) Color(0xFF00BCD4) else Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Open button
            Button(
                onClick = { onConfirm(coinsToSpend) },
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00BCD4).copy(alpha = 0.15f),
                    contentColor = Color(0xFF00BCD4)
                )
            ) {
                Text(
                    "Open App",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            // Skip / dismiss
            TextButton(onClick = onDismiss) {
                Text(
                    "Skip",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}
