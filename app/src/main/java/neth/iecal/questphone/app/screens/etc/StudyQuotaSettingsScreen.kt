package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nethical.questphone.core.core.utils.managers.reloadApps
import neth.iecal.questphone.R

/**
 * Study Quota Settings — Fix #12.
 * If the prime study app doesn't have >= N hours of screen time today,
 * all non-study apps are hard-blocked the next day until quota is met.
 *
 * IMPORTANT: If no app is selected, the quota feature is completely disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyQuotaSettingsScreen(
    navController: NavController,
    settingsVm: LauncherSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var primePkg by remember { mutableStateOf(settingsVm.getPrimeStudyPackage()) }
    var primeName by remember { mutableStateOf("") }
    var quotaHours by remember { mutableStateOf(settingsVm.getDailyStudyQuotaHours()) }
    var quotaInput by remember { mutableStateOf(
        settingsVm.getDailyStudyQuotaHours().let {
            if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()
        }
    ) }
    var allApps by remember { mutableStateOf<List<nethical.questphone.data.AppInfo>>(emptyList()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var saveSuccess by remember { mutableStateOf(false) }

    // True only when an app is actually selected
    val isAppSelected = primePkg.isNotBlank()
    val quotaValid = quotaInput.toFloatOrNull()?.let { it > 0f && it <= 24f } ?: false
    val canSave = isAppSelected && quotaValid

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            reloadApps(context.packageManager, context).onSuccess { apps ->
                allApps = apps.sortedBy { it.name }
                primeName = apps.firstOrNull { it.packageName == primePkg }?.name
                    ?: if (primePkg.isNotBlank()) primePkg else ""
                isLoading = false
            }.onFailure { isLoading = false }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Select Prime Study App") },
            text = {
                Column {
                    OutlinedTextField(
                        value = appSearch,
                        onValueChange = { appSearch = it },
                        label = { Text("Search apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(allApps.filter {
                            it.name.contains(appSearch, ignoreCase = true) ||
                            it.packageName.contains(appSearch, ignoreCase = true)
                        }) { app ->
                            TextButton(
                                onClick = {
                                    primePkg = app.packageName
                                    primeName = app.name
                                    showAppPicker = false
                                    appSearch = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(app.name, color = Color.White, modifier = Modifier.fillMaxWidth())
                                    Text(app.packageName, color = Color(0xFF555555), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppPicker = false; appSearch = "" }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📚 Study Quota", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (canSave) {
                                val h = quotaInput.toFloatOrNull() ?: 4f
                                settingsVm.setPrimeStudyPackage(primePkg)
                                settingsVm.setDailyStudyQuotaHours(h)
                                saveSuccess = true
                            }
                        },
                        enabled = canSave
                    ) {
                        Text(
                            "Save",
                            color = if (canSave) Color(0xFFFFAB40) else Color(0xFF444444)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Save success banner
            if (saveSuccess) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A1A0A))
                            .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("✅", fontSize = 18.sp)
                        Column {
                            Text("Saved!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            Text(
                                "Quota: $primeName — ${quotaInput}h/day",
                                color = Color(0xFF4CAF50), fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ── NO APP SELECTED WARNING ────────────────────────────────────
            if (!isAppSelected) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A0A00))
                            .border(1.dp, Color(0xFFFF6F00), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "⚠️ No Prime Study App Selected",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00)
                        )
                        Text(
                            "The Study Quota feature is currently DISABLED. Select a prime study app below to activate it. Until then, no blocking will occur regardless of your quota setting.",
                            fontSize = 12.sp, color = Color(0xFF999999), lineHeight = 18.sp
                        )
                    }
                }
            }

            // ── How it works ───────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A0A00))
                        .border(1.dp, Color(0xFF2A2800), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "How Study Quota works",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFAB40)
                    )
                    listOf(
                        "Each midnight, QuestPhone checks if your prime study app had enough screen time that day",
                        "If the quota was not met, ALL apps except your Study Apps list are hard-blocked the next day",
                        "The block lifts automatically once you meet the quota for that missed day",
                        "If no prime app is selected, this feature is completely disabled — nothing is blocked"
                    ).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = Color(0xFF666666), fontSize = 12.sp)
                            Text(line, fontSize = 12.sp, color = Color(0xFF999999), lineHeight = 17.sp)
                        }
                    }
                }
            }

            // ── Prime Study App selector ───────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(
                            1.dp,
                            if (isAppSelected) Color(0xFFFFAB40) else Color(0xFF3A2000),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(
                            "Prime Study App",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                        )
                        if (!isAppSelected) {
                            Text(
                                "REQUIRED",
                                fontSize = 10.sp,
                                color = Color(0xFFFF6F00),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        "Screen time on this app counts toward your daily quota.",
                        fontSize = 11.sp, color = Color(0xFF666666)
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                            color = Color(0xFFFFAB40)
                        )
                    } else {
                        Button(
                            onClick = { showAppPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAppSelected) Color(0xFF1A1000) else Color(0xFF2A1500)
                            )
                        ) {
                            Text(
                                if (isAppSelected) "✅  $primeName" else "⚠️  Select App (required) →",
                                color = if (isAppSelected) Color(0xFFFFAB40) else Color(0xFFFF8F00)
                            )
                        }

                        if (isAppSelected) {
                            Text(primePkg, fontSize = 10.sp, color = Color(0xFF444444))
                            TextButton(
                                onClick = { primePkg = ""; primeName = "" },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Clear selection", color = Color(0xFF666666), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ── Daily quota hours ──────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Daily Study Quota",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                    )
                    Text(
                        "Minimum screen time on the prime study app per day (0–24 hours).",
                        fontSize = 11.sp, color = Color(0xFF666666)
                    )
                    OutlinedTextField(
                        value = quotaInput,
                        onValueChange = { v ->
                            quotaInput = v.filter { c -> c.isDigit() || c == '.' }
                        },
                        label = { Text("Hours (e.g. 4)") },
                        isError = !quotaValid && quotaInput.isNotBlank(),
                        supportingText = {
                            if (!quotaValid && quotaInput.isNotBlank())
                                Text("Enter a number between 0.5 and 24", color = MaterialTheme.colorScheme.error)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Quick-select chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1, 2, 3, 4, 5, 6).forEach { h ->
                            FilterChip(
                                selected = quotaInput == h.toString(),
                                onClick = { quotaInput = h.toString() },
                                label = { Text("${h}h", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Save button (bottom) ───────────────────────────────────────
            item {
                Button(
                    onClick = {
                        if (canSave) {
                            val h = quotaInput.toFloatOrNull() ?: 4f
                            settingsVm.setPrimeStudyPackage(primePkg)
                            settingsVm.setDailyStudyQuotaHours(h)
                            saveSuccess = true
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSave) Color(0xFF1A1000) else Color(0xFF111111),
                        disabledContainerColor = Color(0xFF111111)
                    )
                ) {
                    Text(
                        when {
                            !isAppSelected -> "⚠️ Select an app first"
                            !quotaValid -> "Enter a valid hour value"
                            else -> "✅ Save Quota Settings"
                        },
                        color = if (canSave) Color(0xFFFFAB40) else Color(0xFF555555)
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
