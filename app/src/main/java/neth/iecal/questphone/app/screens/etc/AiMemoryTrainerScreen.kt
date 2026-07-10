package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.ai.GemmaRepository
import neth.iecal.questphone.core.ai.KaiPrefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class AiMemoryTrainerViewModel @Inject constructor(
    val userRepository: UserRepository,
    private val gemmaRepository: GemmaRepository
) : ViewModel() {

    private val _memories = MutableStateFlow<MutableList<String>>(mutableListOf())
    val memories: StateFlow<MutableList<String>> = _memories

    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing

    private val _compressError = MutableStateFlow<String?>(null)
    val compressError: StateFlow<String?> = _compressError

    fun load() {
        _memories.value = userRepository.userInfo.aiMemory.toMutableList()
    }

    fun addMemory(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val list = _memories.value.toMutableList()
        if (list.size >= 20) list.removeAt(0)
        list.add(trimmed)
        _memories.value = list
        persist(list)
    }

    fun deleteMemory(index: Int) {
        val list = _memories.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _memories.value = list
            persist(list)
        }
    }

    fun updateMemory(index: Int, newText: String) {
        val list = _memories.value.toMutableList()
        if (index in list.indices && newText.isNotBlank()) {
            list[index] = newText.trim()
            _memories.value = list
            persist(list)
        }
    }

    /** Ask Kai to compress all memories into a concise summary, then replace them. */
    fun compressWithAi(apiKey: String) {
        val current = _memories.value.toList()
        if (current.isEmpty()) return
        _isCompressing.value = true
        _compressError.value = null
        viewModelScope.launch {
            try {
                val compressed = callGeminiCompress(apiKey, current, gemmaRepository.getModel())
                if (compressed.isNotEmpty()) {
                    _memories.value = compressed.toMutableList()
                    persist(compressed.toMutableList())
                }
            } catch (e: Exception) {
                _compressError.value = "Compression failed: ${e.message}"
            } finally {
                _isCompressing.value = false
            }
        }
    }

    private fun persist(list: MutableList<String>) {
        userRepository.userInfo.aiMemory = list
        userRepository.saveUserInfo()
    }

    /** Calls Gemini API to compress memory entries into ≤10 dense bullet points. */
    private suspend fun callGeminiCompress(
        apiKey: String,
        memories: List<String>,
        model: String
    ): List<String> = withContext(Dispatchers.IO) {
        val prompt = """You are a memory compressor for an AI companion called Kai.
Below are the user's current memory entries. Compress them into a maximum of 10 concise, 
information-dense bullet points. Remove duplicates, merge related facts, keep the most 
important and actionable insights. Return ONLY a JSON array of strings, no markdown, no preamble.

Current memories:
${memories.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n")}

Return format: ["memory 1", "memory 2", ...]""".trimIndent()

        val http = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 500)
                put("temperature", 0.3)
            })
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()
        val raw = response.body?.string() ?: throw Exception("Empty response")
        val root = JSONObject(raw)

        // Parse the text out
        val text = root
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val arr = JSONArray(text)
        List(arr.length()) { arr.getString(it) }
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMemoryTrainerScreen(navController: NavController) {
    val vm: AiMemoryTrainerViewModel = hiltViewModel()
    val context = LocalContext.current

    val memories by vm.memories.collectAsState()
    val isCompressing by vm.isCompressing.collectAsState()
    val compressError by vm.compressError.collectAsState()

    var newMemoryText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showCompressConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    val apiKey = KaiPrefs.getApiKey(context)
    val hasApiKey = apiKey.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🧠 AI Memory Trainer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("← Back", color = Color(0xFF00BCD4))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0A0A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF080808)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header info ──
            item {
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF001A1F))
                        .border(1.dp, Color(0xFF00BCD4).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text("What is this?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF00BCD4))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kai stores up to 20 memory snippets about you. These are injected into every chat so Kai knows your habits, goals, and preferences.\n\nHere you can view, edit, add, delete, or use AI to compress them into fewer, denser facts.",
                        fontSize = 12.sp, color = Color(0xFF888888), lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${memories.size}/20 memories stored",
                        fontSize = 11.sp,
                        color = if (memories.size >= 18) Color(0xFFFF5722) else Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── AI Compress button ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("✨ AI Compress", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        "Send all memories to Kai and let it compress them into ≤10 dense, deduplicated facts. This replaces your current memories.",
                        fontSize = 11.sp, color = Color(0xFF666666), lineHeight = 16.sp
                    )
                    if (!hasApiKey) {
                        Text(
                            "⚠️ No API key set. Go to Settings → Kai to add one.",
                            fontSize = 11.sp, color = Color(0xFFFF5722)
                        )
                    }
                    if (compressError != null) {
                        Text(compressError!!, fontSize = 11.sp, color = Color(0xFFFF5722))
                    }
                    Button(
                        onClick = { showCompressConfirm = true },
                        enabled = hasApiKey && memories.isNotEmpty() && !isCompressing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001A1F))
                    ) {
                        if (isCompressing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF00BCD4),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Compressing…", color = Color(0xFF00BCD4))
                        } else {
                            Text("🤖 Compress with Kai →", color = Color(0xFF00BCD4))
                        }
                    }
                }
            }

            // ── Add new memory ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("➕ Add Memory", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    OutlinedTextField(
                        value = newMemoryText,
                        onValueChange = { newMemoryText = it },
                        placeholder = { Text("e.g. Wakes up at 6am, works out before breakfast", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00BCD4),
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFCCCCCC)
                        )
                    )
                    Button(
                        onClick = {
                            vm.addMemory(newMemoryText)
                            newMemoryText = ""
                        },
                        enabled = newMemoryText.isNotBlank() && memories.size < 20,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3320))
                    ) { Text("Save Memory", color = Color(0xFF4CAF50), fontSize = 12.sp) }
                }
            }

            // ── Memory list ──
            if (memories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No memories yet.\nKai will add them automatically during chats, or add them manually above.",
                            fontSize = 13.sp, color = Color(0xFF444444),
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "Stored Memories",
                        fontSize = 12.sp, color = Color(0xFF666666), fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                itemsIndexed(memories) { index, memory ->
                    MemoryCard(
                        index = index,
                        text = memory,
                        isEditing = editingIndex == index,
                        editingText = editingText,
                        onEditStart = { editingIndex = index; editingText = memory },
                        onEditChange = { editingText = it },
                        onEditSave = {
                            vm.updateMemory(index, editingText)
                            editingIndex = null
                            editingText = ""
                        },
                        onEditCancel = { editingIndex = null; editingText = "" },
                        onDelete = { vm.deleteMemory(index) }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Compress confirm dialog
    if (showCompressConfirm) {
        AlertDialog(
            onDismissRequest = { showCompressConfirm = false },
            containerColor = Color(0xFF111111),
            title = { Text("Compress Memories?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Kai will read all ${memories.size} memories and compress them into ≤10 concise facts. Your current memories will be replaced.\n\nThis cannot be undone.",
                    color = Color(0xFF888888), fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompressConfirm = false
                        vm.compressWithAi(apiKey)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001A1F))
                ) { Text("Compress", color = Color(0xFF00BCD4)) }
            },
            dismissButton = {
                TextButton(onClick = { showCompressConfirm = false }) {
                    Text("Cancel", color = Color(0xFF666666))
                }
            }
        )
    }
}

@Composable
private fun MemoryCard(
    index: Int,
    text: String,
    isEditing: Boolean,
    editingText: String,
    onEditStart: () -> Unit,
    onEditChange: (String) -> Unit,
    onEditSave: () -> Unit,
    onEditCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D0D0D))
            .border(
                1.dp,
                if (isEditing) Color(0xFF00BCD4).copy(alpha = 0.5f) else Color(0xFF1A1A1A),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "#${index + 1}",
                fontSize = 10.sp,
                color = Color(0xFF333333),
                fontWeight = FontWeight.Medium
            )
            Row {
                if (!isEditing) {
                    TextButton(
                        onClick = onEditStart,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("Edit", fontSize = 11.sp, color = Color(0xFF00BCD4)) }
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("Delete", fontSize = 11.sp, color = Color(0xFFFF5722)) }
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value = editingText,
                onValueChange = onEditChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00BCD4),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFCCCCCC)
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEditCancel) {
                    Text("Cancel", fontSize = 11.sp, color = Color(0xFF666666))
                }
                Button(
                    onClick = onEditSave,
                    enabled = editingText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF001A1F))
                ) { Text("Save", fontSize = 11.sp, color = Color(0xFF00BCD4)) }
            }
        } else {
            Text(text, fontSize = 13.sp, color = Color(0xFFCCCCCC), lineHeight = 18.sp)
        }
    }
}
