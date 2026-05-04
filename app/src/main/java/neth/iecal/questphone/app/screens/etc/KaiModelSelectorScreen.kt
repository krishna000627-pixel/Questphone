package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import javax.inject.Inject

data class AiModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val speed: String,
    val quality: String
)

val AVAILABLE_MODELS = listOf(
    AiModelOption(
        id = "gemma-3-27b-it",
        displayName = "Gemma 3 27B",
        description = "Default model. Best balance of quality and speed for daily use.",
        speed = "Fast",
        quality = "High"
    ),
    AiModelOption(
        id = "gemma-3-12b-it",
        displayName = "Gemma 3 12B",
        description = "Lighter model. Faster responses, slightly less detailed.",
        speed = "Very Fast",
        quality = "Good"
    ),
    AiModelOption(
        id = "gemma-3-4b-it",
        displayName = "Gemma 3 4B",
        description = "Compact model. Best for quick queries, uses fewer coins.",
        speed = "Fastest",
        quality = "Basic"
    ),
    AiModelOption(
        id = "gemini-2.0-flash-lite",
        displayName = "Gemini 2.0 Flash Lite",
        description = "Google's fast Flash model. Great for concise answers.",
        speed = "Very Fast",
        quality = "High"
    ),
    AiModelOption(
        id = "gemini-2.5-pro-exp-03-25",
        displayName = "Gemini 2.5 Pro (Exp)",
        description = "Most capable model. Best for complex planning and analysis.",
        speed = "Slower",
        quality = "Best"
    ),
)

@HiltViewModel
class KaiModelSelectorViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    fun setModel(modelId: String) {
        userRepository.userInfo.aiModel = modelId
        userRepository.saveUserInfo()
    }
    val currentModel get() = userRepository.userInfo.aiModel
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaiModelSelectorScreen(
    navController: NavController,
    vm: KaiModelSelectorViewModel = hiltViewModel()
) {
    var selected by remember { mutableStateOf(vm.currentModel) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Model", fontWeight = FontWeight.Bold) },
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
            items(AVAILABLE_MODELS) { model ->
                val isSelected = selected == model.id
                val border = if (isSelected)
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier

                Card(
                    modifier = Modifier.fillMaxWidth().then(border)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selected = model.id
                            vm.setModel(model.id)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(),
                        Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = {
                            selected = model.id; vm.setModel(model.id)
                        })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(model.displayName, fontWeight = FontWeight.SemiBold)
                                if (isSelected) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(50)
                                    ) {
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
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
