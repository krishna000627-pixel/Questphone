package neth.iecal.questphone.app.screens.onboard.subscreens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Onboarding step: user pastes their Google AI Studio API key.
 * Skip is allowed — Kai just won't work until key is added later in Settings.
 */
@Composable
fun ApiKeyOnboardScreen(
    apiKey: MutableState<String>
) {
    val context = LocalContext.current
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", fontSize = 52.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "Meet Kai",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Kai is your AI companion. He creates quests, tracks your progress, and keeps you accountable.\n\nPaste your Google AI Studio key to activate Kai. It's free.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = apiKey.value,
            onValueChange = { apiKey.value = it.trim() },
            label = { Text("Google AI Studio API Key") },
            placeholder = { Text("AIza...") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Hide" else "Show", fontSize = 11.sp)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://aistudio.google.com/app/apikey")))
            }
        ) { Text("Get a free key at aistudio.google.com →") }

        Spacer(Modifier.height(8.dp))
        Text(
            "Key is stored locally only. Never sent to any server except Google AI Studio.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You can skip this and add it later in Settings → Kai.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}
