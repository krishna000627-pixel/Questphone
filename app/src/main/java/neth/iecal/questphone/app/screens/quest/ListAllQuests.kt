package neth.iecal.questphone.app.screens.quest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.core.core.utils.getCurrentDate
import java.time.LocalTime
import javax.inject.Inject

enum class QuestSortMode { DEFAULT, CATEGORY, STREAK, TIME_WINDOW, STAT }

@HiltViewModel
class ListAllQuestsViewModel @Inject constructor(
    private val questRepository: QuestRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortMode = MutableStateFlow(QuestSortMode.DEFAULT)
    val sortMode: StateFlow<QuestSortMode> = _sortMode

    private val _questList = MutableStateFlow<List<CommonQuestInfo>>(emptyList())
    var questList = MutableStateFlow<List<CommonQuestInfo>>(emptyList())

    init {
        viewModelScope.launch {
            _questList.value = questRepository.getAllQuests().first()
            applyFilters()
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        applyFilters()
    }

    fun setSortMode(mode: QuestSortMode) {
        _sortMode.value = mode
        applyFilters()
    }

    private fun applyFilters() {
        val base = if (_searchQuery.value.isBlank()) _questList.value
        else _questList.value.filter {
            it.title.contains(_searchQuery.value, ignoreCase = true) ||
                    it.instructions.contains(_searchQuery.value, ignoreCase = true) ||
                    it.category.contains(_searchQuery.value, ignoreCase = true)
        }
        questList.value = when (_sortMode.value) {
            QuestSortMode.CATEGORY    -> base.sortedBy { it.category }
            QuestSortMode.STREAK      -> base.sortedByDescending { it.completionStreak }
            QuestSortMode.TIME_WINDOW -> base.sortedBy { it.time_range.firstOrNull() ?: 0 }
            QuestSortMode.STAT        -> base.sortedByDescending {
                it.statReward1 + it.statReward2 + it.statReward3 + it.statReward4 }
            else -> base
        }
    }
}

private fun isQuestOverdue(quest: CommonQuestInfo): Boolean {
    val now = LocalTime.now()
    val endHour = quest.time_range.getOrNull(1) ?: return false
    val today = getCurrentDate()
    return now.hour > endHour &&
            quest.last_completed_on != today &&
            !quest.is_destroyed &&
            quest.selected_days.isNotEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListAllQuests(
    navHostController: NavHostController,
    viewModel: ListAllQuestsViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val questList by viewModel.questList.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            Button(
                onClick = { navHostController.navigate(RootRoute.SelectTemplates.route) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) { Text("Add Quest") }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "All Quests",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            label = { Text("Search Quests") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty())
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Clear, null)
                                    }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Sort"
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                QuestSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.name.replace('_', ' ').lowercase()
                                            .replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            viewModel.setSortMode(mode)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (sortMode == mode)
                                                Icon(Icons.Default.Done, null,
                                                    tint = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (questList.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                            Text("No quests found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(questList, key = { it.id }) { quest ->
                    QuestListCard(
                        quest = quest,
                        onClick = { navHostController.navigate("${RootRoute.ViewQuest.route}${quest.id}") }
                    )
                }
            }
        }
    }
}

@Composable
fun QuestListCard(quest: CommonQuestInfo, onClick: () -> Unit) {
    val overdue = isQuestOverdue(quest)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (overdue)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    quest.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${quest.reward}🪙",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (quest.category.isNotBlank())
                    QuestCategoryBadge(quest.category, quest.categoryColor)
                if (quest.completionStreak > 0)
                    QuestStreakBadge(quest.completionStreak)
                if (overdue)
                    OverdueBadge()
            }
            if (quest.time_range.size >= 2) {
                Text(
                    "${quest.time_range[0]}:00 – ${quest.time_range[1]}:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
