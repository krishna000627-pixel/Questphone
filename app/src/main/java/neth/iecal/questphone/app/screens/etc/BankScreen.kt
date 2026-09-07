package neth.iecal.questphone.app.screens.etc

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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

// ══════════════════════════════════════════════════════════════════════════════
//  Virtual Stock Data
// ══════════════════════════════════════════════════════════════════════════════

data class VirtualStock(
    val symbol: String,
    val name: String,
    val sector: String,
    val basePrice: Float,
    val volatility: Float,   // per-tick std-dev as fraction of price
    val beta: Float = 1f     // sensitivity to market temperature
)

data class VirtualQuote(
    val stock: VirtualStock,
    val price: Float,
    val openPrice: Float     // price when session started (for % change)
) {
    val symbol     get() = stock.symbol
    val name       get() = stock.name
    val change     get() = price - openPrice
    val changePct  get() = if (openPrice > 0f) (change / openPrice) * 100f else 0f
    val circuitHalted get() = abs(changePct) >= 20f
}

// ══════════════════════════════════════════════════════════════════════════════
//  Stock Generator — 100 procedural stocks, seeded so names are stable
// ══════════════════════════════════════════════════════════════════════════════

object StockGenerator {
    private val prefixes = listOf(
        "Quest","Nexus","Forge","Arc","Nova","Titan","Apex","Vex","Lux","Orb",
        "Flux","Zeta","Kron","Mira","Pyro","Cryo","Echo","Void","Prism","Dusk",
        "Solr","Aeon","Byte","Core","Drift","Edge","Fuse","Grid","Helix","Ion"
    )
    private val suffixes = listOf(
        "Corp","Inc","AI","Tech","Capital","Energy","Systems","Labs","Dynamics","Works"
    )
    private val sectors = listOf(
        "Technology","Finance","Crypto","Gaming","Energy",
        "Auto","Healthcare","Retail","Defense","Media"
    )

    val stocks: List<VirtualStock> by lazy {
        val rng = java.util.Random(0xDEADBEEFL)
        (0 until 100).map { i ->
            val prefix  = prefixes[i % prefixes.size]
            val suffix  = suffixes[i % suffixes.size]
            val sector  = sectors[i % sectors.size]
            // Unique 4-5 char symbol: first 3 of prefix + index char
            val sym = (prefix.take(3) + (if (i < 10) "$i" else "${('A' + i % 26)}")).uppercase()
            VirtualStock(
                symbol    = sym,
                name      = "$prefix $suffix",
                sector    = sector,
                basePrice = 10f + rng.nextFloat() * 4990f,
                volatility= 0.003f + rng.nextFloat() * 0.022f,
                beta      = 0.3f + rng.nextFloat() * 1.7f
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Temperature-Based Simulation
//  · temperature (0..1): shared market mood — same value seen by every stock
//  · Each stock has its OWN Random seeded by symbol hash — uncorrelated noise
//  · Simulation only advances when the screen is open (no time-based drift)
// ══════════════════════════════════════════════════════════════════════════════

data class SimState(
    val temperature: Float = 0.5f,                 // 0=bear … 1=bull
    val prices:  Map<String, Float> = emptyMap(),  // current price per symbol
    val history: Map<String, List<Float>> = emptyMap(), // in-session chart history
    val openPrices: Map<String, Float> = emptyMap()
)

val Float.marketLabel get() = when {
    this >= 0.75f -> "🔥 Bull Run"
    this >= 0.55f -> "📈 Bullish"
    this >= 0.45f -> "➖ Neutral"
    this >= 0.25f -> "📉 Bearish"
    else          -> "❄️ Bear Crash"
}
val Float.tempColor get() = when {
    this >= 0.65f -> Color(0xFF2E7D32)
    this >= 0.45f -> Color(0xFFF9A825)
    else          -> Color(0xFFC62828)
}

// Per-stock seeded Random — deterministic seed per symbol, independent randomness
private val stockRngs = mutableMapOf<String, java.util.Random>()
private fun rngFor(symbol: String) =
    stockRngs.getOrPut(symbol) { java.util.Random(symbol.hashCode().toLong()) }

// Shared temperature Random — same sequence for all, changes every tick
private val tempRng = java.util.Random(0xCAFEBABEL)

fun nextTemperature(current: Float): Float {
    val delta = (tempRng.nextGaussian() * 0.03).toFloat()
    return (current + delta).coerceIn(0f, 1f)
}

fun nextPrice(stock: VirtualStock, currentPrice: Float, temp: Float): Float {
    val rng = rngFor(stock.symbol)
    // Market return driven by temperature (shared across all stocks)
    val marketReturn = (temp - 0.5f) * 0.004f * stock.beta
    // Individual stock noise (independent per stock)
    val idioReturn = rng.nextGaussian().toFloat() * stock.volatility
    return (currentPrice * (1f + marketReturn + idioReturn))
        .coerceAtLeast(stock.basePrice * 0.01f)
}

// ══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ══════════════════════════════════════════════════════════════════════════════

const val BROKERAGE_PCT  = 0.001f
const val MIN_TRADE_GC   = 1
const val CIRCUIT_LIMIT  = 20f
const val TICK_MS        = 3_000L    // price update every 3 seconds
const val MAX_HISTORY    = 150       // points kept per stock for chart

fun brokerageFee(gc: Int) = (gc * BROKERAGE_PCT).roundToInt().coerceAtLeast(1)

sealed class BuySellResult {
    data class Success(val qty: Float, val fee: Int) : BuySellResult()
    data class Error(val message: String) : BuySellResult()
}

@HiltViewModel
class BankViewModel @Inject constructor(val userRepository: UserRepository) : ViewModel() {

    val coinsState = userRepository.coinsState.asStateFlow()

    private val _sim = MutableStateFlow(SimState())
    val sim = _sim.asStateFlow()

    private val _selected = MutableStateFlow<VirtualQuote?>(null)
    val selected = _selected.asStateFlow()

    private val _portfolioValue = MutableStateFlow(0f)
    val portfolioValue = _portfolioValue.asStateFlow()

    private var tickJob: Job? = null

    // Initialise prices from base prices on first launch
    init {
        val initPrices = StockGenerator.stocks.associate { s -> s.symbol to s.basePrice }
        _sim.value = SimState(
            temperature = 0.5f,
            prices      = initPrices,
            openPrices  = initPrices,
            history     = StockGenerator.stocks.associate { s -> s.symbol to listOf(s.basePrice) }
        )
    }

    // ── Simulation control ─────────────────────────────────────────────────

    fun startSim() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    fun stopSim() { tickJob?.cancel(); tickJob = null }

    private fun tick() {
        val old  = _sim.value
        val temp = nextTemperature(old.temperature)

        val newPrices  = mutableMapOf<String, Float>()
        val newHistory = mutableMapOf<String, List<Float>>()

        for (stock in StockGenerator.stocks) {
            val sym      = stock.symbol
            val oldPrice = old.prices[sym] ?: stock.basePrice
            val np       = nextPrice(stock, oldPrice, temp)
            newPrices[sym] = np
            val hist = (old.history[sym] ?: listOf(oldPrice)) + np
            newHistory[sym] = if (hist.size > MAX_HISTORY) hist.takeLast(MAX_HISTORY) else hist
        }

        _sim.value = old.copy(temperature = temp, prices = newPrices, history = newHistory)

        // Keep selected quote live
        _selected.value?.let { sel ->
            _selected.value = sel.copy(price = newPrices[sel.symbol] ?: sel.price)
        }
        recalcPortfolio(newPrices)
    }

    fun selectStock(symbol: String) {
        val stock = StockGenerator.stocks.firstOrNull { it.symbol == symbol } ?: return
        val price = _sim.value.prices[symbol] ?: stock.basePrice
        val open  = _sim.value.openPrices[symbol] ?: price
        _selected.value = VirtualQuote(stock, price, open)
    }

    fun clearSelection() { _selected.value = null }

    private fun recalcPortfolio(prices: Map<String, Float>) {
        _portfolioValue.value = userRepository.getStockHoldings().entries.sumOf { (sym, h) ->
            ((prices[sym] ?: h.avgBuyPrice) * h.quantity).toDouble()
        }.toFloat()
    }

    // ── Trading ────────────────────────────────────────────────────────────

    fun buyStock(symbol: String, gcAmount: Int): BuySellResult {
        val q = _selected.value?.takeIf { it.symbol == symbol }
            ?: return BuySellResult.Error("Stock not found")
        if (q.circuitHalted) return BuySellResult.Error("Circuit breaker — trading halted")
        if (gcAmount < MIN_TRADE_GC) return BuySellResult.Error("Minimum ◈$MIN_TRADE_GC")
        val fee   = brokerageFee(gcAmount)
        val total = gcAmount + fee
        if (total > (userRepository.coinsState.value ?: 0))
            return BuySellResult.Error("Need ◈$total (incl. ◈$fee brokerage)")
        val qty = gcAmount.toFloat() / q.price
        return if (userRepository.buyStock(symbol, q.name, qty, q.price)) {
            recalcPortfolio(_sim.value.prices); BuySellResult.Success(qty, fee)
        } else BuySellResult.Error("Transaction failed")
    }

    fun sellStock(symbol: String, qty: Float): BuySellResult {
        val q = _selected.value?.takeIf { it.symbol == symbol }
            ?: return BuySellResult.Error("Stock not found")
        if (q.circuitHalted) return BuySellResult.Error("Circuit breaker — trading halted")
        val proceeds = (qty * q.price).roundToInt()
        val fee = brokerageFee(proceeds)
        return if (userRepository.sellStock(symbol, qty, q.price)) {
            recalcPortfolio(_sim.value.prices); BuySellResult.Success(qty, fee)
        } else BuySellResult.Error("Not enough shares")
    }

    fun getHoldings(): Map<String, StockHolding>         = userRepository.getStockHoldings()
    fun getTransactions(): List<TradeTransaction>         = userRepository.getTradingTransactions()
    fun takeLoan(amount: Int)                             = userRepository.takeLoan(amount)
    fun repayLoan(amount: Int)                            = userRepository.repayLoan(amount)
    fun hasActiveLoan()                                   = userRepository.hasActiveLoan()
    fun getLoanRemaining()                                = userRepository.getLoanRemaining()
    fun getLoanAmount()                                   = userRepository.getLoanAmount()
    fun getLoanRepaidAmount()                             = userRepository.getLoanRepaidAmount()
    fun getLoanDueDate()                                  = userRepository.getLoanDueDate()
    fun getLoanPenaltyDays()                              = userRepository.getLoanPenaltyDays()
    fun isLoanStoreLocked()                               = userRepository.isLoanStoreLocked()
    fun getLoanXpPenaltyTotal()                           = userRepository.userInfo.loanXpPenaltyTotal
}

// ══════════════════════════════════════════════════════════════════════════════
//  Main BankScreen
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun BankScreen(modifier: Modifier = Modifier, vm: BankViewModel = hiltViewModel()) {
    val coins          by vm.coinsState.collectAsState()
    val sim            by vm.sim.collectAsState()
    val selected       by vm.selected.collectAsState()
    val portfolioValue by vm.portfolioValue.collectAsState()
    var selectedTab    by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) { vm.startSim(); onDispose { vm.stopSim() } }

    // Intercept system back when stock overlay is showing — go back to list, not app drawer
    BackHandler(enabled = selected != null) { vm.clearSelection() }

    // Stock detail overlay
    if (selected != null) {
        StockDetailSheet(
            quote     = selected!!,
            coins     = coins ?: 0,
            history   = sim.history[selected!!.symbol] ?: emptyList(),
            holding   = vm.getHoldings()[selected!!.symbol],
            onBuy     = { gc -> vm.buyStock(selected!!.symbol, gc) },
            onSell    = { qty -> vm.sellStock(selected!!.symbol, qty) },
            onDismiss = { vm.clearSelection() }
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {

        // Balance + temperature header
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("GC Balance", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("◈ ${coins ?: 0} GC", fontSize = 24.sp,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (portfolioValue > 0f)
                            Text("+ ◈%.0f invested".format(portfolioValue), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    }
                    // Temperature gauge
                    TemperatureGauge(temp = sim.temperature)
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary) {
            listOf("📊 Market", "💼 Portfolio", "🏦 Loan").forEachIndexed { i, label ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(label, fontSize = 12.sp,
                        fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) })
            }
        }

        AnimatedContent(targetState = selectedTab, label = "bank_tab") { tab ->
            when (tab) {
                0 -> MarketTab(sim = sim, onSelect = { vm.selectStock(it) })
                1 -> PortfolioTab(vm = vm, sim = sim)
                2 -> LoanTab(vm = vm, coins = coins ?: 0)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Temperature Gauge
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun TemperatureGauge(temp: Float) {
    Column(horizontalAlignment = Alignment.End) {
        Text(temp.marketLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = temp.tempColor)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(temp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF2E7D32)))
                    )
            )
        }
        Text("Mkt Temp %.0f%%".format(temp * 100), fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Market Tab — lists all 100 stocks, live-updating
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MarketTab(sim: SimState, onSelect: (String) -> Unit) {
    var sectorFilter by remember { mutableStateOf("All") }
    var searchQuery  by remember { mutableStateOf("") }

    val sectors = remember { listOf("All") + StockGenerator.stocks.map { it.sector }.distinct().sorted() }

    val visible = remember(sim.prices, sectorFilter, searchQuery) {
        StockGenerator.stocks
            .filter { s ->
                (sectorFilter == "All" || s.sector == sectorFilter) &&
                (searchQuery.isBlank() || s.symbol.contains(searchQuery, ignoreCase = true)
                        || s.name.contains(searchQuery, ignoreCase = true))
            }
            .sortedByDescending { s ->
                val p = sim.prices[s.symbol] ?: s.basePrice
                val o = sim.openPrices[s.symbol] ?: p
                abs(if (o > 0) (p - o) / o else 0f)
            }
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

        // Search bar
        item {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Search 100 stocks…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Sector filter chips
        item {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sectors) { sec ->
                    FilterChip(selected = sectorFilter == sec, onClick = { sectorFilter = sec },
                        label = { Text(sec, fontSize = 11.sp) }, modifier = Modifier.height(30.dp))
                }
            }
        }

        item {
            Text("${visible.size} stocks · updates every ${TICK_MS / 1000}s while open",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        }

        items(visible, key = { it.symbol }) { stock ->
            val price    = sim.prices[stock.symbol] ?: stock.basePrice
            val open     = sim.openPrices[stock.symbol] ?: price
            val chgPct   = if (open > 0f) (price - open) / open * 100f else 0f
            val up       = chgPct >= 0
            val halted   = abs(chgPct) >= CIRCUIT_LIMIT
            val color    = if (halted) MaterialTheme.colorScheme.error
                           else if (up) Color(0xFF2E7D32) else Color(0xFFC62828)

            // Sparkline: last 20 history points for this stock
            val spark = sim.history[stock.symbol]?.takeLast(20) ?: emptyList()

            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable { onSelect(stock.symbol) }) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically) {

                    // Symbol badge
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.1f)),
                        Alignment.Center
                    ) { Text(stock.symbol.take(2), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color) }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(stock.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(stock.name, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stock.sector, fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    }

                    // Mini sparkline
                    if (spark.size >= 2) {
                        MiniSparkline(prices = spark, color = color,
                            modifier = Modifier.width(48.dp).height(24.dp))
                        Spacer(Modifier.width(8.dp))
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("◈%.2f".format(price), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (halted) Text("⚡ HALT", fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
                        else Text("%s%.2f%%".format(if (up) "▲" else "▼", abs(chgPct)),
                            fontSize = 11.sp, color = color)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Mini Sparkline (on stock row)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MiniSparkline(prices: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val mn = prices.min(); val mx = prices.max()
        val rng = (mx - mn).coerceAtLeast(0.001f)
        fun x(i: Int) = i.toFloat() / (prices.size - 1) * w
        fun y(p: Float) = h - (p - mn) / rng * h
        for (i in 1 until prices.size)
            drawLine(color, Offset(x(i-1), y(prices[i-1])), Offset(x(i), y(prices[i])),
                strokeWidth = 1.5f, cap = StrokeCap.Round)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Stock Detail Sheet — chart + trade
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun StockDetailSheet(
    quote: VirtualQuote, coins: Int, history: List<Float>,
    holding: StockHolding?,
    onBuy: (Int) -> BuySellResult, onSell: (Float) -> BuySellResult,
    onDismiss: () -> Unit
) {
    var showBuy  by remember { mutableStateOf(false) }
    var showSell by remember { mutableStateOf(false) }
    var message  by remember { mutableStateOf<String?>(null) }
    var periods  by remember { mutableIntStateOf(80) }

    val up    = quote.changePct >= 0
    val color = if (quote.circuitHalted) MaterialTheme.colorScheme.error
                else if (up) Color(0xFF2E7D32) else Color(0xFFC62828)
    val chartPrices = history.takeLast(periods).takeIf { it.size >= 2 } ?: history

    if (showBuy)  BuyDialog(quote, coins, onBuy  = { gc  -> val r = onBuy(gc);   showBuy  = false; message = r.toMsg() }, onDismiss = { showBuy = false })
    if (showSell && holding != null) SellDialog(holding, quote.price, onSell = { qty -> val r = onSell(qty); showSell = false; message = r.toMsg() }, onDismiss = { showSell = false })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(quote.symbol, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(quote.name, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Text(quote.stock.sector, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("◈%.2f".format(quote.price), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("%s%.2f (%.2f%%)".format(if (up) "▲" else "▼", abs(quote.change), abs(quote.changePct)),
                        fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                if (quote.circuitHalted)
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
                        Text("⚡ Circuit Breaker — >${CIRCUIT_LIMIT.toInt()}% move — trading halted", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp, 4.dp))
                    }
            }
        }

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Chart
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("20" to 20, "50" to 50, "All" to MAX_HISTORY).forEach { (lbl, p) ->
                                val sel = periods == p
                                TextButton(onClick = { periods = p }, modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor   = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )) { Text(lbl, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        if (chartPrices.size < 2) {
                            Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
                                Text("Waiting for data…", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                            }
                        } else {
                            PriceChart(prices = chartPrices, lineColor = color,
                                modifier = Modifier.fillMaxWidth().height(160.dp))
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Low ◈%.2f".format(chartPrices.min()), fontSize = 10.sp, color = Color(0xFFC62828))
                                Text("Pts: ${chartPrices.size} · live every ${TICK_MS/1000}s", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                                Text("High ◈%.2f".format(chartPrices.max()), fontSize = 10.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }

            // Stats
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), Arrangement.SpaceEvenly) {
                        StatCell("Session Open", "◈%.2f".format(quote.openPrice))
                        VerticalDivider(Modifier.height(36.dp))
                        StatCell("Beta", "%.2f".format(quote.stock.beta))
                        VerticalDivider(Modifier.height(36.dp))
                        StatCell("Volatility", "%.1f%%".format(quote.stock.volatility * 100))
                    }
                }
            }

            // Holding
            if (holding != null) {
                item {
                    val curVal = holding.quantity * quote.price
                    val pnl    = curVal - holding.totalInvested
                    val pnlClr = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text("Your Position", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%.4f shares".format(holding.quantity), fontWeight = FontWeight.SemiBold)
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

            // Message toast
            if (message != null) {
                item {
                    Surface(shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(message!!, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { message = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Trade buttons
            item {
                Text("Virtual exchange · 0.1% brokerage · Circuit >${CIRCUIT_LIMIT.toInt()}% halts · Session-only prices",
                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showBuy = true }, enabled = !quote.circuitHalted, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                        Text("Buy", fontWeight = FontWeight.Bold)
                    }
                    if (holding != null && holding.quantity > 0f)
                        OutlinedButton(onClick = { showSell = true }, enabled = !quote.circuitHalted, modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC62828))) {
                            Text("Sell", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun BuySellResult.toMsg() = when (this) {
    is BuySellResult.Success -> "Done · %.4f shares · ◈$fee brokerage".format(qty)
    is BuySellResult.Error   -> message
}

// ══════════════════════════════════════════════════════════════════════════════
//  Price Chart (Canvas)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PriceChart(prices: List<Float>, lineColor: Color, modifier: Modifier = Modifier) {
    val mn = remember(prices) { prices.min() }
    val mx = remember(prices) { prices.max() }
    val rng = (mx - mn).coerceAtLeast(0.01f)

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val pad = 4.dp.toPx()
        fun xi(i: Int) = pad + i.toFloat() / (prices.size - 1).coerceAtLeast(1) * (w - 2 * pad)
        fun yp(p: Float) = pad + (1f - (p - mn) / rng) * (h - 2 * pad)

        // Gradient fill
        val path = Path().apply {
            moveTo(xi(0), h)
            prices.forEachIndexed { i, p -> lineTo(xi(i), yp(p)) }
            lineTo(xi(prices.size - 1), h); close()
        }
        drawPath(path, Brush.verticalGradient(
            listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0f)), 0f, h))

        // Grid
        repeat(3) { k ->
            drawLine(Color.Gray.copy(alpha = 0.1f), Offset(0f, h * (k + 1) / 4f), Offset(w, h * (k + 1) / 4f), 1f)
        }

        // Line
        for (i in 1 until prices.size)
            drawLine(lineColor, Offset(xi(i-1), yp(prices[i-1])), Offset(xi(i), yp(prices[i])),
                2.5f, cap = StrokeCap.Round)

        // Dot
        drawCircle(lineColor, 5f, Offset(xi(prices.size-1), yp(prices.last())))
        drawCircle(Color.White, 2.5f, Offset(xi(prices.size-1), yp(prices.last())))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Shared helpers
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TradeRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Buy / Sell Dialogs
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun BuyDialog(quote: VirtualQuote, coins: Int, onBuy: (Int) -> Unit, onDismiss: () -> Unit) {
    var amt by remember { mutableStateOf("") }
    val gc    = amt.toIntOrNull() ?: 0
    val fee   = if (gc > 0) brokerageFee(gc) else 0
    val total = gc + fee
    val qty   = if (quote.price > 0f) gc.toFloat() / quote.price else 0f
    val ok    = gc >= MIN_TRADE_GC && total <= coins

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Buy ${quote.symbol}", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Price: ◈%.2f".format(quote.price), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = amt, onValueChange = { amt = it.filter(Char::isDigit) },
                    label = { Text("Invest (GC)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                if (gc > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TradeRow("Shares",    "%.4f".format(qty))
                            TradeRow("Invest",    "◈$gc")
                            TradeRow("Brokerage", "◈$fee", Color(0xFFC62828))
                            HorizontalDivider(Modifier.padding(vertical = 2.dp))
                            TradeRow("Total",     "◈$total", if (ok) Color(0xFF2E7D32) else Color(0xFFC62828))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 50, 100, 500).filter { it <= coins }.forEach { p ->
                        OutlinedButton(onClick = { amt = p.toString() },
                            modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("$p", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (ok) onBuy(gc) }, enabled = ok,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Buy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SellDialog(holding: StockHolding, currentPrice: Float, onSell: (Float) -> Unit, onDismiss: () -> Unit) {
    var qtyTxt by remember { mutableStateOf("") }
    val qty      = qtyTxt.toFloatOrNull() ?: 0f
    val proceeds = (qty * currentPrice).roundToInt()
    val fee      = if (proceeds > 0) brokerageFee(proceeds) else 0
    val net      = proceeds - fee
    val ok       = qty > 0f && qty <= holding.quantity

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Sell ${holding.symbol}", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("◈%.2f · Own %.4f shares".format(currentPrice, holding.quantity), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = qtyTxt, onValueChange = { qtyTxt = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 50, 75, 100).forEach { pct ->
                        OutlinedButton(onClick = { qtyTxt = "%.4f".format(holding.quantity * pct / 100f) },
                            modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("$pct%", fontSize = 11.sp)
                        }
                    }
                }
                if (ok) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TradeRow("Proceeds",  "◈$proceeds")
                        TradeRow("Brokerage", "◈$fee", Color(0xFFC62828))
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        TradeRow("Receive",   "◈$net", Color(0xFF2E7D32))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (ok) onSell(qty) }, enabled = ok,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("Sell") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ══════════════════════════════════════════════════════════════════════════════
//  Portfolio Tab
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PortfolioTab(vm: BankViewModel, sim: SimState) {
    val holdings = vm.getHoldings()

    if (holdings.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountBalance, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text("No holdings yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }; return
    }

    val txns = remember { vm.getTransactions().take(15) }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Summary
        item {
            val inv = holdings.values.sumOf { it.totalInvested.toDouble() }.toFloat()
            val cur = holdings.values.sumOf { h -> ((sim.prices[h.symbol] ?: h.avgBuyPrice) * h.quantity).toDouble() }.toFloat()
            val pnl = cur - inv; val pct = if (inv > 0) pnl / inv * 100f else 0f
            val clr = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), Arrangement.SpaceBetween) {
                    StatCell("Invested", "◈%.0f".format(inv))
                    StatCell("Current",  "◈%.0f".format(cur))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("P&L", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("%s◈%.0f (%.1f%%)".format(if (pnl >= 0) "+" else "", pnl, pct),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = clr)
                    }
                }
            }
        }

        items(holdings.values.toList(), key = { it.symbol }) { h ->
            val price = sim.prices[h.symbol] ?: h.avgBuyPrice
            val curVal = price * h.quantity; val pnl = curVal - h.totalInvested
            val clr = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable { vm.selectStock(h.symbol) }) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(h.symbol, fontWeight = FontWeight.Bold)
                            Text(h.companyName, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("◈%.2f".format(price), fontWeight = FontWeight.SemiBold)
                            Text("%s◈%.0f".format(if (pnl >= 0) "+" else "", pnl), fontSize = 12.sp, color = clr)
                        }
                    }
                    Text("%.4f shares · Avg ◈%.2f".format(h.quantity, h.avgBuyPrice), fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        if (txns.isNotEmpty()) {
            item { Text("Recent Transactions", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            items(txns, key = { "${it.symbol}${it.date}${it.type}${it.quantity}" }) { tx ->
                val buy = tx.type == "BUY"
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (buy) "▲" else "▼", color = if (buy) Color(0xFF2E7D32) else Color(0xFFC62828))
                        Column {
                            Text("${tx.type} ${tx.symbol}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("${tx.date} · %.4f shares".format(tx.quantity), fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    Text("%s◈%.0f".format(if (buy) "-" else "+", tx.totalGC), fontSize = 12.sp,
                        color = if (buy) Color(0xFFC62828) else Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Loan Tab
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun LoanTab(vm: BankViewModel, coins: Int) {
    var showRepay         by remember { mutableStateOf(false) }
    var showConfirm       by remember { mutableStateOf(false) }
    var selectedLoanAmount by remember { mutableIntStateOf(0) }
    val hasLoan = vm.hasActiveLoan()

    if (showRepay) AlertDialog(onDismissRequest = { showRepay = false },
        title = { Text("Repay Loan", fontWeight = FontWeight.Bold) },
        text  = {
            val rem = vm.getLoanRemaining(); val canRepay = coins >= rem
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Remaining: ◈$rem"); Text("Balance: ◈$coins",
                    color = if (canRepay) Color(0xFF2E7D32) else Color(0xFFC62828))
            }
        },
        confirmButton = { Button(onClick = { vm.repayLoan(vm.getLoanRemaining()); showRepay = false },
            enabled = coins >= vm.getLoanRemaining()) { Text("Repay") } },
        dismissButton = { TextButton(onClick = { showRepay = false }) { Text("Cancel") } })

    if (showConfirm && selectedLoanAmount > 0) AlertDialog(onDismissRequest = { showConfirm = false },
        title = { Text("Confirm Loan", fontWeight = FontWeight.Bold) },
        text  = { Text("Borrow ◈$selectedLoanAmount? Repay within 7 days.") },
        confirmButton = { Button(onClick = { vm.takeLoan(selectedLoanAmount); showConfirm = false }) { Text("Borrow") } },
        dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } })

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasLoan) {
            item {
                val rem = vm.getLoanRemaining(); val amt = vm.getLoanAmount()
                val rep = vm.getLoanRepaidAmount(); val due = vm.getLoanDueDate()
                val pen = vm.getLoanPenaltyDays(); val xp  = vm.getLoanXpPenaltyTotal()
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Active Loan", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                            StatCell("Borrowed", "◈$amt"); StatCell("Repaid", "◈$rep"); StatCell("Left", "◈$rem")
                        }
                        LinearProgressIndicator(progress = { if (amt > 0) rep.toFloat() / amt else 0f },
                            modifier = Modifier.fillMaxWidth())
                        Text("Due: $due${if (pen > 0) " · $pen days overdue · -$xp XP" else ""}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { showRepay = true }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Repay Loan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Emergency Loan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Borrow GC · repay within 7 days · overdue = daily XP penalty",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                }
            }
            items(listOf(50, 100, 200, 500)) { amount ->
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { selectedLoanAmount = amount; showConfirm = true }) {
                    Row(Modifier.padding(16.dp, 14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
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
