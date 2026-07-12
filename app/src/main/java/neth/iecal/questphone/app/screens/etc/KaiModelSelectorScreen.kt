package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

data class AiModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val speed: String,
    val quality: String,
    val isDeprecated: Boolean = false
)

val AVAILABLE_MODELS = listOf(
    AiModelOption(
        id = "gemini-2.0-flash",
        displayName = "Gemini 2.0 Flash ⭐ Recommended",
        description = "Fast, capable, free on Google AI Studio. New default after Gemma 4 27B was deprecated.",
        speed = "Very Fast", quality = "High"
    ),
    AiModelOption(
        id = "gemma-3-12b-it",
        displayName = "Gemma 4 12B",
        description = "Lighter and faster. Slightly less detailed than 27B.",
        speed = "Very Fast", quality = "Good"
    ),
    AiModelOption(
        id = "gemma-3-4b-it",
        displayName = "Gemma 4 4B",
        description = "Most compact. Best for quick queries, lowest latency.",
        speed = "Fastest", quality = "Basic"
    ),
    AiModelOption(
        id = "gemini-2.0-flash",
        displayName = "Gemini 2.0 Flash",
        description = "Google's fast multimodal model. Great all-rounder.",
        speed = "Very Fast", quality = "High"
    ),
    AiModelOption(
        id = "gemini-2.0-flash-lite",
        displayName = "Gemini 2.0 Flash Lite",
        description = "Even lighter Flash variant. Ultra-fast, minimal token cost.",
        speed = "Fastest", quality = "Good"
    ),
    AiModelOption(
        id = "gemini-2.5-flash-preview-04-17",
        displayName = "Gemini 2.5 Flash (Preview)",
        description = "Latest Flash with thinking capabilities. Experimental.",
        speed = "Fast", quality = "Very High"
    ),
    AiModelOption(
        id = "gemini-2.5-pro-preview-03-25",
        displayName = "Gemini 2.5 Pro (Preview)",
        description = "Most capable. Best for complex planning, quest generation, analysis.",
        speed = "Slower", quality = "Best"
    ),
)

@HiltViewModel
class KaiModelSelectorViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    fun setModel(modelId: String) {
        userRepository.userInfo.aiModel = modelId.trim()
        userRepository.saveUserInfo()
    }
    val currentModel get() = userRepository.userInfo.aiModel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaiModelSelectorScreen(navController: NavController, vm: KaiModelSelectorViewModel = hiltViewModel()) {
    var selected by remember { mutableStateOf(vm.currentModel) }
    var customModelInput by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    // Check if current model is a custom one (not in the list)
    val isCustomModel = AVAILABLE_MODELS.none { it.id == selected }

    LaunchedEffect(Unit) {
        if (isCustomModel) {
            customModelInput = selected
            showCustomInput = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Model", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Choose which AI model powers Kai. All models use your Google AI Studio API key and run on the free tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Model list
            items(AVAILABLE_MODELS.size) { idx ->
                val model = AVAILABLE_MODELS[idx]
                val isSelected = selected == model.id && !showCustomInput
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selected = model.id
                            showCustomInput = false
                            customModelInput = ""
                            vm.setModel(model.id)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        Arrangement.spacedBy(12.dp), Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = {
                            selected = model.id
                            showCustomInput = false
                            vm.setModel(model.id)
                        })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                if (isSelected) {
                                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                                        Text("Active", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(model.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("⚡ ${model.speed}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("✨ ${model.quality}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Show model ID for reference
                            Text(model.id, fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Custom model entry
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (showCustomInput) Modifier.border(2.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCustomInput = true },
                    colors = CardDefaults.cardColors(
                        containerColor = if (showCustomInput)
                            Color(0xFFFF9800).copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text("Custom Model ID", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Type any Google AI Studio model ID directly.\nUseful when a model is deprecated or new ones release.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(selected = showCustomInput, onClick = { showCustomInput = true })
                        }
                        if (showCustomInput) {
                            OutlinedTextField(
                                value = customModelInput,
                                onValueChange = { customModelInput = it },
                                label = { Text("Model ID") },
                                placeholder = { Text("e.g. gemini-2.0-flash") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val id = customModelInput.trim()
                                    if (id.isNotBlank()) {
                                        selected = id
                                        vm.setModel(id)
                                    }
                                },
                                enabled = customModelInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) { Text("Use This Model") }

                            if (showCustomInput && selected == customModelInput.trim() && customModelInput.isNotBlank()) {
                                Surface(color = Color(0xFFFF9800).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "✅ Active: $selected",
                                        Modifier.padding(8.dp),
                                        fontSize = 11.sp,
                                        color = Color(0xFFFF9800),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Text(
                                "Find available model IDs at:\naistudio.google.com/app/apikey",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
