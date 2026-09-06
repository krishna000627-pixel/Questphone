package neth.iecal.questphone.app.screens.locker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme

@Composable
fun PinGateScreen(
    correctPin: String,
    title: String,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(entered) {
        error = false
        if (entered.length == correctPin.length && correctPin.isNotBlank()) {
            if (entered == correctPin) {
                onUnlocked()
            } else {
                error = true
                entered = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                if (error) "Wrong PIN" else "Enter PIN to continue",
                fontSize = 13.sp,
                color = if (error) Color(0xFFE53935) else Color(0xFF666666)
            )
        }

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(correctPin.length.coerceAtLeast(4)) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(
                            when {
                                error -> Color(0xFFE53935)
                                i < entered.length -> Color.White
                                else -> Color(0xFF2A2A2A)
                            }
                        )
                )
            }
        }

        // Keypad
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) Spacer(Modifier.size(72.dp))
                        else Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A))
                                .border(1.dp, Color(0xFF2A2A2A), CircleShape)
                                .clickable {
                                    if (key == "⌫") {
                                        if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                    } else if (entered.length < 8) {
                                        entered += key
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        Text(
            "Cancel",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.clickable { onCancel() }.padding(16.dp)
        )
    }
}
