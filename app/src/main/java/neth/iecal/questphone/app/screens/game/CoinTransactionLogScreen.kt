package neth.iecal.questphone.app.screens.game

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
data class CoinTransaction(
    val timestampMs: Long,
    val amount: Int,       // positive = earned, negative = spent
    val reason: String
)

private const val TX_PREFS = "coin_transactions"
private const val TX_KEY = "transactions_json"
private val txJson = Json { ignoreUnknownKeys = true }

/**
 * Call this from UserRepository.addCoins / spendCoins to record every event.
 */
object CoinTransactionLogger {
    fun record(context: Context, amount: Int, reason: String) {
        val prefs = context.getSharedPreferences(TX_PREFS, Context.MODE_PRIVATE)
        val existing = load(prefs).toMutableList()
        existing.add(0, CoinTransaction(System.currentTimeMillis(), amount, reason))
        val trimmed = existing.take(200) // keep last 200
        prefs.edit().putString(TX_KEY, txJson.encodeToString(trimmed)).apply()
    }

    fun load(context: Context): List<CoinTransaction> =
        load(context.getSharedPreferences(TX_PREFS, Context.MODE_PRIVATE))

    private fun load(prefs: SharedPreferences): List<CoinTransaction> {
        val raw = prefs.getString(TX_KEY, null) ?: return emptyList()
        return try { txJson.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }
}

@HiltViewModel
class CoinTransactionLogViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinTransactionLogScreen(
    navController: NavController,
    vm: CoinTransactionLogViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val transactions = remember { CoinTransactionLogger.load(context) }
    val totalEarned = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val totalSpent = transactions.filter { it.amount < 0 }.sumOf { -it.amount }

    val byDate = transactions.groupBy {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it.timestampMs))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coin History", fontWeight = FontWeight.Bold) },
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
                // Summary strip
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+$totalEarned 🪙", fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50), fontSize = 16.sp)
                            Text("Earned", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        VerticalDivider(Modifier.height(40.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("-$totalSpent 🪙", fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935), fontSize = 16.sp)
                            Text("Spent", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        VerticalDivider(Modifier.height(40.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${vm.userRepository.userInfo.coins} 🪙",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Balance", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (transactions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            byDate.forEach { (date, txs) ->
                item {
                    Text(date, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                items(txs, key = { "${it.timestampMs}_${it.reason}" }) { tx ->
                    val isEarn = tx.amount > 0
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp, 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isEarn) Color(0x1F4CAF50) else Color(0x1FE53935)
                                ) {
                                    Text(
                                        if (isEarn) "+" else "−",
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = if (isEarn) Color(0xFF4CAF50) else Color(0xFFE53935),
                                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                                    )
                                }
                                Column {
                                    Text(tx.reason, style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(
                                        SimpleDateFormat("HH:mm", Locale.getDefault())
                                            .format(Date(tx.timestampMs)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                "${if (isEarn) "+" else "−"}${Math.abs(tx.amount)} 🪙",
                                fontWeight = FontWeight.Bold,
                                color = if (isEarn) Color(0xFF4CAF50) else Color(0xFFE53935)
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
