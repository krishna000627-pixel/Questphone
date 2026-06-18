package neth.iecal.questphone.app.screens.etc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

@HiltViewModel
class QuestNotificationsViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestNotificationsScreen(
    navController: NavController,
    vm: QuestNotificationsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val u = vm.userRepository.userInfo

    var briefingEnabled by remember { mutableStateOf(u.dailyBriefingEnabled) }
    var briefingHour by remember { mutableIntStateOf(u.dailyBriefingHour) }
    var questReminder by remember { mutableStateOf(u.questReminderEnabled) }
    var streakWarning by remember { mutableStateOf(u.streakWarningEnabled) }

    fun save() {
        u.dailyBriefingEnabled = briefingEnabled
        u.dailyBriefingHour = briefingHour
        u.questReminderEnabled = questReminder
        u.streakWarningEnabled = streakWarning
        vm.userRepository.saveUserInfo()
        scheduleQuestNotifications(context, vm.userRepository)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { save(); navController.popBackStack() }) {
                        Text("<", color = Color.White, fontSize = 18.sp)
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) { Text("Save", color = Color(0xFF00BCD4)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            item {
                NotifToggleCard(
                    title = "Kai Daily Briefing",
                    subtitle = "Morning summary of today's quests + motivation from Kai",
                    enabled = briefingEnabled,
                    onToggle = { briefingEnabled = it }
                ) {
                    if (briefingEnabled) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Briefing time", fontSize = 13.sp, color = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { if (briefingHour > 5) briefingHour-- }) {
                                    Text("-", color = Color(0xFF00BCD4))
                                }
                                Text("%02d:00".format(briefingHour), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { if (briefingHour < 11) briefingHour++ }) {
                                    Text("+", color = Color(0xFF00BCD4))
                                }
                            }
                        }
                    }
                }
            }

            item {
                NotifToggleCard(
                    title = "Quest Reminders",
                    subtitle = "Get notified when a quest's time window opens",
                    enabled = questReminder,
                    onToggle = { questReminder = it }
                )
            }

            item {
                NotifToggleCard(
                    title = "Streak Warning",
                    subtitle = "Reminded at 9:00 PM if you haven't completed today's quests",
                    enabled = streakWarning,
                    onToggle = { streakWarning = it }
                )
            }

            item {
                Button(
                    onClick = { sendTestNotification(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Text("Send test notification", color = Color(0xFF888888))
                }
            }
        }
    }
}

@Composable
private fun NotifToggleCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF0D0D0D), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(subtitle, fontSize = 11.sp, color = Color(0xFF555555), lineHeight = 16.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00BCD4))
            )
        }
        extra()
    }
}

private const val NOTIF_CHANNEL_QUESTS = "quest_reminders"

fun scheduleQuestNotifications(context: Context, userRepository: UserRepository) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_QUESTS, "Quest Reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Quest start reminders and streak warnings" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

fun sendTestNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_QUESTS, "Quest Reminders", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_QUESTS)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("QuestPhone")
        .setContentText("Notifications are working! Your quests await. 🎯")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(9999, notif)
    } catch (_: SecurityException) {}
}
