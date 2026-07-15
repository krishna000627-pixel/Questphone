package neth.iecal.questphone.app.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    val info get() = userRepository.userInfo
    fun save() = userRepository.saveUserInfo()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(navController: NavController, vm: ProfileSettingsViewModel = hiltViewModel()) {
    val info = vm.info

    var fullName    by remember { mutableStateOf(info.full_name) }
    var dob         by remember { mutableStateOf(info.profileDob) }
    var profileType by remember { mutableStateOf(info.profileType) }
    var classField  by remember { mutableStateOf(info.profileClass) }
    var skills      by remember { mutableStateOf(info.profileSkills) }
    var sideHustle  by remember { mutableStateOf(info.profileSideHustle) }
    var wakeWord    by remember { mutableStateOf(info.jarvisWakeWord) }
    var focusStart  by remember { mutableStateOf("${info.focusStartHour}:${info.focusStartMin.toString().padStart(2,'0')}") }
    var focusEnd    by remember { mutableStateOf("${info.focusEndHour}:${info.focusEndMin.toString().padStart(2,'0')}") }
    var coinRatio   by remember { mutableStateOf(info.coinToMinuteRatio.toString()) }
    var appName     by remember { mutableStateOf(info.launcherAppName) }

    fun saveAll() {
        info.full_name = fullName
        info.profileDob = dob
        info.profileType = profileType
        info.profileClass = classField
        info.profileSkills = skills
        info.profileSideHustle = sideHustle
        info.jarvisWakeWord = wakeWord.ifBlank { "jarvis" }
        // Parse focus time
        runCatching {
            val (sh, sm) = focusStart.split(":").map { it.toInt() }
            info.focusStartHour = sh; info.focusStartMin = sm
        }
        runCatching {
            val (eh, em) = focusEnd.split(":").map { it.toInt() }
            info.focusEndHour = eh; info.focusEndMin = em
        }
        info.coinToMinuteRatio = coinRatio.toIntOrNull() ?: 100
        info.launcherAppName = appName.ifBlank { "QuestPhone" }
        vm.save()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { saveAll(); navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                actions = {
                    TextButton(onClick = { saveAll(); navController.popBackStack() }) {
                        Text("Save", color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // -- Identity ----------------------------------------------
            item {
                SettingSection("👤 Identity") {
                    FieldInput("Full Name", fullName) { fullName = it }
                    FieldInput("Date of Birth (DD/MM/YY)", dob) { dob = it }

                    // Profile type chips
                    Text("Profile Type", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("school", "college", "work").forEach { type ->
                            FilterChip(
                                selected = profileType == type,
                                onClick = { profileType = type },
                                label = { Text(type.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                            )
                        }
                    }

                    FieldInput(
                        label = when (profileType) {
                            "school" -> "Class (e.g. 12th PCM)"
                            "college" -> "Course (e.g. B.Tech CSE)"
                            else -> "Role (e.g. Software Engineer)"
                        },
                        value = classField
                    ) { classField = it }
                    FieldInput("Skills (e.g. AI Prompt Engineering)", skills) { skills = it }
                    FieldInput("Side Hustle (optional)", sideHustle) { sideHustle = it }
                }
            }

            // -- Focus Time --------------------------------------------
            item {
                SettingSection("⚔️ Focus Schedule") {
                    Text("Mon–Fri focus window (24hr format, e.g. 7:50 and 16:00)",
                        fontSize = 12.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = focusStart, onValueChange = { focusStart = it },
                            label = { Text("Start (H:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = focusEnd, onValueChange = { focusEnd = it },
                            label = { Text("End (H:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }

            // -- Jarvis ------------------------------------------------
            item {
                SettingSection("🤖 Jarvis Wake Word") {
                    Text("Say this word to trigger Google Assistant", fontSize = 12.sp, color = Color.Gray)
                    FieldInput("Wake word (default: jarvis)", wakeWord) { wakeWord = it }
                }
            }

            // -- Coin Ratio --------------------------------------------
            item {
                SettingSection("🪙 Coin → Free Time Ratio") {
                    Text("How many coins = 1 minute of free screen time", fontSize = 12.sp, color = Color.Gray)
                    FieldInput("Coins per minute (e.g. 100)", coinRatio) { coinRatio = it }
                    val c = coinRatio.toIntOrNull() ?: 100
                    Text("$c coins = 1 minute free time", fontSize = 12.sp, color = Color(0xFF4CAF50))
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF0D0D0D))
        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(14.dp))
        .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        content()
    }
}

@Composable
private fun FieldInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp))
}
