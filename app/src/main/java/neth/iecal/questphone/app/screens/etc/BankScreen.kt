package neth.iecal.questphone.app.screens.etc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.StockHolding
import nethical.questphone.data.TradeTransaction
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Virtual Market Engine ─────────────────────────────────────────────────────

data class VirtualStock(
    val symbol: String,
    val name: String,
    val sector: String,
    val basePrice: Float,      // reference price (GC)
    val volatility: Float,     // hourly volatility fraction e.g. 0.03 = 3%
    val trend: Float = 0f      // per-hour drift e.g. +0.001 = slight uptrend
)

data class VirtualQuote(
    val stock: VirtualStock,
    val price: Float,
    val prevClose: Float,
    val dayHigh: Float,
    val dayLow: Float,
    val circuitHalted: Boolean
) {
    val change get() = price - prevClose
    val changePct get() = if (prevClose > 0f) (change / prevClose) * 100f else 0f
    val symbol get() = stock.symbol
    val name get() = stock.name
}

object VirtualMarket {

    val stocks = listOf(
        VirtualStock("QSTCO",  "QuestCorp",      "Technology", 420f,  0.025f,  0.0008f),
        VirtualStock("XPIDEX", "XP Index Fund",  "Finance",    1200f, 0.012f,  0.0003f),
        VirtualStock("COINVT", "CoinVault",       "Crypto",     88f,   0.09f,   0.002f),
        VirtualStock("BRNFR",  "BrainForge AI",  "Technology", 650f,  0.04f,   0.0015f),
        VirtualStock("LOOTBX", "LootBox Inc",    "Gaming",     145f,  0.06f,  -0.001f),
        VirtualStock("DMGLD",  "DreamGold",      "Commodity",  3100f, 0.010f,  0.0002f),
        VirtualStock("RPGMTR", "RPG Motors",     "Auto",       520f,  0.032f,  0.0006f),
        VirtualStock("NRGLNK", "NRG Link",       "Energy",     280f,  0.022f,  0.0005f)
    )

    // Bucket = 5-minute interval index from epoch
    fun currentBucket(): Long = System.currentTimeMillis() / (5L * 60_000L)

    // Deterministic price at a given bucket via seeded Catmull-Rom noise.
    // Keyframe every 12 buckets (1 hour): O(1) per call, smooth curves.
    fun priceAt(stock: VirtualStock, bucket: Long): Float {
        val h = (stock.symbol.hashCode().toLong() and 0xFFFFFFFFL) * 2654435761L
        val kf = bucket / 12L
        val t  = (bucket % 12L).toFloat() / 12f

        fun kv(k: Long): Float {
            val s = h xor (k * 6364136223846793005L + 1442695040888963407L)
            return ((s ushr 32) and 0xFFFFFFFFL).toFloat() / 0xFFFFFFFFL.toFloat()  // 0..1
        }

        // Catmull-Rom spline through 4 keyframe values
        val p0 = kv(kf - 1); val p1 = kv(kf)
        val p2 = kv(kf + 1); val p3 = kv(kf + 2)
        val t2 = t * t; val t3 = t2 * t
        val smooth = 0.5f * ((2f * p1) + (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3)

        // Add fast (5-min) micro-noise
        val micro = run {
            val ms = h xor (bucket * 2654435761L)
            ((ms ushr 32) and 0xFFFFFFFFL).toFloat() / 0xFFFFFFFFL.toFloat() - 0.5f
        } * 0.15f  // micro noise is 15% of total amplitude

        val noise = (smooth - 0.5f) + micro           // -0.65..+0.65
        val range = stock.basePrice * stock.volatility * 4f  // ±4 * hourly vol
        // Layer in trend: price drifts by trend * hours elapsed since epoch
        val trendOffset = stock.trend * stock.basePrice * (bucket / 12f).coerceAtMost(720f)
        return (stock.basePrice + noise * range + trendOffset).coerceAtLeast(stock.basePrice * 0.05f)
    }

    // Price history: `count` points ending at `endBucket`
    fun history(stock: VirtualStock, endBucket: Long, count: Int): List<Float> =
        (0 until count).map { i -> priceAt(stock, endBucket - count + 1 + i) }

    fun quote(stock: VirtualStock, now: Long = currentBucket()): VirtualQuote {
        val price     = priceAt(stock, now)
        val prevClose = priceAt(stock, now - 288L)  // 24h ago
        val dayPts    = (0..12).map { i -> priceAt(stock, now - 144L + i * 12L) }  // ~12-hr scan
        val halted    = abs((price - prevClose) / prevClose.coerceAtLeast(1f)) * 100f >= 20f
        return VirtualQuote(stock, price, prevClose, dayPts.max(), dayPts.min(), halted)
    }
}

// ── Trading Rules ─────────────────────────────────────────────────────────────

const val BROKERAGE_PCT = 0.001f    // 0.1% per trade
const val MIN_TRADE_GC  = 1         // minimum 1 GC invest/redeem
const val CIRCUIT_LIMIT = 20f       // % move to halt

fun brokerageFee(gcAmount: Int): Int = (gcAmount * BROKERAGE_PCT).roundToInt().coerceAtLeast(1)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class BankViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    val coinsState = userRepository.coinsState.asStateFlow()

    private val _quotes = MutableStateFlow<Map<String, VirtualQuote>>(emptyMap())
    val quotes = _quotes.asStateFlow()

    private val _portfolioValue = MutableStateFlow(0f)
    val portfolioValue = _portfolioValue.asStateFlow()

    // Selected stock for chart / trade sheet
    private val _selected = MutableStateFlow<VirtualQuote?>(null)
    val selected = _selected.asStateFlow()

    // Chart data: symbol -> list of prices (newest last)
    private val _chartData = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val chartData = _chartData.asStateFlow()

    private var tickJob: Job? = null

    init { refresh() }

    fun refresh() {
        val now = VirtualMarket.currentBucket()
        val qs  = VirtualMarket.stocks.associate { s -> s.symbol to VirtualMarket.quote(s, now) }
        _quotes.value = qs
        recalcPortfolio(qs)
        // Update selected quote if one is open
        _selected.value?.let { sel -> _selected.value = qs[sel.symbol] }
    }

    fun selectStock(symbol: String, periods: Int = 80) {
        val q = _quotes.value[symbol] ?: return
        _selected.value = q
        loadChart(symbol, periods)
    }

    fun clearSelection() { _selected.value = null }

    fun loadChart(symbol: String, periods: Int) {
        val stock = VirtualMarket.stocks.firstOrNull { it.symbol == symbol } ?: return
        val now   = VirtualMarket.currentBucket()
        viewModelScope.launch {
            _chartData.value = _chartData.value + (symbol to VirtualMarket.history(stock, now, periods))
        }
    }

    fun startAutoRefresh() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) { delay(5 * 60_000L); refresh() }
        }
    }

    fun stopAutoRefresh() { tickJob?.cancel(); tickJob = null }

    private fun recalcPortfolio(qs: Map<String, VirtualQuote>) {
        val h = userRepository.getStockHoldings()
        _portfolioValue.value = h.entries.sumOf { (sym, hld) ->
            ((qs[sym]?.price ?: hld.avgBuyPrice) * hld.quantity).toDouble()
        }.toFloat()
    }

    // Buy: deducts GC + brokerage, adds holding
    fun buyStock(symbol: String, gcAmount: Int): BuySellResult {
        val q = _quotes.value[symbol] ?: return BuySellResult.Error("Stock not found")
        if (q.circuitHalted) return BuySellResult.Error("Circuit breaker active — trading halted")
        if (gcAmount < MIN_TRADE_GC) return BuySellResult.Error("Minimum trade is $MIN_TRADE_GC GC")
        val fee  = brokerageFee(gcAmount)
        val total = gcAmount + fee
        if (total > (userRepository.coinsState.value ?: 0))
            return BuySellResult.Error("Not enough GC (need $total incl. ₹$fee brokerage)")
        val qty = gcAmount.toFloat() / q.price
        val ok  = userRepository.buyStock(symbol, q.name, qty, q.price)
        return if (ok) { recalcPortfolio(_quotes.value); BuySellResult.Success(qty, fee) }
        else BuySellResult.Error("Transaction failed")
    }

    // Sell: adds GC back minus brokerage
    fun sellStock(symbol: String, qty: Float): BuySellResult {
        val q = _quotes.value[symbol] ?: return BuySellResult.Error("Stock not found")
        if (q.circuitHalted) return BuySellResult.Error("Circuit breaker active — trading halted")
        val proceeds = (qty * q.price).roundToInt()
        val fee = brokerageFee(proceeds)
        val ok  = userRepository.sellStock(symbol, qty, q.price)
        return if (ok) { recalcPortfolio(_quotes.value); BuySellResult.Success(qty, fee) }
        else BuySellResult.Error("Not enough shares or transaction failed")
    }

    fun getHoldings(): Map<String, StockHolding>           = userRepository.getStockHoldings()
    fun getTransactions(): List<TradeTransaction>           = userRepository.getTradingTransactions()
    fun takeLoan(amount: Int)                               = userRepository.takeLoan(amount)
    fun repayLoan(amount: Int)                              = userRepository.repayLoan(amount)
    fun hasActiveLoan()                                     = userRepository.hasActiveLoan()
    fun getLoanRemaining()                                  = userRepository.getLoanRemaining()
    fun getLoanAmount()                                     = userRepository.getLoanAmount()
    fun getLoanRepaidAmount()                               = userRepository.getLoanRepaidAmount()
    fun getLoanDueDate()                                    = userRepository.getLoanDueDate()
    fun getLoanPenaltyDays()                                = userRepository.getLoanPenaltyDays()
    fun isLoanStoreLocked()                                 = userRepository.isLoanStoreLocked()
    fun getLoanXpPenaltyTotal()                             = userRepository.userInfo.loanXpPenaltyTotal
}

sealed class BuySellResult {
    data class Success(val qty: Float, val fee: Int) : BuySellResult()
    data class Error(val message: String) : BuySellResult()
}

// ── Main BankScreen ───────────────────────────────────────────────────────────

@Composable
fun BankScreen(modifier: Modifier = Modifier, vm: BankViewModel = hiltViewModel()) {
    val coins         by vm.coinsState.collectAsState()
    val quotes        by vm.quotes.collectAsState()
    val portfolioValue by vm.portfolioValue.collectAsState()
    val selected      by vm.selected.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Start/stop auto-refresh with the screen lifecycle
    DisposableEffect(Unit) { vm.startAutoRefresh(); onDispose { vm.stopAutoRefresh() } }

    // Stock detail sheet overlay
    if (selected != null) {
        StockDetailSheet(
            quote   = selected!!,
            coins   = coins ?: 0,
            chartData = vm.chartData.collectAsState().value[selected!!.symbol] ?: emptyList(),
            onLoadChart = { periods -> vm.loadChart(selected!!.symbol, periods) },
            onBuy   = { gc -> vm.buyStock(selected!!.symbol, gc) },
            onSell  = { qty -> vm.sellStock(selected!!.symbol, qty) },
            holding = vm.getHoldings()[selected!!.symbol],
            onDismiss = { vm.clearSelection() }
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Balance header ──────────────────────────────────────────────────
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("GC Balance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("◈ ${coins ?: 0} GC", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (portfolioValue > 0f) {
                        Text("+ ◈%.0f invested".format(portfolioValue), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                Text("Virtual Market · Paper Trading · 0.1% brokerage", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        }

        // ── Tabs ────────────────────────────────────────────────────────────
        TabRow(selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary) {
            listOf("📊 Market", "💼 Portfolio", "🏦 Loan").forEachIndexed { i, label ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(label, fontSize = 12.sp, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) })
            }
        }

        AnimatedContent(targetState = selectedTab, label = "bank_tab") { tab ->
            when (tab) {
                0 -> MarketTab(quotes = quotes, onSelect = { vm.selectStock(it.symbol); vm.loadChart(it.symbol, 80) }, onRefresh = { vm.refresh() })
                1 -> PortfolioTab(vm = vm, quotes = quotes)
                2 -> LoanTab(vm = vm, coins = coins ?: 0)
            }
        }
    }
}

// ── Market Tab ────────────────────────────────────────────────────────────────

@Composable
fun MarketTab(
    quotes: Map<String, VirtualQuote>,
    onSelect: (VirtualQuote) -> Unit,
    onRefresh: () -> Unit
) {
    val sorted = remember(quotes) {
        quotes.values.sortedByDescending { abs(it.changePct) }
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Virtual Exchange", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Tap any stock to chart & trade", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Sector chips
        val sectors = remember(sorted) { sorted.map { it.stock.sector }.distinct() }
        item {
            var filter by remember { mutableStateOf("All") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("All") + sectors).forEach { sec ->
                    FilterChip(
                        selected = filter == sec,
                        onClick  = { filter = sec },
                        label    = { Text(sec, fontSize = 11.sp) },
                        modifier = Modifier.height(30.dp)
                    )
                }
            }
            // Show filtered below — but since state is local we just store and pass
            // This chip row is visual; the items block below reads the same sorted list
        }

        items(sorted, key = { it.symbol }) { q ->
            VirtualStockRow(q = q, onClick = { onSelect(q) })
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun VirtualStockRow(q: VirtualQuote, onClick: () -> Unit) {
    val up    = q.changePct >= 0
    val color = if (q.circuitHalted) MaterialTheme.colorScheme.error
                else if (up) Color(0xFF2E7D32) else Color(0xFFC62828)

    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {

            // Left: ticker + name + sector badge
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // Colored ticker badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(q.symbol.take(2), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                }
                Column {
                    Text(q.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(q.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(q.stock.sector, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            }

            // Right: price + change
            Column(horizontalAlignment = Alignment.End) {
                Text("◈%.2f".format(q.price), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (q.circuitHalted) {
                    Text("⚡ HALTED", fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
                } else {
                    Text("%s%.2f (%.2f%%)".format(if (up) "▲" else "▼", abs(q.change), abs(q.changePct)),
                        fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
                }
                Text("H:%.0f  L:%.0f".format(q.dayHigh, q.dayLow),
                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        }
    }
}

// ── Stock Detail Sheet (Chart + Trade) ───────────────────────────────────────

@Composable
fun StockDetailSheet(
    quote: VirtualQuote,
    coins: Int,
    chartData: List<Float>,
    onLoadChart: (Int) -> Unit,
    onBuy: (Int) -> BuySellResult,
    onSell: (Float) -> BuySellResult,
    holding: StockHolding?,
    onDismiss: () -> Unit
) {
    var showBuy  by remember { mutableStateOf(false) }
    var showSell by remember { mutableStateOf(false) }
    var message  by remember { mutableStateOf<String?>(null) }
    var period   by remember { mutableIntStateOf(80) }

    LaunchedEffect(period) { onLoadChart(period) }

    val up    = quote.changePct >= 0
    val color = if (up) Color(0xFF2E7D32) else Color(0xFFC62828)

    if (showBuy) {
        BuyDialog(quote = quote, coins = coins,
            onBuy = { gc ->
                val r = onBuy(gc)
                showBuy = false
                message = when (r) {
                    is BuySellResult.Success -> "Bought %.4f shares · ◈${r.fee} brokerage".format(r.qty)
                    is BuySellResult.Error   -> r.message
                }
            },
            onDismiss = { showBuy = false })
    }

    if (showSell && holding != null) {
        SellDialog(holding = holding, currentPrice = quote.price,
            onSell = { qty ->
                val r = onSell(qty)
                showSell = false
                message = when (r) {
                    is BuySellResult.Success -> "Sold %.4f shares · ◈${r.fee} brokerage".format(r.qty)
                    is BuySellResult.Error   -> r.message
                }
            },
            onDismiss = { showSell = false })
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ── Top bar ───────────────────────────────────────────────────────
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(quote.symbol, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(quote.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Text(quote.stock.sector, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("◈%.2f".format(quote.price), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("%s%.2f (%.2f%%)".format(if (up) "▲" else "▼", abs(quote.change), abs(quote.changePct)),
                        fontSize = 13.sp, color = color, modifier = Modifier.padding(bottom = 4.dp), fontWeight = FontWeight.SemiBold)
                }
                if (quote.circuitHalted) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)) {
                        Text("⚡ Circuit Breaker Active — price moved >${CIRCUIT_LIMIT.toInt()}% — trading halted",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Chart ──────────────────────────────────────────────────────
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Period selector
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1H" to 12, "4H" to 48, "1D" to 80, "3D" to 240).forEach { (label, p) ->
                                val sel = period == p
                                TextButton(
                                    onClick = { period = p },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor   = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                ) { Text(label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (chartData.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            PriceChart(prices = chartData, lineColor = color, modifier = Modifier.fillMaxWidth().height(160.dp))
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Low ◈%.2f".format(chartData.min()), fontSize = 10.sp, color = Color(0xFFC62828))
                                Text("High ◈%.2f".format(chartData.max()), fontSize = 10.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }

            // ── Stats row ─────────────────────────────────────────────────
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatCell("Day High",  "◈%.0f".format(quote.dayHigh))
                        VerticalDivider(Modifier.height(36.dp))
                        StatCell("Day Low",   "◈%.0f".format(quote.dayLow))
                        VerticalDivider(Modifier.height(36.dp))
                        StatCell("Prev Close","◈%.2f".format(quote.prevClose))
                    }
                }
            }

            // ── Holding row (if any) ───────────────────────────────────────
            if (holding != null) {
                item {
                    val curVal = holding.quantity * quote.price
                    val pnl    = curVal - holding.totalInvested
                    val pnlClr = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text("Your Position", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%.4f shares".format(holding.quantity), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Avg ◈%.2f · Invested ◈%.0f".format(holding.avgBuyPrice, holding.totalInvested),
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("◈%.0f".format(curVal), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("%s◈%.0f".format(if (pnl >= 0) "+" else "", pnl),
                                    fontSize = 12.sp, color = pnlClr, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Toast message ──────────────────────────────────────────────
            if (message != null) {
                item {
                    Surface(shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(message!!, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { message = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // ── Trade buttons ──────────────────────────────────────────────
            item {
                val tradingRules = buildString {
                    append("Virtual exchange · 0.1% brokerage · Min ◈$MIN_TRADE_GC · Circuit >${CIRCUIT_LIMIT.toInt()}% halts trading")
                }
                Text(tradingRules, fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showBuy = true },
                        enabled = !quote.circuitHalted,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text("Buy", fontWeight = FontWeight.Bold) }

                    if (holding != null && holding.quantity > 0f) {
                        OutlinedButton(
                            onClick = { showSell = true },
                            enabled = !quote.circuitHalted,
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC62828))
                        ) { Text("Sell", fontWeight = FontWeight.Bold, color = Color(0xFFC62828)) }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Price Chart (Canvas) ──────────────────────────────────────────────────────

@Composable
fun PriceChart(prices: List<Float>, lineColor: Color, modifier: Modifier = Modifier) {
    val minP = remember(prices) { prices.min() }
    val maxP = remember(prices) { prices.max() }
    val range = (maxP - minP).coerceAtLeast(0.01f)

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val pad = 4.dp.toPx()

        fun xOf(i: Int) = pad + i.toFloat() / (prices.size - 1).coerceAtLeast(1) * (w - 2 * pad)
        fun yOf(p: Float) = pad + (1f - (p - minP) / range) * (h - 2 * pad)

        // Gradient fill
        val path = Path().apply {
            moveTo(xOf(0), h)
            prices.forEachIndexed { i, p -> lineTo(xOf(i), yOf(p)) }
            lineTo(xOf(prices.size - 1), h); close()
        }
        drawPath(path, Brush.verticalGradient(
            listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f)),
            startY = 0f, endY = h
        ))

        // Grid lines (3 horizontal)
        for (k in 1..3) {
            val y = h * k / 4f
            drawLine(Color.Gray.copy(alpha = 0.12f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // Price line
        for (i in 1 until prices.size) {
            drawLine(lineColor, Offset(xOf(i - 1), yOf(prices[i - 1])), Offset(xOf(i), yOf(prices[i])),
                strokeWidth = 2.5f, cap = StrokeCap.Round)
        }

        // Last price dot
        drawCircle(lineColor, radius = 5f, center = Offset(xOf(prices.size - 1), yOf(prices.last())))
        drawCircle(Color.White, radius = 2.5f, center = Offset(xOf(prices.size - 1), yOf(prices.last())))
    }
}

// ── Buy Dialog ────────────────────────────────────────────────────────────────

@Composable
fun BuyDialog(quote: VirtualQuote, coins: Int, onBuy: (Int) -> Unit, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    val gc      = amountText.toIntOrNull() ?: 0
    val fee     = if (gc > 0) brokerageFee(gc) else 0
    val total   = gc + fee
    val qty     = if (quote.price > 0f) gc.toFloat() / quote.price else 0f
    val canBuy  = gc >= MIN_TRADE_GC && total <= coins

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buy ${quote.symbol}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current price: ◈%.2f per share".format(quote.price), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Invest (GC)") }, placeholder = { Text("Min $MIN_TRADE_GC GC") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )
                if (gc > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TradeRow("You get",      "%.4f shares".format(qty))
                            TradeRow("Invest",        "◈$gc")
                            TradeRow("Brokerage",     "◈$fee (0.1%)", Color(0xFFC62828))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            TradeRow("Total cost",    "◈$total", if (canBuy) Color(0xFF2E7D32) else Color(0xFFC62828))
                            TradeRow("Balance after", "◈${coins - total}")
                        }
                    }
                }
                // Preset buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 50, 100, 500).filter { it <= coins }.forEach { p ->
                        OutlinedButton(onClick = { amountText = p.toString() },
                            modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("$p", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (canBuy) onBuy(gc) }, enabled = canBuy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Buy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TradeRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

// ── Sell Dialog ───────────────────────────────────────────────────────────────

@Composable
fun SellDialog(holding: StockHolding, currentPrice: Float, onSell: (Float) -> Unit, onDismiss: () -> Unit) {
    var qtyText by remember { mutableStateOf("") }
    val qty       = qtyText.toFloatOrNull() ?: 0f
    val proceeds  = (qty * currentPrice).roundToInt()
    val fee       = if (proceeds > 0) brokerageFee(proceeds) else 0
    val net       = proceeds - fee
    val canSell   = qty > 0f && qty <= holding.quantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell ${holding.symbol}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Price: ◈%.2f · You own %.4f shares".format(currentPrice, holding.quantity),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = qtyText, onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Quantity") }, placeholder = { Text("Max %.4f".format(holding.quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                )
                // Preset: 25 / 50 / 75 / 100 %
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 50, 75, 100).forEach { pct ->
                        OutlinedButton(onClick = { qtyText = "%.4f".format(holding.quantity * pct / 100f) },
                            modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("$pct%", fontSize = 11.sp)
                        }
                    }
                }
                if (qty > 0f && canSell) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TradeRow("Proceeds",  "◈$proceeds")
                            TradeRow("Brokerage", "◈$fee (0.1%)", Color(0xFFC62828))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            TradeRow("You receive", "◈$net", Color(0xFF2E7D32))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (canSell) onSell(qty) }, enabled = canSell,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("Sell") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Portfolio Tab ─────────────────────────────────────────────────────────────

@Composable
fun PortfolioTab(vm: BankViewModel, quotes: Map<String, VirtualQuote>) {
    val holdings = remember(quotes) { vm.getHoldings() }

    if (holdings.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.AccountBalance, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text("No holdings yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text("Buy from the Market tab", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // P&L summary card
        item {
            val totalInvested = holdings.values.sumOf { it.totalInvested.toDouble() }.toFloat()
            val currentValue  = holdings.values.sumOf { h ->
                ((quotes[h.symbol]?.price ?: h.avgBuyPrice) * h.quantity).toDouble()
            }.toFloat()
            val pnl    = currentValue - totalInvested
            val pnlPct = if (totalInvested > 0) (pnl / totalInvested) * 100f else 0f
            val clr    = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), Arrangement.SpaceBetween) {
                    Column {
                        Text("Invested", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("◈%.0f".format(totalInvested), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Column {
                        Text("Current", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("◈%.0f".format(currentValue), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("P&L", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("%s◈%.0f (%.1f%%)".format(if (pnl >= 0) "+" else "", pnl, pnlPct),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = clr)
                    }
                }
            }
        }

        items(holdings.values.toList(), key = { it.symbol }) { h ->
            val q      = quotes[h.symbol]
            val price  = q?.price ?: h.avgBuyPrice
            val curVal = price * h.quantity
            val pnl    = curVal - h.totalInvested
            val clr    = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth() as Modifier) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(h.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(h.companyName, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("◈%.2f".format(price), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("%s◈%.0f".format(if (pnl >= 0) "+" else "", pnl), fontSize = 12.sp, color = clr)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("%.4f shares · Avg ◈%.2f · Invested ◈%.0f".format(h.quantity, h.avgBuyPrice, h.totalInvested),
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            }
        }

        // Recent transactions
        val txns = remember { vm.getTransactions().take(15) }
        if (txns.isNotEmpty()) {
            item {
                Text("Recent Transactions", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            items(txns, key = { "${it.symbol}${it.date}${it.type}${it.quantity}" }) { tx ->
                val isBuy = tx.type == "BUY"
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isBuy) "▲" else "▼", color = if (isBuy) Color(0xFF2E7D32) else Color(0xFFC62828), fontSize = 12.sp)
                        Column {
                            Text("${tx.type} ${tx.symbol}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("${tx.date} · qty %.4f".format(tx.quantity), fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    Text("%s◈%.0f".format(if (isBuy) "-" else "+", tx.totalGC), fontSize = 12.sp,
                        color = if (isBuy) Color(0xFFC62828) else Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Loan Tab ──────────────────────────────────────────────────────────────────

@Composable
fun LoanTab(vm: BankViewModel, coins: Int) {
    var showRepayDialog   by remember { mutableStateOf(false) }
    var showConfirmLoan   by remember { mutableStateOf(false) }
    var selectedLoanAmount by remember { mutableIntStateOf(0) }

    val hasLoan   = vm.hasActiveLoan()
    val loanTiers = listOf(50, 100, 200, 500)

    if (showRepayDialog) {
        AlertDialog(
            onDismissRequest = { showRepayDialog = false },
            title = { Text("Repay Loan", fontWeight = FontWeight.Bold) },
            text  = {
                val remaining = vm.getLoanRemaining()
                val canRepay  = coins >= remaining
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Loan remaining: ◈$remaining", fontSize = 13.sp)
                    Text("Your balance: ◈$coins", fontSize = 13.sp,
                        color = if (canRepay) Color(0xFF2E7D32) else Color(0xFFC62828))
                    if (!canRepay) Text("Not enough GC to repay.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                val remaining = vm.getLoanRemaining()
                Button(onClick = { vm.repayLoan(remaining); showRepayDialog = false },
                    enabled = coins >= vm.getLoanRemaining()) { Text("Repay") }
            },
            dismissButton = { TextButton(onClick = { showRepayDialog = false }) { Text("Cancel") } }
        )
    }

    if (showConfirmLoan && selectedLoanAmount > 0) {
        AlertDialog(
            onDismissRequest = { showConfirmLoan = false; selectedLoanAmount = 0 },
            title = { Text("Confirm Loan", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Borrow ◈$selectedLoanAmount GC?", fontSize = 14.sp)
                    Text("Repay within 7 days to avoid XP penalty.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { vm.takeLoan(selectedLoanAmount); showConfirmLoan = false; selectedLoanAmount = 0 }) { Text("Borrow") }
            },
            dismissButton = { TextButton(onClick = { showConfirmLoan = false; selectedLoanAmount = 0 }) { Text("Cancel") } }
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (hasLoan) {
            item {
                val remaining  = vm.getLoanRemaining()
                val amount     = vm.getLoanAmount()
                val repaid     = vm.getLoanRepaidAmount()
                val dueDate    = vm.getLoanDueDate()
                val penalty    = vm.getLoanPenaltyDays()
                val xpPenalty  = vm.getLoanXpPenaltyTotal()
                val progress   = if (amount > 0) repaid.toFloat() / amount else 0f

                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Active Loan", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            StatCell("Borrowed",  "◈$amount")
                            StatCell("Repaid",    "◈$repaid")
                            StatCell("Remaining", "◈$remaining")
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                        Text("Due: $dueDate${if (penalty > 0) " · $penalty days overdue · -$xpPenalty XP" else ""}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { showRepayDialog = true }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Repay Loan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!hasLoan) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Emergency Loan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Borrow GC and repay within 7 days. Overdue loans incur daily XP penalties.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                }
            }
            items(loanTiers) { amount ->
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { selectedLoanAmount = amount; showConfirmLoan = true }) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("◈$amount GC", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Repay ◈$amount within 7 days", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Text("Borrow →", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}
