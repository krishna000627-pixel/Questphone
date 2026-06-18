package neth.iecal.questphone.app.screens.locker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.withContext
import neth.iecal.questphone.core.vault.AppVaultManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVaultSettingsScreen(navController: NavController) {
    val ctx = LocalContext.current
    val vaultPin = remember { AppVaultManager.getPin(ctx) }
    var unlocked by remember { mutableStateOf(vaultPin.isBlank()) }

    // PIN gate
    if (!unlocked) {
        PinGateScreen(
            correctPin = vaultPin,
            title = "🔐 Vault Settings",
            onUnlocked = { unlocked = true },
            onCancel = { navController.popBackStack() }
        )
        return
    }

    var vaultApps by remember { mutableStateOf(AppVaultManager.getVaultApps(ctx)) }
    var allApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    // PIN
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var pinSuccess by remember { mutableStateOf(false) }
    val currentPin = vaultPin

    // Secret code
    var secretCode by remember { mutableStateOf(AppVaultManager.getSecretCode(ctx)) }
    var secretInput by remember { mutableStateOf(AppVaultManager.getSecretCode(ctx)) }
    var secretSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        allApps = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second }
        }
    }

    val filtered = allApps.filter {
        search.isBlank() || it.second.contains(search, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 Vault Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Vault PIN
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (currentPin.isBlank()) "Set Vault PIN" else "Change Vault PIN",
                        fontWeight = FontWeight.SemiBold, color = Color.White
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError.isNotBlank()) Text(pinError, color = Color(0xFFE53935), fontSize = 12.sp)
                    if (pinSuccess) Text("✅ Vault PIN saved", color = Color(0xFF69F0AE), fontSize = 12.sp)
                    Button(
                        onClick = {
                            pinError = ""; pinSuccess = false
                            when {
                                newPin.length < 4 -> pinError = "PIN must be at least 4 digits"
                                newPin != confirmPin -> pinError = "PINs don't match"
                                else -> {
                                    AppVaultManager.setPin(ctx, newPin)
                                    newPin = ""; confirmPin = ""; pinSuccess = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C))
                    ) { Text("Save Vault PIN") }
                }
            }

            // Secret code
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Calculator Secret Code", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        "Enter this code in the calculator then press = to open vault",
                        fontSize = 12.sp, color = Color(0xFF666666)
                    )
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { if (it.all { c -> c.isDigit() }) secretInput = it },
                        label = { Text("Secret code (numbers only)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (secretSuccess) Text("✅ Secret code saved", color = Color(0xFF69F0AE), fontSize = 12.sp)
                    Button(
                        onClick = {
                            AppVaultManager.setSecretCode(ctx, secretInput)
                            secretCode = secretInput
                            secretSuccess = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C))
                    ) { Text("Save Secret Code") }
                }
            }

            item {
                Text(
                    "VAULT APPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF444444),
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(filtered) { (pkg, name) ->
                val inVault = vaultApps.contains(pkg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(
                            1.dp,
                            if (inVault) Color(0xFF4A148C) else Color(0xFF222222),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (inVault) AppVaultManager.removeFromVault(ctx, pkg)
                            else AppVaultManager.addToVault(ctx, pkg)
                            vaultApps = AppVaultManager.getVaultApps(ctx)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(pkg, fontSize = 10.sp, color = Color(0xFF444444))
                    }
                    Switch(
                        checked = inVault,
                        onCheckedChange = {
                            if (inVault) AppVaultManager.removeFromVault(ctx, pkg)
                            else AppVaultManager.addToVault(ctx, pkg)
                            vaultApps = AppVaultManager.getVaultApps(ctx)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4A148C))
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
