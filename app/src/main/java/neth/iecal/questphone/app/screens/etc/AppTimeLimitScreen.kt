package neth.iecal.questphone.app.screens.etc

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.utils.UsageStatsHelper
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** packageName -> daily limit in minutes (0 = no limit) */
private const val LIMITS_PREFS = "app_time_limits"

@HiltViewModel
class AppTimeLimitViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    private val _appUsage = MutableStateFlow<List<AppUsageEntry>>(emptyList())
    val appUsage = _appUsage.asStateFlow()

    fun load(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val helper = UsageStatsHelper(context)
            val stats = helper.getForegroundStatsByDay(LocalDate.now())
            val pm = context.packageManager
            val prefs = context.getSharedPreferences(LIMITS_PREFS, Context.MODE_PRIVATE)
            _appUsage.value = stats
                .filter { it.totalTime > 60_000 }
                .map { stat ->
                    val label = try {
                        pm.getApplicationInfo(stat.packageName, 0).loadLabel(pm).toString()
                    } catch (_: PackageManager.NameNotFoundException) { stat.packageName }
                    AppUsageEntry(
                        packageName = stat.packageName,
                        appLabel = label,
                        usedMs = stat.totalTime,
                        limitMinutes = prefs.getInt(stat.packageName, 0)
                    )
                }
                .sortedByDescending { it.usedMs }
        }
    }

    fun setLimit(context: Context, packageName: String, minutes: Int) {
        context.getSharedPreferences(LIMITS_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(packageName, minutes).apply()
        _appUsage.value = _appUsage.value.map {
            if (it.packageName == packageName) it.copy(limitMinutes = minutes) else it
        }
    }
}

data class AppUsageEntry(
    val packageName: String,
    val appLabel: String,
    val usedMs: Long,
    val limitMinutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimeLimitScreen(
    navController: NavController,
    vm: AppTimeLimitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val apps by vm.appUsage.collectAsState()
    var editingPkg by remember { mutableStateOf<String?>(null) }
    var limitInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load(context) }

    if (editingPkg != null) {
        AlertDialog(
            onDismissRequest = { editingPkg = null },
            title = { Text("Set Daily Limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter limit in minutes (0 = no limit)")
                    OutlinedTextField(
                        value = limitInput,
                        onValueChange = { limitInput = it.filter(Char::isDigit) },
                        label = { Text("Minutes") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val mins = limitInput.toIntOrNull() ?: 0
                    vm.setLimit(context, editingPkg!!, mins)
                    editingPkg = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingPkg = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Time Limits", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Set a daily usage cap per app. Once the limit is hit, the app is blocked for the rest of the day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(apps, key = { it.packageName }) { entry ->
                val pm = context.packageManager
                val icon = remember(entry.packageName) {
                    try { pm.getApplicationIcon(entry.packageName).toBitmap().asImageBitmap() }
                    catch (_: Exception) { null }
                }
                val usedMin = TimeUnit.MILLISECONDS.toMinutes(entry.usedMs)
                val overLimit = entry.limitMinutes > 0 && usedMin >= entry.limitMinutes

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (overLimit)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        Arrangement.spacedBy(12.dp), Alignment.CenterVertically
                    ) {
                        if (icon != null) {
                            Image(icon, null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(entry.appLabel, fontWeight = FontWeight.Medium)
                            Text(
                                "Used: ${usedMin}m" +
                                        if (entry.limitMinutes > 0) " / ${entry.limitMinutes}m limit" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (overLimit) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            limitInput = entry.limitMinutes.takeIf { it > 0 }?.toString() ?: ""
                            editingPkg = entry.packageName
                        }) {
                            Text(if (entry.limitMinutes > 0) "Edit" else "Set Limit")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
