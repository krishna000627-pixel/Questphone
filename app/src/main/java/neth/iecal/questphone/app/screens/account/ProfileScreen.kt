package neth.iecal.questphone.app.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.R
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.game.InventoryBox
import neth.iecal.questphone.app.screens.launcher.CultivationCard
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.habitica.StatPoints
import nethical.questphone.data.tracker.Tracker
import nethical.questphone.data.tracker.TrackerType
import nethical.questphone.data.xpToLevelUp
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    val coins = userRepository.coinsState
    val streak = userRepository.currentStreakState
    val userInfo get() = userRepository.userInfo
    fun saveUserInfo() = userRepository.saveUserInfo()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, viewModel: ProfileViewModel = hiltViewModel()) {
    val coins by viewModel.coins.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val userInfo = viewModel.userInfo

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                actions = {
                    TextButton(onClick = { navController.navigate(RootRoute.ProfileSettings.route) }) {
                        Text("Edit", color = Color(0xFF7B1FA2))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Avatar + Name ─────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val initials = userInfo.full_name.trim().split(" ")
                    .take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
                    .ifBlank { "KT" }
                Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF7B1FA2), Color(0xFF1565C0)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Column {
                    Text(userInfo.full_name.ifBlank { "Krishna Tiwari" }, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    if (userInfo.profileDob.isNotEmpty())
                        Text(userInfo.profileDob, fontSize = 13.sp, color = Color.Gray)
                }
            }

            // ── Info Chips ────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (userInfo.profileClass.isNotEmpty())
                    InfoChip(when (userInfo.profileType) {
                        "college" -> "Course" ; "work" -> "Role" ; else -> "Class"
                    }, userInfo.profileClass.ifBlank { "12th PCM" }, Color(0xFF1565C0), Modifier.weight(1f))
                if (userInfo.profileSkills.isNotEmpty())
                    InfoChip("Skill", userInfo.profileSkills, Color(0xFF6A1B9A), Modifier.weight(1f))
            }
            if (userInfo.profileSideHustle.isNotEmpty()) {
                InfoChip("Side Hustle", userInfo.profileSideHustle, Color(0xFF00695C), Modifier.fillMaxWidth())
            }

            // ── Stats Row ─────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("🪙", "Coins", "$coins", Color(0xFFFFB300), Modifier.weight(1f))
                StatCard("💎", "Diamonds", "${userInfo.diamonds}", Color(0xFF7B1FA2), Modifier.weight(1f))
                StatCard("🔥", "Streak", "$streak d", Color(0xFFFF6D00), Modifier.weight(1f))
                StatCard("⚔️", "Level", "${userInfo.level}", Color(0xFF1565C0), Modifier.weight(1f))
            }

            // ── Cultivation Card ──────────────────────────────────────
            Text("Cultivation Realm", fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold)
            CultivationCard(level = userInfo.level, currentXp = userInfo.xp, streak = streak)

            // ── XP Progress ───────────────────────────────────────────
            val xpNeeded = xpToLevelUp(userInfo.level)
            ProfileSection(title = "Progress") {
                ProgressRow("XP This Level", "${userInfo.xp} / $xpNeeded")
                ProgressRow("Longest Streak", "${userInfo.streak.longestStreak} days")
            }

            // ── Stat Points ───────────────────────────────────────────
            val sp = userInfo.statPoints
            ProfileSection(title = "Stat Points") {
                listOf(
                    sp.name1 to sp.value1, sp.name2 to sp.value2,
                    sp.name3 to sp.value3, sp.name4 to sp.value4
                ).forEach { (name, value) ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(name, fontSize = 13.sp, color = Color(0xFFBBBBBB))
                        Text("$value", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                    }
                }
                if (userInfo.statPointsToAllocate > 0) {
                    TextButton(onClick = { navController.navigate(RootRoute.ProfileSettings.route) }) {
                        Text("${userInfo.statPointsToAllocate} points to allocate →", color = Color(0xFFFFB300))
                    }
                }
            }

            // ── Trackers ──────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Trackers", fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { navController.navigate(RootRoute.TrackerSettings.route) }) {
                    Text("+ Add / Edit", fontSize = 11.sp, color = Color(0xFF7B1FA2))
                }
            }
            if (userInfo.trackers.isEmpty()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D0D0D)).padding(16.dp), Alignment.Center) {
                    Text("No trackers. Tap 'Add / Edit'.", fontSize = 13.sp, color = Color.Gray)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    userInfo.trackers.forEach { TrackerProfileCard(it) }
                }
            }

            // ── Inventory ─────────────────────────────────────────────
            ProfileSection(title = "Inventory") {
                if (userInfo.inventory.isEmpty()) {
                    Text("No items. Buy from the Store.", fontSize = 13.sp, color = Color.Gray)
                } else {
                    InventoryBox(navController = navController)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun InfoChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f))
        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
@Composable private fun StatCard(emoji: String, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D0D0D))
        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 9.sp, color = Color.Gray)
    }
}
@Composable private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0D0D0D))
        .border(1.dp, Color(0xFF222222), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}
@Composable private fun ProgressRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color(0xFFBBBBBB))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
@Composable private fun NavChip(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f))
        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        .clickable { onClick() }.padding(12.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
@Composable private fun TrackerProfileCard(tracker: Tracker) {
    val color = Color(tracker.color)
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0D0D0D))
        .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tracker.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(tracker.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (tracker.note.isNotEmpty()) Text(tracker.note, fontSize = 11.sp, color = Color.Gray)
            }
        }
        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(when (tracker.type) {
                TrackerType.COUNTDOWN -> "${tracker.value} days left"
                TrackerType.BACKLOG   -> "${tracker.value} backlog"
                TrackerType.COUNTER   -> if (tracker.target > 0) "${tracker.value}/${tracker.target}" else "${tracker.value}"
                TrackerType.CHECKBOX  -> if (tracker.value == 1) "✅ Done" else "Pending"
            }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
