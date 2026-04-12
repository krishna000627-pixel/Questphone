package neth.iecal.questphone.app.screens.onboard.subscreens

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.core.services.AppBlockerServiceInfo
import neth.iecal.questphone.core.services.INTENT_ACTION_REFRESH_APP_BLOCKER
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.core.core.utils.managers.reloadApps
import nethical.questphone.core.core.utils.managers.sendRefreshRequest
import nethical.questphone.data.AppInfo
import javax.inject.Inject

enum class SelectAppsModes{
    ALLOW_ADD, // only allow adding one app, block removing any apps
    ALLOW_REMOVE, // only allow removing one app, block adding any app
    ALLOW_ADD_AND_REMOVE, // no restrictions
    SELECT_STUDY_APPS // for selecting study apps
}
@HiltViewModel
class SelectAppsViewModel @Inject constructor (application: Application,
                                               private val userRepository: UserRepository) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    val searchQuery = mutableStateOf("")

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _selectedApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedApps: StateFlow<Set<String>> = _selectedApps

    fun init(mode: SelectAppsModes) {
        viewModelScope.launch {
            loadApps()
            loadSelectedApps(mode)
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            reloadApps(context.packageManager, context)
                .onSuccess {
                    _apps.value = it
                }
                .onFailure {
                    Log.e("SelectAppsVM", "Failed to load apps: $it")
                    Toast.makeText(context, "Error loading apps", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun loadSelectedApps(mode: SelectAppsModes) {
        _selectedApps.value = if (mode == SelectAppsModes.SELECT_STUDY_APPS) {
            userRepository.getStudyApps()
        } else {
            userRepository.getBlockedPackages()
        }
    }

    fun toggleApp(packageName: String, mode: SelectAppsModes) {
        val updated = _selectedApps.value.toMutableSet().apply {
            if (contains(packageName)) remove(packageName) else add(packageName)
        }
        _selectedApps.value = updated
        saveToPrefs(mode)
    }

    private fun saveToPrefs(mode: SelectAppsModes) {
        if (mode == SelectAppsModes.SELECT_STUDY_APPS) {
            userRepository.updateStudyApps(selectedApps.value)
        } else {
            userRepository.updateBlockedAppsSet(selectedApps.value)
        }
        Log.d("Saving apps list",selectedApps.toString())
        sendRefreshRequest(context, INTENT_ACTION_REFRESH_APP_BLOCKER)
        AppBlockerServiceInfo.appBlockerService?.loadLockedApps()
    }

    fun clearSelectedApps(mode: SelectAppsModes) {
        _selectedApps.value = emptySet()
        saveToPrefs(mode)
    }
    fun addApp(packageName: String, mode: SelectAppsModes) {
        if (!_selectedApps.value.contains(packageName)) {
            val updated = _selectedApps.value.toMutableSet().apply { add(packageName) }
            _selectedApps.value = updated
            saveToPrefs(mode)
        }
    }

    fun removeApp(packageName: String, mode: SelectAppsModes) {
        if (_selectedApps.value.contains(packageName)) {
            val updated = _selectedApps.value.toMutableSet().apply { remove(packageName) }
            _selectedApps.value = updated
            saveToPrefs(mode)
        }
    }

}


@Composable
fun SelectApps(selectAppsModes: SelectAppsModes = SelectAppsModes.ALLOW_ADD_AND_REMOVE, viewModel: SelectAppsViewModel = hiltViewModel(),) {

    val context = LocalContext.current

    val apps by viewModel.apps.collectAsState()
    val selectedApps by viewModel.selectedApps.collectAsState()

    androidx.compose.runtime.LaunchedEffect(selectAppsModes) {
        viewModel.init(selectAppsModes)
    }

    var searchQuery by viewModel.searchQuery
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    // the one special app that was added for ALLOW_ADD or ALLOW_REMOVE mode. is not used for the third mode
    var specialChosenApp by remember { mutableStateOf<String?>(null) }


    val sp = context.getSharedPreferences("distractions", Context.MODE_PRIVATE)

    fun saveToBlocker(){
        sp.edit { putStringSet("distracting_apps", selectedApps.toSet()) }
        sendRefreshRequest(context, INTENT_ACTION_REFRESH_APP_BLOCKER)
        AppBlockerServiceInfo.appBlockerService?.loadLockedApps()
    }

    fun handleAppSelection(packageName: String) {
        when (selectAppsModes) {
            SelectAppsModes.ALLOW_ADD -> {
                if (specialChosenApp == null && !selectedApps.contains(packageName)) {
                    specialChosenApp = packageName
                    viewModel.addApp(packageName, selectAppsModes)
                } else if (specialChosenApp == packageName) {
                    specialChosenApp = null
                    viewModel.removeApp(packageName, selectAppsModes)
                }
            }

            SelectAppsModes.ALLOW_REMOVE -> {
                if (specialChosenApp == null && selectedApps.contains(packageName)) {
                    viewModel.removeApp(packageName, selectAppsModes)
                    specialChosenApp = packageName
                } else if (specialChosenApp == packageName) {
                    viewModel.addApp(packageName, selectAppsModes)
                    specialChosenApp = null
                }
            }

            SelectAppsModes.ALLOW_ADD_AND_REMOVE, SelectAppsModes.SELECT_STUDY_APPS -> {
                if (selectedApps.contains(packageName)) {
                    viewModel.removeApp(packageName, selectAppsModes)
                } else {
                    viewModel.addApp(packageName, selectAppsModes)
                }
            }
        }
        saveToBlocker()

    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(80.dp))

        Text(
            text = if (selectAppsModes == SelectAppsModes.SELECT_STUDY_APPS) "Select Study Apps" else "Select Distractions",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = if (selectAppsModes == SelectAppsModes.SELECT_STUDY_APPS) "These apps will earn you distraction time (10:1 ratio)" else "These might be social media or games..",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
        ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it},
                        label = { Text("Search Apps") },
                        placeholder = { Text("Type app name...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )
                }
            items(filteredApps) { (appName, packageName) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            handleAppSelection(packageName)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isSelected = selectedApps.contains(packageName)

                    val isEnabled = when(selectAppsModes) {
                        SelectAppsModes.ALLOW_ADD -> {
                            (specialChosenApp == null && !isSelected) || specialChosenApp == packageName
                        }
                        SelectAppsModes.ALLOW_REMOVE -> {
                            (specialChosenApp == null && isSelected) || specialChosenApp == packageName
                        }
                        SelectAppsModes.ALLOW_ADD_AND_REMOVE, SelectAppsModes.SELECT_STUDY_APPS -> {
                            true
                        }
                    }

                    Checkbox(
                        checked = isSelected,
                        enabled = isEnabled,
                        onCheckedChange = { _ ->
                            handleAppSelection(packageName)
                        }
                    )
                    Text(
                        text = appName,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
