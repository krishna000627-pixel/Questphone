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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import neth.iecal.questphone.app.navigation.RootRoute

private data class StatsEntry(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val gradientStart: Color,
    val gradientEnd: Color,
    val accentColor: Color,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHubScreen(navController: NavController) {

    val entries = listOf(
        StatsEntry("⚔️", "Ascension Hall",
            "Battle power · study · arena · all stats",
            Color(0xFF0A0A2E), Color(0xFF1A1A4E),
            Color(0xFF5C6BC0), RootRoute.AscensionHall.route),
        StatsEntry("📱", "Screen Time",
            "Daily app usage · 7-day trend · top apps",
            Color(0xFF0A1020), Color(0xFF0D1830),
            Color(0xFF2196F3), RootRoute.ShowScreentimeStats.route),
        StatsEntry("⏱", "Focus Sessions",
            "Pomodoro history · coins per session",
            Color(0xFF0A1A10), Color(0xFF0D2A18),
            Color(0xFF4CAF50), RootRoute.FocusSessionHistory.route),
        StatsEntry("🪙", "Coin Log",
            "Every earn & spend event with reasons",
            Color(0xFF1A1000), Color(0xFF2A1A00),
            Color(0xFFFFD700), RootRoute.CoinTransactionLog.route),
        StatsEntry("💾", "Backup & Restore",
            "Export/import all data as JSON",
            Color(0xFF001A1A), Color(0xFF002828),
            Color(0xFF00BCD4), RootRoute.BackupRestore.route),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Stats", fontWeight = FontWeight.Black, fontSize = 20.sp,
                            color = Color.White)
                        Text("Your performance at a glance",
                            fontSize = 11.sp, color = Color(0xFF666666))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                windowInsets = WindowInsets(0)
            )
        },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(entries) { e ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(e.gradientStart, e.gradientEnd)))
                        .border(1.dp,
                            Brush.horizontalGradient(listOf(e.accentColor.copy(alpha = 0.3f), Color.Transparent)),
                            RoundedCornerShape(16.dp))
                        .clickable { navController.navigate(e.route) }
                        .padding(16.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Accent icon box
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(e.accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, e.accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(e.emoji, fontSize = 20.sp)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(e.title, fontWeight = FontWeight.Bold,
                                    color = Color.White, fontSize = 15.sp)
                                Text(e.subtitle, color = Color(0xFF888888), fontSize = 11.sp)
                            }
                        }
                        Text("→", color = e.accentColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
