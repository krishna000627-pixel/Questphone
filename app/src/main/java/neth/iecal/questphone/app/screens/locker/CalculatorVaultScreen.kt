package neth.iecal.questphone.app.screens.locker

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import neth.iecal.questphone.core.vault.AppVaultManager

@Composable
fun CalculatorVaultScreen(navController: NavController) {
    val ctx = LocalContext.current
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var lastOperator by remember { mutableStateOf("") }
    var lastValue by remember { mutableStateOf(0.0) }
    var shouldReset by remember { mutableStateOf(false) }
    var rawInput by remember { mutableStateOf("") } // tracks raw digit sequence for secret code

    var showVault by remember { mutableStateOf(false) }
    var showPinPrompt by remember { mutableStateOf(false) }
    var vaultPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val secretCode = remember { AppVaultManager.getSecretCode(ctx) }
    val correctPin = remember { AppVaultManager.getPin(ctx) }

    fun formatResult(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    fun calculate(): Double = try {
        when (lastOperator) {
            "+" -> lastValue + display.toDouble()
            "-" -> lastValue - display.toDouble()
            "×" -> lastValue * display.toDouble()
            "÷" -> if (display.toDouble() != 0.0) lastValue / display.toDouble() else Double.NaN
            else -> display.toDouble()
        }
    } catch (_: Exception) { 0.0 }

    fun onKey(key: String) {
        when (key) {
            "=" -> {
                // Secret code check — just the current display value
                if (secretCode.isNotBlank() && rawInput == secretCode) {
                    rawInput = ""
                    display = "0"
                    expression = ""
                    lastOperator = ""
                    lastValue = 0.0
                    shouldReset = true
                    if (correctPin.isBlank()) {
                        showVault = true
                    } else {
                        showPinPrompt = true
                    }
                    return
                }
                val result = calculate()
                display = formatResult(result)
                expression = ""
                lastOperator = ""
                rawInput = ""
                shouldReset = true
            }
            "AC" -> {
                display = "0"; expression = ""; lastOperator = ""
                lastValue = 0.0; rawInput = ""; shouldReset = false
            }
            "+/-" -> display = if (display.startsWith("-")) display.drop(1) else "-$display"
            "%" -> display = formatResult(display.toDoubleOrNull()?.div(100) ?: 0.0)
            "+", "-", "×", "÷" -> {
                lastValue = display.toDoubleOrNull() ?: 0.0
                lastOperator = key
                expression = display + " $key "
                rawInput = "" // reset on operator
                shouldReset = true
            }
            "." -> {
                if (shouldReset) { display = "0."; shouldReset = false; return }
                if (!display.contains(".")) display += "."
            }
            else -> {
                if (shouldReset) { display = key; rawInput = key; shouldReset = false }
                else {
                    display = if (display == "0") key else (display + key).take(12)
                    rawInput += key
                }
            }
        }
    }

    // PIN prompt
    if (showPinPrompt) {
        LaunchedEffect(vaultPinInput) {
            pinError = false
            if (vaultPinInput.length == correctPin.length) {
                if (vaultPinInput == correctPin) {
                    showPinPrompt = false
                    showVault = true
                } else {
                    pinError = true
                    vaultPinInput = ""
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔐", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("Vault PIN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (pinError) Text("Wrong PIN", fontSize = 13.sp, color = Color(0xFFE53935))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(correctPin.length.coerceAtLeast(4)) { i ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < vaultPinInput.length) Color.White else Color(0xFF333333))
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { k ->
                            if (k.isEmpty()) Spacer(Modifier.size(64.dp))
                            else Box(
                                Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF1A1A1A))
                                    .clickable {
                                        if (k == "⌫") { if (vaultPinInput.isNotEmpty()) vaultPinInput = vaultPinInput.dropLast(1) }
                                        else if (vaultPinInput.length < 8) vaultPinInput += k
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(k, fontSize = 20.sp, color = Color.White) }
                        }
                    }
                }
            }

            Text("Cancel", fontSize = 14.sp, color = Color(0xFF555555),
                modifier = Modifier.clickable { showPinPrompt = false; vaultPinInput = "" }.padding(16.dp))
        }
        return
    }

    // Vault app list
    if (showVault) {
        VaultAppListScreen(onClose = { showVault = false })
        return
    }

    // Calculator UI
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (expression.isNotBlank()) Text(expression, fontSize = 18.sp, color = Color(0xFF666666))
            Text(
                display,
                fontSize = if (display.length > 9) 42.sp else 64.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                listOf("AC", "+/-", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            ).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        val isOperator = key in listOf("÷", "×", "-", "+", "=")
                        val isFunction = key in listOf("AC", "+/-", "%")
                        val isWide = key == "0"
                        Box(
                            modifier = Modifier
                                .weight(if (isWide) 2f else 1f)
                                .aspectRatio(if (isWide) 2.18f else 1f)
                                .clip(RoundedCornerShape(50))
                                .background(when { isOperator -> Color(0xFFFF9F0A); isFunction -> Color(0xFF505050); else -> Color(0xFF1C1C1E) })
                                .clickable { onKey(key) },
                            contentAlignment = if (isWide) Alignment.CenterStart else Alignment.Center
                        ) {
                            Text(
                                key, fontSize = 28.sp, fontWeight = FontWeight.Normal,
                                color = if (isFunction) Color.Black else Color.White,
                                modifier = if (isWide) Modifier.padding(start = 28.dp) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultAppListScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var vaultApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // Load app names off main thread — fixes lag
    LaunchedEffect(Unit) {
        vaultApps = withContext(Dispatchers.IO) {
            AppVaultManager.getVaultApps(ctx).mapNotNull { pkg ->
                try {
                    val name = ctx.packageManager.getApplicationLabel(
                        ctx.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                    pkg to name
                } catch (_: Exception) { null }
            }.sortedBy { it.second }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔐 Vault", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Close", fontSize = 14.sp, color = Color(0xFF666666),
                modifier = Modifier.clickable { onClose() }.padding(8.dp))
        }

        if (vaultApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗄️", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No apps in vault", color = Color.Gray)
                    Text("Add apps in Settings → App Vault", fontSize = 12.sp, color = Color(0xFF444444))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vaultApps) { (pkg, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111111))
                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                            .clickable {
                                ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
                                    neth.iecal.questphone.core.vault.AppVaultManager.setVaultLaunch(ctx)
                                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(it)
                                    onClose()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
