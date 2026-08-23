package neth.iecal.questphone.app.screens.etc

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Serializable
data class FocusSession(
    val startMs: Long,
    val durationMs: Long,  // actual completed duration
    val coinsEarned: Int,
    val phase: String      // "WORK" | "BREAK"
)

private const val PREFS_KEY = "focus_sessions"
private const val DATA_KEY = "sessions_json"

object FocusSessionStore {
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, session: FocusSession) {
        val prefs = prefs(context)
        val existing = load(context).toMutableList()
        existing.add(0, session)
        // Keep last 90 sessions
        val trimmed = existing.take(90)
        prefs.edit().putString(DATA_KEY, json.encodeToString(trimmed)).apply()
    }

    fun load(context: Context): List<FocusSession> {
        val raw = prefs(context).getString(DATA_KEY, null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    fun todayStats(context: Context): Pair<Int, Long> {
        val todaySdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val today = todaySdf.format(Date())
        val sessions = load(context).filter {
            todaySdf.format(Date(it.startMs)) == today && it.phase == "WORK"
        }
        val totalCoins = sessions.sumOf { it.coinsEarned }
        val totalMs = sessions.sumOf { it.durationMs }
        return totalCoins to totalMs
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
}

@HiltViewModel
class FocusSessionHistoryViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSessionHistoryScreen(
    navController: NavController,
    vm: FocusSessionHistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sessions = remember { FocusSessionStore.load(context) }
    val workSessions = sessions.filter { it.phase == "WORK" }
    val totalCoins = workSessions.sumOf { it.coinsEarned }
    val totalFocusMs = workSessions.sumOf { it.durationMs }

    // Group by date
    val byDate = workSessions.groupBy {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it.startMs))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            // Summary bar
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        Arrangement.SpaceEvenly
                    ) {
                        StatChip("Sessions", "${workSessions.size}", Color(0xFF2196F3))
                        StatChip("Focus Time", formatMs(totalFocusMs), Color(0xFF4CAF50))
                        StatChip("Coins Earned", "$totalCoins 🪙", Color(0xFFFF9800))
                    }
                }
            }
            if (sessions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text("No focus sessions yet.\nComplete a Pomodoro to see history here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            byDate.forEach { (date, daySessions) ->
                item {
                    Text(date, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(daySessions) { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            Arrangement.SpaceBetween, Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startMs)),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(formatMs(session.durationMs), fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("+${session.coinsEarned} 🪙",
                                color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMs(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
