package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import neth.iecal.questphone.app.navigation.RootRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHubScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 Stats", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val items = listOf(
                StatsEntry("📱 Screen Time", "Daily app usage, 7-day trend, top apps", Color(0xFF0A1020), RootRoute.ShowScreentimeStats.route),
                StatsEntry("⏱ Focus Sessions", "Pomodoro history, coins earned per session", Color(0xFF0A1A10), RootRoute.FocusSessionHistory.route),
                StatsEntry("📚 Study Tracker", "Study time vs quota, block schedule status", Color(0xFF1A1000), RootRoute.StudyTracker.route),
                StatsEntry("📈 Stat History", "30-day growth charts per stat", Color(0xFF1A001A), RootRoute.StatHistory.route),
                StatsEntry("🪙 Coin Log", "Every earn & spend event with reasons", Color(0xFF1A1000), RootRoute.CoinTransactionLog.route),
                StatsEntry("💾 Backup & Restore", "Export/import all data as JSON", Color(0xFF001A1A), RootRoute.BackupRestore.route),
            )
            items(items.size) { i ->
                val e = items[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(e.color)
                        .clickable { navController.navigate(e.route) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(e.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(e.subtitle, color = Color.Gray, fontSize = 11.sp)
                    }
                    Text("→", color = Color.Gray, fontSize = 18.sp)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private data class StatsEntry(val title: String, val subtitle: String, val color: Color, val route: String)
