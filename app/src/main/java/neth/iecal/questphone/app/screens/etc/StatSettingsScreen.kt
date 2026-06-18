package neth.iecal.questphone.app.screens.etc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.habitica.StatPoints
import javax.inject.Inject

@HiltViewModel
class StatSettingsViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatSettingsScreen(
    navController: NavController,
    vm: StatSettingsViewModel = hiltViewModel()
) {
    val sp = vm.userRepository.userInfo.statPoints
    var name1 by remember { mutableStateOf(sp.name1) }
    var name2 by remember { mutableStateOf(sp.name2) }
    var name3 by remember { mutableStateOf(sp.name3) }
    var name4 by remember { mutableStateOf(sp.name4) }

    val statColors = listOf(Color(0xFFEF5350), Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFF4CAF50))
    val vals = listOf(sp.value1, sp.value2, sp.value3, sp.value4)
    val names = listOf(name1, name2, name3, name4)

    fun save() {
        vm.userRepository.userInfo.statPoints = sp.copy(
            name1 = name1.ifBlank { "Strength" },
            name2 = name2.ifBlank { "Intelligence" },
            name3 = name3.ifBlank { "Focus" },
            name4 = name4.ifBlank { "Discipline" }
        )
        vm.userRepository.saveUserInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stat Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { save(); navController.popBackStack() }) {
                        Text("<", color = Color.White, fontSize = 18.sp)
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) {
                        Text("Save", color = Color(0xFF00BCD4))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            // Stat cards with current values + rename
            item {
                Text("Your Stats", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Rename stats to match your goals. Values update automatically when quests are completed.",
                    fontSize = 12.sp, color = Color(0xFF555555))
            }

            val setters = listOf<(String) -> Unit>(
                { name1 = it }, { name2 = it }, { name3 = it }, { name4 = it }
            )

            items(4) { i ->
                val color = statColors[i]
                val value = vals[i]
                val name = names[i]

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(
                            "$value",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = color
                        )
                        Text(
                            "Stat ${i + 1}",
                            fontSize = 11.sp,
                            color = Color(0xFF444444)
                        )
                    }
                    // Linear progress bar
                    LinearProgressIndicator(
                        progress = { (value % 100) / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = color,
                        trackColor = Color(0xFF1A1A1A)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { setters[i](it) },
                        label = { Text("Stat name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = color,
                            unfocusedBorderColor = Color(0xFF2A2A2A)
                        )
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Allocation Tips", fontWeight = FontWeight.SemiBold, color = Color(0xFF888888), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Stat 1 (default: Strength) — physical quests: exercise, sport",
                    "Stat 2 (default: Intelligence) — study, reading, learning",
                    "Stat 3 (default: Focus) — meditation, deep work, no-phone time",
                    "Stat 4 (default: Discipline) — habits, routines, consistency"
                ).forEach { tip ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = Color(0xFF333333), fontSize = 12.sp)
                        Text(tip, fontSize = 11.sp, color = Color(0xFF555555), lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
