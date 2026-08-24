package neth.iecal.questphone.app.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.game.InventoryBox
import neth.iecal.questphone.app.screens.launcher.CultivationCard
import neth.iecal.questphone.backed.repositories.UserRepository
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
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(onClick = { navController.navigate(RootRoute.RenderSync.route) }) {
                        Text("Sync", color = scheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
            )
        },
        containerColor = scheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar + Name card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val initials = userInfo.full_name.trim().split(" ")
                        .take(2).joinToString("") { it.firstOrNull()?.uppercase() ?: "" }
                        .ifBlank { "QP" }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(scheme.primary, scheme.secondary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            userInfo.full_name.ifBlank { "Adventurer" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        if (userInfo.profileDob.isNotEmpty()) {
                            Text(userInfo.profileDob, fontSize = 12.sp, color = scheme.onSurface.copy(alpha = 0.5f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(scheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text("Level ${userInfo.level}", fontSize = 11.sp,
                                    color = scheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Info chips
            if (userInfo.profileClass.isNotEmpty() || userInfo.profileSkills.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (userInfo.profileClass.isNotEmpty())
                        ModernInfoChip(
                            when (userInfo.profileType) {
                                "college" -> "Course"; "work" -> "Role"; else -> "Class"
                            },
                            userInfo.profileClass.ifBlank { "12th PCM" },
                            scheme.primary,
                            Modifier.weight(1f)
                        )
                    if (userInfo.profileSkills.isNotEmpty())
                        ModernInfoChip("Skill", userInfo.profileSkills, scheme.secondary, Modifier.weight(1f))
                }
            }
            if (userInfo.profileSideHustle.isNotEmpty()) {
                ModernInfoChip("Side Hustle", userInfo.profileSideHustle, scheme.tertiary, Modifier.fillMaxWidth())
            }

            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernStatCard("Coins", "$coins", scheme.tertiary, Modifier.weight(1f))
                ModernStatCard("Streak", "$streak d", Color(0xFFFF6D00), Modifier.weight(1f))
                ModernStatCard("Level", "${userInfo.level}", scheme.primary, Modifier.weight(1f))
                ModernStatCard("XP", "${userInfo.xp}", scheme.secondary, Modifier.weight(1f))
            }

            // Cultivation
            SectionLabel("Cultivation Realm", scheme)
            CultivationCard(level = userInfo.level, currentXp = userInfo.xp, streak = streak)

            // XP Progress
            val xpNeeded = xpToLevelUp(userInfo.level)
            ModernSection("Progress", scheme) {
                ModernProgressRow("XP This Level", "${userInfo.xp} / $xpNeeded", scheme)
                LinearProgressIndicator(
                    progress = { (userInfo.xp.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = scheme.primary,
                    trackColor = scheme.primary.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(4.dp))
                ModernProgressRow("Longest Streak", "${userInfo.streak.longestStreak} days", scheme)
            }

            // Stat points
            val sp = userInfo.statPoints
            ModernSection("Stat Points", scheme) {
                listOf(
                    sp.name1 to sp.value1, sp.name2 to sp.value2,
                    sp.name3 to sp.value3, sp.name4 to sp.value4
                ).forEach { (name, value) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(name, fontSize = 13.sp, color = scheme.onSurface.copy(alpha = 0.6f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.secondary.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("$value", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.secondary)
                        }
                    }
                }
                if (userInfo.statPointsToAllocate > 0) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { navController.navigate(RootRoute.ProfileSettings.route) },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allocate ${userInfo.statPointsToAllocate} points", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { navController.navigate(RootRoute.StatHistory.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f))
                    ) { Text("Stat History", fontSize = 12.sp, color = scheme.primary) }
                    OutlinedButton(
                        onClick = { navController.navigate(RootRoute.CoinTransactionLog.route) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f))
                    ) { Text("Coin Log", fontSize = 12.sp, color = scheme.primary) }
                }
            }

            // Trackers
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                SectionLabel("Trackers", scheme)
                TextButton(onClick = { navController.navigate(RootRoute.TrackerSettings.route) }) {
                    Text("Add / Edit", fontSize = 12.sp, color = scheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            if (userInfo.trackers.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                        Text("No trackers yet. Tap Add / Edit.", fontSize = 13.sp,
                            color = scheme.onSurface.copy(alpha = 0.45f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    userInfo.trackers.forEach { ModernTrackerCard(it, scheme) }
                }
            }

            // Inventory
            ModernSection("Inventory", scheme) {
                if (userInfo.inventory.isEmpty()) {
                    Text("No items yet. Buy from the Store.", fontSize = 13.sp,
                        color = scheme.onSurface.copy(alpha = 0.45f))
                } else {
                    InventoryBox(navController = navController)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String, scheme: ColorScheme) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = scheme.primary.copy(alpha = 0.7f),
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
    )
}

@Composable
private fun ModernInfoChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ModernStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ModernSection(title: String, scheme: ColorScheme, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.primary.copy(alpha = 0.7f),
                letterSpacing = 1.2.sp
            )
            HorizontalDivider(color = scheme.primary.copy(alpha = 0.1f))
            content()
        }
    }
}

@Composable
private fun ModernProgressRow(label: String, value: String, scheme: ColorScheme) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = scheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
    }
}

@Composable
private fun ModernTrackerCard(tracker: Tracker, scheme: ColorScheme) {
    val color = Color(tracker.color)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tracker.emoji, fontSize = 20.sp)
                }
                Column {
                    Text(tracker.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    if (tracker.note.isNotEmpty())
                        Text(tracker.note, fontSize = 11.sp, color = scheme.onSurface.copy(alpha = 0.45f))
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    when (tracker.type) {
                        TrackerType.COUNTDOWN -> "${tracker.value} days left"
                        TrackerType.BACKLOG   -> "${tracker.value} backlog"
                        TrackerType.COUNTER   -> if (tracker.target > 0) "${tracker.value}/${tracker.target}" else "${tracker.value}"
                        TrackerType.CHECKBOX  -> if (tracker.value == 1) "Done" else "Pending"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color
                )
            }
        }
    }
}
