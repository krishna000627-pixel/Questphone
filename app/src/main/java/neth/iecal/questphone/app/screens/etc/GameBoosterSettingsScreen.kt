package neth.iecal.questphone.app.screens.etc

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import nethical.questphone.core.core.utils.managers.reloadApps
import nethical.questphone.data.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoosterSettingsScreen(
    navController: NavController,
    settingsVm: LauncherSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var allApps by remember { mutableStateOf<List<nethical.questphone.data.AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Booster app selector
    var gameBoosterPackage by remember { mutableStateOf(settingsVm.getGameBoosterPackage()) }
    var showBoosterPicker by remember { mutableStateOf(false) }

    // Game apps list
    var gameApps by remember { mutableStateOf(settingsVm.getGameApps().toSet()) }
    var gameAppsSearchQuery by remember { mutableStateOf("") }
    var showGameAppPicker by remember { mutableStateOf(false) }
    var pickerSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            reloadApps(context.packageManager, context)
                .onSuccess { apps -> allApps = apps.distinctBy { it.packageName }.sortedBy { it.name } }
        } catch (_: Exception) {}
        isLoading = false
    }

    val filteredForPicker = remember(allApps, pickerSearchQuery) {
        if (pickerSearchQuery.isBlank()) allApps
        else allApps.filter { it.name.contains(pickerSearchQuery, ignoreCase = true) }
    }

    // App picker dialog — choose which app is the Game Booster
    if (showBoosterPicker) {
        var boosterSearch by remember { mutableStateOf("") }
        val boosterFiltered = remember(allApps, boosterSearch) {
            if (boosterSearch.isBlank()) allApps
            else allApps.filter { it.name.contains(boosterSearch, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showBoosterPicker = false },
            title = { Text("Select Game Booster App", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = boosterSearch,
                        onValueChange = { boosterSearch = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        if (gameBoosterPackage.isNotEmpty()) {
                            item {
                                TextButton(
                                    onClick = {
                                        settingsVm.setGameBoosterPackage("")
                                        gameBoosterPackage = ""
                                        showBoosterPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("✕ Remove Game Booster", color = MaterialTheme.colorScheme.error) }
                                HorizontalDivider()
                            }
                        }
                        items(boosterFiltered, key = { it.packageName }) { app ->
                            val selected = app.packageName == gameBoosterPackage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.surface
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        settingsVm.setGameBoosterPackage(app.packageName, app.name)
                                        gameBoosterPackage = app.packageName
                                        showBoosterPicker = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    app.name,
                                    fontSize = 14.sp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBoosterPicker = false }) { Text("Cancel") }
            }
        )
    }

    // Game app picker dialog — toggle which apps count as game apps
    if (showGameAppPicker) {
        AlertDialog(
            onDismissRequest = { showGameAppPicker = false; pickerSearchQuery = "" },
            title = { Text("Add Game Apps", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pickerSearchQuery,
                        onValueChange = { pickerSearchQuery = it },
                        label = { Text("Search apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text(
                        "${gameApps.size} selected — hidden from main drawer, only appear inside Game Booster",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(filteredForPicker, key = { it.packageName }) { app ->
                            val inGame = app.packageName in gameApps
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (inGame) MaterialTheme.colorScheme.surface
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        1.dp,
                                        if (inGame) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (inGame) {
                                            settingsVm.removeGameApp(app.packageName)
                                        } else {
                                            settingsVm.addGameApp(app.packageName)
                                        }
                                        gameApps = settingsVm.getGameApps().toSet()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    app.name,
                                    color = if (inGame) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = if (inGame) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = inGame,
                                    onCheckedChange = { checked ->
                                        if (checked) settingsVm.addGameApp(app.packageName)
                                        else settingsVm.removeGameApp(app.packageName)
                                        gameApps = settingsVm.getGameApps().toSet()
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGameAppPicker = false; pickerSearchQuery = "" }) {
                    Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎮 Game Booster", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { try { navController.popBackStack() } catch (_: Exception) {} }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            // -- Game Booster App selector ----------------------------------
            Text(
                "GAME BOOSTER APP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (gameBoosterPackage.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { showBoosterPicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val boosterName = if (gameBoosterPackage.isEmpty()) {
                        "Not set — tap to select"
                    } else {
                        try {
                            context.packageManager.getApplicationLabel(
                                context.packageManager.getApplicationInfo(gameBoosterPackage, 0)
                            ).toString()
                        } catch (_: Exception) { gameBoosterPackage }
                    }
                    Text(
                        boosterName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (gameBoosterPackage.isEmpty())
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    if (gameBoosterPackage.isNotEmpty()) {
                        Text(
                            gameBoosterPackage,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
                Text(
                    if (gameBoosterPackage.isEmpty()) "Select" else "Change",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                "This app appears in the launcher as \"🎮 Game Booster\". Selecting it here hides it from the regular app list and surfaces it as a special entry.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            // -- Game Apps section ------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "GAME APPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        if (gameApps.isEmpty()) "None — hidden from main drawer"
                        else "${gameApps.size} app${if (gameApps.size == 1) "" else "s"} hidden from main drawer",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { showGameAppPicker = true }) {
                    Text("+ Add Apps", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            // Search bar for existing game apps
            if (gameApps.isNotEmpty()) {
                OutlinedTextField(
                    value = gameAppsSearchQuery,
                    onValueChange = { gameAppsSearchQuery = it },
                    label = { Text("Search game apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            if (isLoading && gameApps.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (gameApps.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No game apps added yet.\nTap \"+ Add Apps\" to choose which games to hide from the main drawer.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                val sortedGameApps = remember(gameApps, allApps, gameAppsSearchQuery) {
                    gameApps
                        .map { pkg ->
                            val name = try {
                                context.packageManager.getApplicationLabel(
                                    context.packageManager.getApplicationInfo(pkg, 0)
                                ).toString()
                            } catch (_: Exception) { pkg }
                            Pair(pkg, name)
                        }
                        .filter { (_, name) ->
                            gameAppsSearchQuery.isBlank() || name.contains(gameAppsSearchQuery, ignoreCase = true)
                        }
                        .sortedBy { (_, name) -> name }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedGameApps, key = { it.first }) { (pkg, appName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(appName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text(pkg, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            }
                            Switch(
                                checked = true,
                                onCheckedChange = {
                                    settingsVm.removeGameApp(pkg)
                                    gameApps = settingsVm.getGameApps().toSet()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}
