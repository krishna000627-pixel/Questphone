package neth.iecal.questphone.app.screens.etc

import android.app.admin.DevicePolicyManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.admin.DeviceAdminManager
import neth.iecal.questphone.core.notifications.NotificationBlockerService
import javax.inject.Inject

@HiltViewModel
class NotificationBlockerViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    private val _notifBlockEnabled = MutableStateFlow(userRepository.userInfo.notificationBlockEnabled)
    val notifBlockEnabled = _notifBlockEnabled.asStateFlow()

    private val _blockAllInFocus = MutableStateFlow(userRepository.userInfo.blockAllNotificationsInFocus)
    val blockAllInFocus = _blockAllInFocus.asStateFlow()

    private val _blockDistractingNotifs = MutableStateFlow(userRepository.userInfo.blockDistractingAppNotifications)
    val blockDistractingNotifs = _blockDistractingNotifs.asStateFlow()

    private val _adminEnabled = MutableStateFlow(userRepository.userInfo.adminLockEnabled)
    val adminEnabled = _adminEnabled.asStateFlow()

    fun setNotifBlock(v: Boolean) {
        _notifBlockEnabled.value = v
        userRepository.userInfo.notificationBlockEnabled = v
        userRepository.saveUserInfo()
    }

    fun setBlockAllInFocus(v: Boolean) {
        _blockAllInFocus.value = v
        userRepository.userInfo.blockAllNotificationsInFocus = v
        userRepository.saveUserInfo()
    }

    fun setBlockDistractingNotifs(v: Boolean) {
        _blockDistractingNotifs.value = v
        userRepository.userInfo.blockDistractingAppNotifications = v
        userRepository.saveUserInfo()
    }

    fun setAdminEnabled(v: Boolean) {
        _adminEnabled.value = v
        userRepository.userInfo.adminLockEnabled = v
        userRepository.saveUserInfo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBlockerSettingsScreen(
    navController: NavController,
    vm: NotificationBlockerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notifBlockEnabled by vm.notifBlockEnabled.collectAsStateWithLifecycle()
    val blockAllInFocus by vm.blockAllInFocus.collectAsStateWithLifecycle()
    val blockDistractingNotifs by vm.blockDistractingNotifs.collectAsStateWithLifecycle()
    val adminEnabled by vm.adminEnabled.collectAsStateWithLifecycle()

    // Re-check live permissions when screen resumes
    var hasNotifAccess by remember { mutableStateOf(NotificationBlockerService.isGranted(context)) }
    var hasDNDAccess by remember { mutableStateOf(NotificationBlockerService.isDNDGranted(context)) }
    var isAdminActive by remember { mutableStateOf(DeviceAdminManager.isAdminActive(context)) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasNotifAccess = NotificationBlockerService.isGranted(context)
            hasDNDAccess = NotificationBlockerService.isDNDGranted(context)
            isAdminActive = DeviceAdminManager.isAdminActive(context)
        }
    }

    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isAdminActive = DeviceAdminManager.isAdminActive(context)
        vm.setAdminEnabled(isAdminActive)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications & Admin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── NOTIFICATION BLOCKER ─────────────────────────────────────
            item {
                SectionHeader("🔕 Notification Blocker")
            }

            item {
                PermissionCard(
                    title = "Notification Access",
                    description = "Required to intercept and cancel notifications from distracting apps.",
                    granted = hasNotifAccess,
                    onGrant = { NotificationBlockerService.openSettings(context) }
                )
            }

            item {
                PermissionCard(
                    title = "Do Not Disturb Access",
                    description = "Required to enable full DND silence during Deep Focus sessions.",
                    granted = hasDNDAccess,
                    onGrant = { NotificationBlockerService.openDNDSettings(context) }
                )
            }

            item {
                SettingToggleCard(
                    title = "Enable Notification Blocker",
                    subtitle = "Block notifications from distracting apps",
                    enabled = hasNotifAccess,
                    checked = notifBlockEnabled,
                    onCheckedChange = { vm.setNotifBlock(it) }
                )
            }

            item {
                SettingToggleCard(
                    title = "Block ALL during Focus",
                    subtitle = "Silence every notification when Focus Mode is active",
                    enabled = hasNotifAccess && notifBlockEnabled,
                    checked = blockAllInFocus,
                    onCheckedChange = { vm.setBlockAllInFocus(it) }
                )
            }

            item {
                SettingToggleCard(
                    title = "Block distracting app notifications",
                    subtitle = "Cancel notifications from apps in your block list",
                    enabled = hasNotifAccess && notifBlockEnabled,
                    checked = blockDistractingNotifs,
                    onCheckedChange = { vm.setBlockDistractingNotifs(it) }
                )
            }

            // ── DEVICE ADMIN ─────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }

            item {
                SectionHeader("🛡 Device Admin (Optional)")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAdminActive)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Prevent Uninstallation", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Makes it harder to uninstall QuestPhone by requiring " +
                                            "Device Admin deactivation first. " +
                                            "Useful for accountability — you can always disable this.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Surface(
                            color = if (isAdminActive) Color(0x1F4CAF50) else Color(0x1FFF9800),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                if (isAdminActive) "✅ Device Admin is ACTIVE — uninstall is locked"
                                else "⚠ Device Admin is NOT active",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = if (isAdminActive) Color(0xFF388E3C) else Color(0xFFE65100)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isAdminActive) {
                                Button(
                                    onClick = {
                                        adminLauncher.launch(DeviceAdminManager.buildActivationIntent(context))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) { Text("Activate Admin") }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        DeviceAdminManager.deactivate(context)
                                        isAdminActive = false
                                        vm.setAdminEnabled(false)
                                    }
                                ) { Text("Deactivate Admin") }
                            }
                        }

                        Text(
                            "To deactivate later: Settings → Security → Device Admins → QuestPhone → Deactivate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text("✅", fontSize = 20.sp)
            } else {
                TextButton(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.4f))
            }
            Switch(
                checked = checked && enabled,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled
            )
        }
    }
}
