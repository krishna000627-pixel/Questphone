package neth.iecal.questphone.app.screens.etc

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.StockHolding
import nethical.questphone.data.TradeTransaction
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject

// ── ViewModel ──────────────────────────────────────────────────────────────

@HiltViewModel
class BankViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {

    val coinsState = userRepository.coinsState.asStateFlow()

    private val _stockQuotes = MutableStateFlow<Map<String, StockQuote>>(emptyMap())
    val stockQuotes = _stockQuotes.asStateFlow()

    private val _isLoadingQuotes = MutableStateFlow(false)
    val isLoadingQuotes = _isLoadingQuotes.asStateFlow()

    private val _portfolioValue = MutableStateFlow(0f)
    val portfolioValue = _portfolioValue.asStateFlow()

    // Popular NSE stocks for discovery
    val popularStocks = listOf(
        "RELIANCE.NS", "TCS.NS", "INFY.NS", "HDFCBANK.NS", "ICICIBANK.NS",
        "HINDUNILVR.NS", "ITC.NS", "SBIN.NS", "BAJFINANCE.NS", "WIPRO.NS",
        "ADANIENT.NS", "TATAMOTORS.NS", "AXISBANK.NS", "MARUTI.NS", "SUNPHARMA.NS"
    )

    init {
        refreshQuotes()
    }

    fun refreshQuotes() {
        val symbols = (userRepository.getStockHoldings().keys + popularStocks).toSet()
        viewModelScope.launch {
            _isLoadingQuotes.value = true
            try {
                val results = fetchBatchQuotes(symbols)
                _stockQuotes.value = results
                recalcPortfolioValue(results)
            } catch (_: Exception) {}
            _isLoadingQuotes.value = false
        }
    }

    private suspend fun fetchBatchQuotes(symbols: Set<String>): Map<String, StockQuote> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, StockQuote>()
            try {
                val symbolsParam = symbols.joinToString(",")
                val url = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=$symbolsParam&fields=regularMarketPrice,regularMarketChange,regularMarketChangePercent,longName,shortName"
                val json = org.json.JSONObject(URL(url).readText())
                val quoteResponse = json.getJSONObject("quoteResponse")
                val result = quoteResponse.getJSONArray("result")
                for (i in 0 until result.length()) {
                    val q = result.getJSONObject(i)
                    val symbol = q.optString("symbol", "")
                    val price = q.optDouble("regularMarketPrice", 0.0).toFloat()
                    val change = q.optDouble("regularMarketChange", 0.0).toFloat()
                    val changePct = q.optDouble("regularMarketChangePercent", 0.0).toFloat()
                    val name = q.optString("longName", "").ifBlank { q.optString("shortName", symbol.removeSuffix(".NS")) }
                    if (symbol.isNotEmpty() && price > 0f) {
                        results[symbol] = StockQuote(symbol, name, price, change, changePct)
                    }
                }
            } catch (_: Exception) {}
            results
        }

    private fun recalcPortfolioValue(quotes: Map<String, StockQuote>) {
        val holdings = userRepository.getStockHoldings()
        var total = 0f
        holdings.forEach { (sym, holding) ->
            val price = quotes[sym]?.price ?: holding.avgBuyPrice
            total += holding.quantity * price
        }
        _portfolioValue.value = total
    }

    fun buyStock(symbol: String, companyName: String, quantity: Float): Boolean {
        val price = _stockQuotes.value[symbol]?.price ?: return false
        val ok = userRepository.buyStock(symbol, companyName, quantity, price)
        if (ok) recalcPortfolioValue(_stockQuotes.value)
        return ok
    }

    fun sellStock(symbol: String, quantity: Float): Boolean {
        val price = _stockQuotes.value[symbol]?.price ?: return false
        val ok = userRepository.sellStock(symbol, quantity, price)
        if (ok) recalcPortfolioValue(_stockQuotes.value)
        return ok
    }

    fun getHoldings(): Map<String, nethical.questphone.data.StockHolding> = userRepository.getStockHoldings()
    fun getTransactions(): List<nethical.questphone.data.TradeTransaction> = userRepository.getTradingTransactions()
    fun takeLoan(amount: Int) = userRepository.takeLoan(amount)
    fun repayLoan(amount: Int) = userRepository.repayLoan(amount)
    fun hasActiveLoan() = userRepository.hasActiveLoan()
    fun getLoanRemaining() = userRepository.getLoanRemaining()
    fun getLoanAmount() = userRepository.getLoanAmount()
    fun getLoanRepaidAmount() = userRepository.getLoanRepaidAmount()
    fun getLoanDueDate() = userRepository.getLoanDueDate()
    fun getLoanPenaltyDays() = userRepository.getLoanPenaltyDays()
    fun isLoanStoreLocked() = userRepository.isLoanStoreLocked()
    fun getLoanXpPenaltyTotal() = userRepository.userInfo.loanXpPenaltyTotal
    private val _searchResults = MutableStateFlow<List<StockQuote>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchStocks(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400) // debounce
            _isSearching.value = true
            try {
                val results = withContext(Dispatchers.IO) {
                    searchYahooFinance(query)
                }
                // Fetch live prices for search results
                if (results.isNotEmpty()) {
                    val symbols = results.map { it.symbol }.toSet()
                    val quotes = fetchBatchQuotes(symbols)
                    _searchResults.value = results.map { r ->
                        quotes[r.symbol] ?: r
                    }
                } else {
                    _searchResults.value = emptyList()
                }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    private suspend fun searchYahooFinance(query: String): List<StockQuote> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://query1.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=10&newsCount=0&enableFuzzyQuery=true&quotesQueryId=tss_match_phrase_query"
                val json = org.json.JSONObject(URL(url).readText())
                val quotes = json.optJSONArray("quotes") ?: return@withContext emptyList()
                val results = mutableListOf<StockQuote>()
                for (i in 0 until quotes.length()) {
                    val q = quotes.getJSONObject(i)
                    val symbol = q.optString("symbol", "")
                    val name = q.optString("longname", "").ifBlank { q.optString("shortname", symbol) }
                    val type = q.optString("quoteType", "")
                    // Only show equities and ETFs
                    if (symbol.isNotEmpty() && type in listOf("EQUITY", "ETF", "MUTUALFUND")) {
                        results.add(StockQuote(symbol = symbol, name = name, price = 0f, change = 0f, changePct = 0f))
                    }
                }
                results
            } catch (_: Exception) { emptyList() }
        }
}

data class StockQuote(
    val symbol: String,
    val name: String,
    val price: Float,
    val change: Float,
    val changePct: Float
)

// ── Main BankScreen ─────────────────────────────────────────────────────────

@Composable
fun BankScreen(modifier: Modifier = Modifier, vm: BankViewModel = hiltViewModel()) {

    val coins by vm.coinsState.collectAsState()
    val quotes by vm.stockQuotes.collectAsState()
    val isLoading by vm.isLoadingQuotes.collectAsState()
    val portfolioValue by vm.portfolioValue.collectAsState()
    val holdings: Map<String, nethical.questphone.data.StockHolding> = remember(quotes) { vm.getHoldings() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("📊 Market", "💼 Portfolio", "🏦 Loan")

    Column(modifier = modifier.fillMaxSize()) {

        // Balance header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("GC Balance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "◈ $coins GC",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (portfolioValue > 0f) {
                        Text(
                            "+ ₹%.0f invested".format(portfolioValue),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                Text("1 GC = 1 INR · Paper Trading", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = { Text(label, fontSize = 12.sp, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        AnimatedContent(targetState = selectedTab, label = "bank_tab") { tab ->
            when (tab) {
                0 -> MarketTab(vm, quotes, isLoading, coins)
                1 -> PortfolioTab(vm, holdings, quotes)
                2 -> LoanTab(vm, coins)
            }
        }
    }
}

// ── Market Tab ──────────────────────────────────────────────────────────────

@Composable
fun MarketTab(vm: BankViewModel, quotes: Map<String, StockQuote>, isLoading: Boolean, coins: Int) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStock by remember { mutableStateOf<StockQuote?>(null) }
    var showBuyDialog by remember { mutableStateOf(false) }

    val searchResults by vm.searchResults.collectAsState()
    val isSearching by vm.isSearching.collectAsState()

    // Trigger live search on query change
    LaunchedEffect(searchQuery) {
        vm.searchStocks(searchQuery)
    }

    // What to show: search results when typing, popular stocks when idle
    val isSearchMode = searchQuery.isNotBlank()
    val displayList = if (isSearchMode) {
        searchResults
    } else {
        quotes.values.toList().sortedByDescending { kotlin.math.abs(it.changePct) }
    }

    if (showBuyDialog && selectedStock != null) {
        BuyDialog(
            stock = selectedStock!!,
            coins = coins,
            onBuy = { qty ->
                vm.buyStock(selectedStock!!.symbol, selectedStock!!.name, qty)
                showBuyDialog = false
            },
            onDismiss = { showBuyDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search any stock worldwide…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isSearchMode) "Search results" else "NSE Popular Stocks · Live prices",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            when {
                isSearching -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                isLoading -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                !isSearchMode -> TextButton(onClick = { vm.refreshQuotes() }, contentPadding = PaddingValues(4.dp)) {
                    Text("Refresh", fontSize = 11.sp)
                }
            }
        }
        if (isSearchMode && searchResults.isEmpty() && !isSearching && searchQuery.length > 1) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results for \"$searchQuery\"", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayList, key = { it.symbol }) { stock ->
                    StockRow(
                        stock = stock,
                        showLoadingPrice = isSearchMode && stock.price == 0f,
                        onClick = {
                            if (stock.price > 0f) {
                                selectedStock = stock
                                showBuyDialog = true
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun StockRow(stock: StockQuote, showLoadingPrice: Boolean = false, onClick: () -> Unit) {
    val isPositive = stock.changePct >= 0
    val changeColor = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stock.symbol.removeSuffix(".NS"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(stock.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showLoadingPrice) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text("₹%.2f".format(stock.price), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (stock.price > 0f) {
                        Text(
                            "%s%.2f (%.2f%%)".format(if (isPositive) "▲" else "▼", kotlin.math.abs(stock.change), kotlin.math.abs(stock.changePct)),
                            fontSize = 11.sp, color = changeColor, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BuyDialog(stock: StockQuote, coins: Int, onBuy: (Float) -> Unit, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toIntOrNull() ?: 0
    val qty = if (stock.price > 0f) amount.toFloat() / stock.price else 0f
    val canAfford = amount > 0 && amount <= coins

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buy ${stock.symbol.removeSuffix(".NS")}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Price: ₹%.2f per share".format(stock.price), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Invest (GC)") },
                    placeholder = { Text("e.g. 10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                if (amount > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("You get", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%.6f shares".format(qty), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Cost", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$amount GC", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Balance after", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${coins - amount} GC", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 50, 100).forEach { preset ->
                        if (preset <= coins) {
                            OutlinedButton(
                                onClick = { amountText = preset.toString() },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) { Text("$preset GC", fontSize = 11.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (canAfford && qty > 0f) onBuy(qty) }, enabled = canAfford && qty > 0f) { Text("Buy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Portfolio Tab ────────────────────────────────────────────────────────────

@Composable
fun PortfolioTab(vm: BankViewModel, holdings: Map<String, StockHolding>, quotes: Map<String, StockQuote>) {
    var showSellDialog by remember { mutableStateOf<StockHolding?>(null) }

    if (showSellDialog != null) {
        val h = showSellDialog!!
        SellDialog(
            holding = h,
            currentPrice = quotes[h.symbol]?.price ?: h.avgBuyPrice,
            onSell = { qty -> vm.sellStock(h.symbol, qty); showSellDialog = null },
            onDismiss = { showSellDialog = null }
        )
    }

    if (holdings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text("No holdings yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text("Buy stocks from the Market tab", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // P&L summary
        item {
            val totalInvested = holdings.values.sumOf { it.totalInvested.toDouble() }.toFloat()
            val currentValue = holdings.values.sumOf { h -> ((quotes[h.symbol]?.price ?: h.avgBuyPrice) * h.quantity).toDouble() }.toFloat()
            val pnl = currentValue - totalInvested
            val pnlPct = if (totalInvested > 0) (pnl / totalInvested) * 100f else 0f
            val pnlColor = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Invested", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("₹%.0f GC".format(totalInvested), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("P&L", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(
                            "%s₹%.0f (%.1f%%)".format(if (pnl >= 0) "+" else "", pnl, pnlPct),
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = pnlColor
                        )
                    }
                }
            }
        }

        items(holdings.values.toList(), key = { it.symbol }) { holding ->
            val quote = quotes[holding.symbol]
            val currentPrice = quote?.price ?: holding.avgBuyPrice
            val currentValue = currentPrice * holding.quantity
            val pnl = currentValue - holding.totalInvested
            val pnlColor = if (pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(holding.symbol.removeSuffix(".NS"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(holding.companyName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹%.2f".format(currentPrice), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "%s₹%.0f".format(if (pnl >= 0) "+" else "", pnl),
                                fontSize = 12.sp, color = pnlColor, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Qty: %.2f  ·  Avg: ₹%.2f".format(holding.quantity, holding.avgBuyPrice), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        OutlinedButton(
                            onClick = { showSellDialog = holding },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("Sell", fontSize = 11.sp) }
                    }
                }
            }
        }

        // Recent transactions
        val txns = vm.getTransactions().take(20)
        if (txns.isNotEmpty()) {
            item {
                Text("Recent Transactions", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            items(txns, key = { "${it.symbol}${it.date}${it.type}${it.quantity}" }) { tx ->
                val isBuy = tx.type == "BUY"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isBuy) "▲" else "▼", color = if (isBuy) Color(0xFF2E7D32) else Color(0xFFC62828), fontSize = 12.sp)
                        Column {
                            Text("${tx.type} ${tx.symbol.removeSuffix(".NS")}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("${tx.date} · qty %.2f".format(tx.quantity), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    Text(
                        "%s%.0f GC".format(if (isBuy) "-" else "+", tx.totalGC),
                        fontSize = 12.sp,
                        color = if (isBuy) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun SellDialog(holding: StockHolding, currentPrice: Float, onSell: (Float) -> Unit, onDismiss: () -> Unit) {
    var qtyText by remember { mutableStateOf("") }
    val qty = qtyText.toFloatOrNull() ?: 0f
    val canSell = qty > 0f && qty <= holding.quantity
    val gain = qty * currentPrice

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell ${holding.symbol.removeSuffix(".NS")}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current price: ₹%.2f  ·  You hold: %.2f".format(currentPrice, holding.quantity), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Quantity to sell") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                if (qty > 0f) {
                    Text(
                        "You'll receive: %.0f GC".format(gain),
                        fontSize = 12.sp,
                        color = if (canSell) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (canSell) onSell(qty) }, enabled = canSell) { Text("Sell") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Loan Tab ────────────────────────────────────────────────────────────────

@Composable
fun LoanTab(vm: BankViewModel, coins: Int) {
    val hasLoan = remember(coins) { vm.hasActiveLoan() }
    val isLocked = remember(coins) { vm.isLoanStoreLocked() }
    var showRepayDialog by remember { mutableStateOf(false) }
    var selectedLoanAmount by remember { mutableIntStateOf(0) }
    var showConfirmLoan by remember { mutableStateOf(false) }

    val loanTiers = listOf(50, 100, 200, 500)

    if (showRepayDialog) {
        var repayText by remember { mutableStateOf("") }
        val repayAmt = repayText.toIntOrNull() ?: 0
        val remaining = vm.getLoanRemaining()
        AlertDialog(
            onDismissRequest = { showRepayDialog = false },
            title = { Text("Repay Loan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remaining: ${remaining} GC  ·  Balance: $coins GC", fontSize = 13.sp)
                    OutlinedTextField(
                        value = repayText,
                        onValueChange = { repayText = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount to repay") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    TextButton(onClick = { repayText = minOf(remaining, coins).toString() }) {
                        Text("Pay all (${minOf(remaining, coins)} GC)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.repayLoan(repayAmt); showRepayDialog = false },
                    enabled = repayAmt > 0 && repayAmt <= coins
                ) { Text("Repay") }
            },
            dismissButton = { TextButton(onClick = { showRepayDialog = false }) { Text("Cancel") } }
        )
    }

    if (showConfirmLoan && selectedLoanAmount > 0) {
        AlertDialog(
            onDismissRequest = { showConfirmLoan = false },
            title = { Text("Borrow $selectedLoanAmount GC?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Repay within 7 days", fontSize = 13.sp)
                    Text("• 5% daily interest after due date", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    Text("• Day 4+ overdue: -10 XP per day", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    Text("• Day 7+ overdue: Plugin Store purchases blocked", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = { vm.takeLoan(selectedLoanAmount); showConfirmLoan = false }) { Text("Borrow") }
            },
            dismissButton = { TextButton(onClick = { showConfirmLoan = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Store locked warning
        if (isLocked) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text("Plugin Store Locked", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            "Your loan is 7+ days overdue. Plugin Store purchases and new loans are blocked until you repay.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        val xpLost = vm.getLoanXpPenaltyTotal()
                        if (xpLost > 0) Text("XP lost to penalties: $xpLost XP", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (hasLoan) {
            item {
                val remaining = vm.getLoanRemaining()
                val total = vm.getLoanAmount()
                val repaid = vm.getLoanRepaidAmount()
                val dueDate = vm.getLoanDueDate()
                val penaltyDays = vm.getLoanPenaltyDays()
                val progress = if (total > 0) repaid.toFloat() / total else 0f

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (penaltyDays > 0) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Active Loan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (penaltyDays > 0) {
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.error) {
                                    Text("$penaltyDays days overdue", fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("Borrowed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("$total GC", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Remaining", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text("$remaining GC", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                    color = if (penaltyDays > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (penaltyDays > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        // Penalty escalation info
                        if (penaltyDays in 1..3) {
                            Text("⚠ Interest compounding daily (5%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        } else if (penaltyDays in 4..6) {
                            Text("⚠ Losing 10 XP/day · Interest compounding", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        } else if (penaltyDays >= 7) {
                            Text("🔒 Plugin Store locked · XP penalty active", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Due: $dueDate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Button(
                            onClick = { showRepayDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Repay Loan") }
                    }
                }
            }
        } else if (!isLocked) {
            item {
                Text("🏦 Coin Loan", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 4.dp))
                Text(
                    "Borrow GC now and repay within 7 days.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            item {
                // Penalty escalation info card
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Penalties for late repayment", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Day 1-3 overdue: +5% interest daily", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Day 4-7 overdue: -10 XP per day", fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        Text("Day 7+ overdue: Plugin Store purchases locked", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            items(loanTiers) { amount ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedLoanAmount = amount
                        showConfirmLoan = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("$amount GC", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Repay $amount GC within 7 days", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Text("Borrow →", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
