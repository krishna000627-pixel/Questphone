package neth.iecal.questphone.app.screens.habitica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.coroutines.flow.MutableStateFlow
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.habitica.CustomAchievement
import nethical.questphone.data.habitica.StatPoints
import javax.inject.Inject

@HiltViewModel
class AchievementViewModel @Inject constructor(
    val userRepository: UserRepository
) : ViewModel() {
    val achievements get() = userRepository.getCustomAchievements()
    val statPoints get() = userRepository.getStatPoints()
    val statPointsToAllocate get() = userRepository.userInfo.statPointsToAllocate
    val coins = userRepository.coinsState

    fun addAchievement(a: CustomAchievement) = userRepository.addCustomAchievement(a)
    fun complete(id: String) = userRepository.completeCustomAchievement(id)
    fun delete(id: String) = userRepository.deleteCustomAchievement(id)
    fun allocate(slot: Int) = userRepository.allocateStatPoint(slot)
    fun rename(slot: Int, name: String) = userRepository.renameStatPoint(slot, name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(navController: NavController, vm: AchievementViewModel = hiltViewModel()) {
    var achievements by remember { mutableStateOf(vm.achievements.toList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    fun refresh() { achievements = vm.achievements.toList() }

    if (showAddDialog) {
        AchievementAddDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { vm.addAchievement(it); refresh(); showAddDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements & Stats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painterResource(R.drawable.baseline_info_24), null)
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, null, tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D0D0D), contentColor = Color.White) {
                listOf("🏆 Achievements", "⚡ Stat Points").forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(label, fontSize = 12.sp) })
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                when (selectedTab) {
                    0 -> {
                        if (achievements.isEmpty()) {
                            item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                                Text("No achievements. Tap + to add one.", color = Color.Gray, fontSize = 14.sp)
                            }}
                        }
                        items(achievements, key = { it.id }) { a ->
                            AchievementCard(a,
                                onComplete = { vm.complete(a.id); refresh() },
                                onDelete = { vm.delete(a.id); refresh() }
                            )
                        }
                    }
                    1 -> {
                        item {
                            val toAllocate = vm.statPointsToAllocate
                            if (toAllocate > 0) {
                                Text("$toAllocate stat points to allocate!",
                                    fontSize = 14.sp, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp))
                            }
                            StatPointsCard(vm.statPoints,
                                onAllocate = { slot -> vm.allocate(slot) },
                                onRename = { slot, name -> vm.rename(slot, name) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun AchievementCard(a: CustomAchievement, onComplete: () -> Unit, onDelete: () -> Unit) {
    val done = a.isCompleted
    Row(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(if (done) Color(0xFF0A1A0A) else Color(0xFF0D0D0D))
        .border(1.dp, if (done) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFF222222), RoundedCornerShape(14.dp))
        .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (done) Color.Gray else Color.White)
            if (a.description.isNotEmpty()) Text(a.description, fontSize = 11.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🪙 ${a.coinReward} reward", fontSize = 11.sp, color = Color(0xFFFFB300))
                if (done) Text("✅ ${a.completedDate}", fontSize = 11.sp, color = Color(0xFF4CAF50))
            }
        }
        if (!done) {
            TextButton(onClick = onComplete) { Text("Done", color = Color(0xFF4CAF50)) }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFF333333), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatPointsCard(sp: StatPoints, onAllocate: (Int) -> Unit, onRename: (Int, String) -> Unit) {
    val stats = listOf(
        Triple(1, sp.name1, sp.value1),
        Triple(2, sp.name2, sp.value2),
        Triple(3, sp.name3, sp.value3),
        Triple(4, sp.name4, sp.value4),
    )
    var editingSlot by remember { mutableIntStateOf(0) }
    var editName by remember { mutableStateOf("") }

    if (editingSlot > 0) {
        AlertDialog(
            onDismissRequest = { editingSlot = 0 },
            title = { Text("Rename Stat") },
            text = {
                OutlinedTextField(value = editName, onValueChange = { editName = it },
                    label = { Text("Stat name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { onRename(editingSlot, editName); editingSlot = 0 }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingSlot = 0 }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF0D0D0D))
        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Stat Points", fontSize = 12.sp, color = Color.Gray, letterSpacing = 1.sp)
        stats.forEach { (slot, name, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    LinearProgressIndicator(
                        progress = { (value / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = Color(0xFF7B1FA2), trackColor = Color(0xFF1A1A1A)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF7B1FA2))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onAllocate(slot) }) { Text("+", fontSize = 18.sp) }
                TextButton(onClick = { editName = name; editingSlot = slot }) {
                    Text("✏️", fontSize = 14.sp)
                }
            }
        }
        Text("Stat points earned by leveling up. Tap + to allocate.", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun AchievementAddDialog(onDismiss: () -> Unit, onAdd: (CustomAchievement) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var coinReward by remember { mutableStateOf("100") }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("New Achievement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Achievement Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = coinReward, onValueChange = { coinReward = it },
                    label = { Text("🪙 Coin Reward") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onAdd(CustomAchievement(title = title, description = desc,
                    coinReward = coinReward.toIntOrNull() ?: 100))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
