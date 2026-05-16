package neth.iecal.questphone.app.screens.etc

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.ai.*
import javax.inject.Inject

// ---- Pixel art avatar definitions ------------------------------------------------------------------------------------------
data class DisplayMessage(
    val role: String,
    val text: String,
    val actionDone: String? = null
)

@HiltViewModel
class GemmaChatViewModel @Inject constructor(
    private val gemmaRepository: GemmaRepository,
    private val memoryManager: KaiMemoryManager,
    val userRepository: UserRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<DisplayMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId = _activeSessionId.asStateFlow()

    // Heartbeat: last notable thing Kai said proactively
    private val _heartbeatMsg = MutableStateFlow<String?>(null)
    val heartbeatMsg = _heartbeatMsg.asStateFlow()
    private var lastHeartbeatTime = 0L
    private var lastHeartbeatText = ""

    fun init(context: Context) {
        val sessions = memoryManager.getSessions(context)
        _sessions.value = sessions
        val activeId = memoryManager.getActiveSessionId(context)
        if (activeId != null && sessions.any { it.id == activeId }) {
            loadSession(context, activeId)
        }
        // Start heartbeat
        viewModelScope.launch {
            while (true) {
                delay(60_000L) // every minute
                runHeartbeat(context)
            }
        }
    }

    private fun runHeartbeat(context: Context) {
        val u = userRepository.userInfo
        val now = System.currentTimeMillis()
        // Only show heartbeat if >1 hour since last meaningful one
        if (now - lastHeartbeatTime < 3_600_000L) return

        viewModelScope.launch {
            val simpleLines = listOf(
                "Still here if you need me 🤖",
                "How's the focus going? 💪",
                "Don't forget to drink water 💧",
                "Streak looking good! Keep it up 🔥",
                "One quest at a time 🎯"
            )
            val pick = simpleLines.random()
            // 70% chance → just a quiet line (not surfaced in UI)
            // 30% chance → actually surface it
            if ((0..9).random() < 3) {
                if (pick != lastHeartbeatText) {
                    _heartbeatMsg.value = pick
                    lastHeartbeatText = pick
                    lastHeartbeatTime = now
                }
            }
        }
    }

    fun dismissHeartbeat() { _heartbeatMsg.value = null }

    fun newSession(context: Context) {
        val session = memoryManager.createSession(context)
        _sessions.value = memoryManager.getSessions(context)
        _activeSessionId.value = session.id
        _messages.value = emptyList()
        _error.value = null
    }

    fun loadSession(context: Context, sessionId: String) {
        val session = memoryManager.getSession(context, sessionId) ?: return
        _activeSessionId.value = sessionId
        memoryManager.setActiveSessionId(context, sessionId)
        _messages.value = session.messages.map { DisplayMessage(it.role, it.text) }
    }

    fun deleteSession(context: Context, sessionId: String) {
        memoryManager.deleteSession(context, sessionId)
        _sessions.value = memoryManager.getSessions(context)
        val newActive = _sessions.value.firstOrNull()
        if (newActive != null) loadSession(context, newActive.id)
        else { _messages.value = emptyList(); _activeSessionId.value = null }
    }

    fun isConfigured() = gemmaRepository.isConfigured()
    fun getApiKey() = gemmaRepository.getApiKey()
    fun getModel() = gemmaRepository.getModel()
    fun saveApiKey(key: String) { gemmaRepository.saveApiKey(key); _error.value = null }

    fun sendMessage(context: Context, text: String) {
        if (text.isBlank() || _isLoading.value) return

        // Coin cost check
        val u = userRepository.userInfo
        val costPerMin = u.aiCoinCostPerMin
        if (costPerMin > 0 && u.coins < costPerMin) {
            _error.value = "Not enough coins to chat! Need ${costPerMin} 🪙/min (you have ${u.coins})"
            return
        }
        if (costPerMin > 0) userRepository.useCoins(costPerMin)

        val trimmed = text.trim()
        // Create session if none exists
        val sessionId = _activeSessionId.value ?: run {
            val s = memoryManager.createSession(context, trimmed)
            _sessions.value = memoryManager.getSessions(context)
            _activeSessionId.value = s.id
            s.id
        }

        val userMsg = DisplayMessage("user", trimmed)
        _messages.value = _messages.value + userMsg
        memoryManager.addMessage(context, sessionId, ChatMessage("user", trimmed))
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val history = _messages.value.dropLast(1).map { ChatMessage(it.role, it.text) }
            // Prepend live coin balance so AI always quotes the correct number
            val liveBalance = userRepository.userInfo.coins
            val msgWithBalance = "[Current balance: ${liveBalance} coins] $trimmed"
            gemmaRepository.chat(history, msgWithBalance)
                .onSuccess { resp ->
                    val modelMsg = DisplayMessage("model", resp.text, resp.actionDone)
                    _messages.value = _messages.value + modelMsg
                    memoryManager.addMessage(context, sessionId, ChatMessage("model", resp.text))
                    _sessions.value = memoryManager.getSessions(context)
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Something went wrong"
                    _messages.value = _messages.value.dropLast(1)
                }
            _isLoading.value = false
        }
    }
}

// ---- Markdown formatter --------------------------------------------------------------------------------------------------------------
@Composable
private fun FormattedText(text: String, baseColor: Color, modifier: Modifier = Modifier) {
    val annotated = buildAnnotatedString {
        // Process inline markdown: **bold**, *italic*, `code`
        fun appendInline(raw: String, color: Color) {
            var remaining = raw
            while (remaining.isNotEmpty()) {
                when {
                    remaining.startsWith("**") && remaining.indexOf("**", 2) > 0 -> {
                        val end = remaining.indexOf("**", 2)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                            append(remaining.substring(2, end))
                        }
                        remaining = remaining.substring(end + 2)
                    }
                    remaining.startsWith("*") && remaining.indexOf("*", 1) > 0 -> {
                        val end = remaining.indexOf("*", 1)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                            append(remaining.substring(1, end))
                        }
                        remaining = remaining.substring(end + 1)
                    }
                    remaining.startsWith("`") && remaining.indexOf("`", 1) > 0 -> {
                        val end = remaining.indexOf("`", 1)
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF1A1A2A),
                            color = Color(0xFF00BCD4), fontSize = 12.sp
                        )) { append(remaining.substring(1, end)) }
                        remaining = remaining.substring(end + 1)
                    }
                    else -> {
                        val next = listOf(
                            remaining.indexOf("**").takeIf { it > 0 } ?: Int.MAX_VALUE,
                            remaining.indexOf("*").takeIf { it > 0 } ?: Int.MAX_VALUE,
                            remaining.indexOf("`").takeIf { it > 0 } ?: Int.MAX_VALUE
                        ).min()
                        if (next == Int.MAX_VALUE) {
                            withStyle(SpanStyle(color = color)) { append(remaining) }
                            remaining = ""
                        } else {
                            withStyle(SpanStyle(color = color)) { append(remaining.substring(0, next)) }
                            remaining = remaining.substring(next)
                        }
                    }
                }
            }
        }

        val lines = text.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            when {
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = baseColor)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = baseColor)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 18.sp, color = baseColor)) {
                        append(line.removePrefix("# "))
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    withStyle(SpanStyle(color = Color(0xFF00BCD4))) { append("• ") }
                    appendInline(line.substring(2), baseColor)
                }
                line.matches(Regex("^\\d+\\. .*")) -> {
                    val num = line.substringBefore(". ")
                    withStyle(SpanStyle(color = Color(0xFF00BCD4), fontWeight = FontWeight.Bold)) { append("$num. ") }
                    appendInline(line.substringAfter(". "), baseColor)
                }
                else -> {
                    appendInline(line, baseColor)
                }
            }
            if (lineIdx < lines.size - 1) append("\n")
        }
    }
    Text(annotated, modifier = modifier, fontSize = 14.sp, lineHeight = 21.sp)
}

// ---- Main screen ----------------------------------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GemmaChatScreen(
    navController: NavController,
    vm: GemmaChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val activeSessionId by vm.activeSessionId.collectAsState()
    val heartbeat by vm.heartbeatMsg.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(!vm.isConfigured()) }
    var showSessionPanel by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(vm.getApiKey()) }
    var showApiKey by remember { mutableStateOf(false) }

    val avatarIndex = vm.userRepository.userInfo.aiAvatarIndex
    val coinCost = vm.userRepository.userInfo.aiCoinCostPerMin

    LaunchedEffect(Unit) { vm.init(context) }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (isLoading) 1 else 0)
    }

    // API Key dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { if (vm.isConfigured()) showApiKeyDialog = false },
            containerColor = Color(0xFF0D0D0D), titleContentColor = Color.White,
            title = { Text("Kai API Key", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Free key → aistudio.google.com", fontSize = 12.sp, color = Color(0xFF888888))
                    Text("Active model: ${vm.getModel()}", fontSize = 11.sp, color = Color(0xFF00BCD4))
                    OutlinedTextField(
                        value = apiKeyInput, onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") }, singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(if (showApiKey) "Hide" else "Show", fontSize = 11.sp, color = Color(0xFF00BCD4))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.saveApiKey(apiKeyInput); showApiKeyDialog = false },
                    enabled = apiKeyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))) { Text("Save") }
            },
            dismissButton = {
                if (vm.isConfigured()) TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Pixel art avatar
                        Box(
                            modifier = Modifier.size(32.dp)
                                .background(Color(0xFF001A1F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { KaiPixelAvatar(avatarIndex = avatarIndex, size = 22.dp) }
                        Column {
                            Text("Kai", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${vm.getModel().substringBefore("-").replaceFirstChar{it.uppercase()}} · ${coinCost}🪙/msg",
                                fontSize = 10.sp, color = Color(0xFF888888))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { try { navController.popBackStack() } catch (_: Exception) {} }) {
                        Text("<", color = Color.White, fontSize = 18.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { showSessionPanel = !showSessionPanel }) {
                        // Hamburger / history icon (3 lines)
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
                            val w = size.width
                            val h = size.height
                            val color = androidx.compose.ui.graphics.Color(0xFF00BCD4)
                            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density)
                            drawLine(color, androidx.compose.ui.geometry.Offset(0f, h * 0.25f), androidx.compose.ui.geometry.Offset(w, h * 0.25f), strokeWidth = 2.5f * density)
                            drawLine(color, androidx.compose.ui.geometry.Offset(0f, h * 0.5f),  androidx.compose.ui.geometry.Offset(w, h * 0.5f),  strokeWidth = 2.5f * density)
                            drawLine(color, androidx.compose.ui.geometry.Offset(0f, h * 0.75f), androidx.compose.ui.geometry.Offset(w, h * 0.75f), strokeWidth = 2.5f * density)
                        }
                    }
                    TextButton(onClick = { showApiKeyDialog = true }) {
                        Text("⚙️", fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF080808))
            )
        },
        containerColor = Color(0xFF080808),
        bottomBar = {
            Column(
                modifier = Modifier.background(Color(0xFF080808))
                    .padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()
            ) {
                // Heartbeat banner
                heartbeat?.let { hb ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF001A1F))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable { vm.dismissHeartbeat() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KaiPixelAvatar(avatarIndex = avatarIndex, size = 20.dp)
                        Text(hb, fontSize = 12.sp, color = Color(0xFF00BCD4), modifier = Modifier.weight(1f))
                        Text("×", fontSize = 12.sp, color = Color(0xFF444444))
                    }
                    Spacer(Modifier.height(6.dp))
                }

                error?.let { Text(it, fontSize = 11.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(bottom = 6.dp)) }

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it },
                        placeholder = { Text("Ask Kai anything...", color = Color(0xFF444444)) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val t = inputText.trim()
                            if (t.isNotBlank() && !isLoading) {
                                inputText = ""
                                vm.sendMessage(context, t)
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00BCD4), unfocusedBorderColor = Color(0xFF222222))
                    )
                    IconButton(
                        onClick = {
                            val t = inputText.trim()
                            if (t.isNotBlank() && !isLoading) {
                                inputText = ""
                                vm.sendMessage(context, t)
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier.size(48.dp).background(
                            if (inputText.isNotBlank() && !isLoading) Color(0xFF00BCD4) else Color(0xFF1A1A1A),
                            RoundedCornerShape(24.dp))
                    ) {
                        Icon(Icons.Default.Send, null,
                            tint = if (inputText.isNotBlank() && !isLoading) Color.Black else Color(0xFF444444),
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Session list panel
            if (showSessionPanel) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF0D0D0D))
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Chat History", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        TextButton(onClick = { vm.newSession(context); showSessionPanel = false }) {
                            Text("+ New Chat", color = Color(0xFF00BCD4), fontSize = 12.sp)
                        }
                    }
                    if (sessions.isEmpty()) {
                        Text("No chats yet", fontSize = 12.sp, color = Color(0xFF444444),
                            modifier = Modifier.padding(8.dp))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sessions) { session ->
                                val isActive = session.id == activeSessionId
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isActive) Color(0xFF001A1F) else Color(0xFF111111))
                                        .border(1.dp, if (isActive) Color(0xFF00BCD4) else Color(0xFF222222), RoundedCornerShape(10.dp))
                                        .clickable { vm.loadSession(context, session.id); showSessionPanel = false }
                                        .padding(10.dp)
                                ) {
                                    Text(session.title, fontSize = 11.sp, color = Color.White,
                                        maxLines = 2, lineHeight = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("${session.messages.size} msgs", fontSize = 9.sp, color = Color(0xFF444444))
                                        Text("×", fontSize = 12.sp, color = Color(0xFF444444),
                                            modifier = Modifier.clickable { vm.deleteSession(context, session.id) })
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF1A1A1A))
            }

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp)
            ) {
                if (messages.isEmpty() && !isLoading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(72.dp).background(Color(0xFF001A1F), CircleShape),
                                contentAlignment = Alignment.Center) {
                                KaiPixelAvatar(avatarIndex = avatarIndex, size = 52.dp)
                            }
                            Text("Hi! I'm Kai 👋", fontSize = 20.sp, fontWeight = FontWeight.Light, color = Color.White)
                            Text("Your personal growth companion.\nI have full access to your quests, settings & more.",
                                fontSize = 12.sp, color = Color(0xFF555555),
                                textAlign = TextAlign.Center, lineHeight = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            listOf("Show my quests 📋", "Create a study routine 📚",
                                "Turn on stranger mode 🕵️", "What's my progress? 📊",
                                "Open YouTube 📺", "Delete all quests and start fresh 🗑️"
                            ).forEach { prompt ->
                                TextButton(
                                    onClick = { if (!isLoading) vm.sendMessage(context, prompt) },
                                    modifier = Modifier.background(Color(0xFF0D0D0D), RoundedCornerShape(20.dp))
                                ) { Text(prompt, fontSize = 12.sp, color = Color(0xFF777777)) }
                            }
                        }
                    }
                }

                items(messages) { msg -> KaiMessageBubble(msg, avatarIndex) }
                if (isLoading) { item { KaiTypingIndicator(avatarIndex) } }
            }
        }
    }
}

@Composable
private fun KaiMessageBubble(msg: DisplayMessage, avatarIndex: Int) {
    val isUser = msg.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                Box(Modifier.size(28.dp).background(Color(0xFF001A1F), CircleShape), contentAlignment = Alignment.Center) {
                    KaiPixelAvatar(avatarIndex = avatarIndex, size = 20.dp)
                }
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier.widthIn(max = 290.dp)
                    .background(
                        if (isUser) Color(0xFF003840) else Color(0xFF111111),
                        RoundedCornerShape(
                            topStart = if (isUser) 18.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 18.dp,
                            bottomStart = 18.dp, bottomEnd = 18.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (isUser) {
                    Text(msg.text, fontSize = 14.sp, color = Color.White, lineHeight = 21.sp)
                } else {
                    FormattedText(msg.text, Color.White)
                }
            }
        }
        msg.actionDone?.let { action ->
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(start = 36.dp)
                    .background(Color(0xFF0A2010), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡", fontSize = 10.sp)
                Text(action, fontSize = 11.sp, color = Color(0xFF4CAF50), lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun KaiTypingIndicator(avatarIndex: Int) {
    val t = rememberInfiniteTransition(label = "t")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(Color(0xFF001A1F), CircleShape), Alignment.Center) {
            KaiPixelAvatar(avatarIndex = avatarIndex, size = 20.dp)
        }
        Spacer(Modifier.width(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.background(Color(0xFF111111), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            repeat(3) { idx ->
                val a by t.animateFloat(0.2f, 1f,
                    infiniteRepeatable(tween(600, delayMillis = idx * 150), RepeatMode.Reverse), "d$idx")
                Box(Modifier.size(6.dp).alpha(a).background(Color(0xFF00BCD4), RoundedCornerShape(3.dp)))
            }
        }
    }
}
