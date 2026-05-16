package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import nethical.questphone.core.core.utils.managers.reloadApps

/**
 * Stranger Mode whitelist selector.
 * Whitelist and app list both loaded in LaunchedEffect to avoid
 * composition-time crashes from Hilt/SharedPreferences timing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrangerModeSettingsScreen(
    navController: NavController,
    settingsVm: LauncherSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var allApps by remember { mutableStateOf<List<nethical.questphone.data.AppInfo>>(emptyList()) }
    var whitelist by remember { mutableStateOf(emptySet<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try { whitelist = settingsVm.getStrangerWhitelist(context) } catch (_: Exception) {}
        try {
            reloadApps(context.packageManager, context)
                .onSuccess { apps -> allApps = apps.distinctBy { it.packageName }.sortedBy { it.name } }
        } catch (_: Exception) {}
        isLoading = false
    }

    val filtered = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stranger Mode Whitelist", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { try { navController.popBackStack() } catch (_: Exception) {} }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(neth.iecal.questphone.R.drawable.baseline_info_24),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        try {
                            settingsVm.saveStrangerWhitelist(context, whitelist)
                            context.sendBroadcast(android.content.Intent("neth.iecal.questphone.RELOAD_APP_LIST"))
                            navController.popBackStack()
                        } catch (_: Exception) {}
                    }) {
                        Text("Save", color = Color(0xFF00BCD4))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp)
            )

            Text(
                "${whitelist.size} apps selected — only these show in Stranger Mode",
                fontSize = 11.sp,
                color = Color(0xFF00BCD4),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00BCD4))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val selected = app.packageName in whitelist
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFF0A1A1F) else Color(0xFF0D0D0D))
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFF00BCD4) else Color(0xFF222222),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    whitelist = if (selected) whitelist - app.packageName
                                    else whitelist + app.packageName
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    whitelist = if (checked) whitelist + app.packageName
                                    else whitelist - app.packageName
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF00BCD4),
                                    uncheckedColor = Color(0xFF444444)
                                )
                            )
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}
