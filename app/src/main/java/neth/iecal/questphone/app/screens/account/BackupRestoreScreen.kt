package neth.iecal.questphone.app.screens.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import neth.iecal.questphone.R
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.data.UserInfo
import nethical.questphone.data.json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@Serializable
data class QuestPhoneBackup(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val userInfo: UserInfo,
    val quests: List<CommonQuestInfo>
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val userRepository: UserRepository,
    val questRepository: QuestRepository
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isWorking.value = true
            try {
                val quests = questRepository.getAllQuests().first()
                val backup = QuestPhoneBackup(
                    userInfo = userRepository.userInfo,
                    quests = quests
                )
                val jsonStr = json.encodeToString(backup)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                _status.value = "✅ Backup saved successfully."
            } catch (e: Exception) {
                _status.value = "❌ Export failed: ${e.message}"
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isWorking.value = true
            try {
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception("Cannot read file")
                val backup = json.decodeFromString<QuestPhoneBackup>(raw)

                // Restore quests (merge: upsert all, existing ids are overwritten)
                questRepository.upsertAll(backup.quests)

                // Restore user info (keep current API key to avoid lock-out)
                val restored = backup.userInfo.copy(
                    needsSync = true,
                    isAnonymous = userRepository.userInfo.isAnonymous
                )
                userRepository.userInfo = restored
                userRepository.saveUserInfo(isSetLastUpdated = false)

                val count = backup.quests.size
                _status.value = "✅ Restored $count quests and user profile."
            } catch (e: Exception) {
                _status.value = "❌ Restore failed: ${e.message}"
            } finally {
                _isWorking.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    navController: NavController,
    vm: BackupRestoreViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val isWorking by vm.isWorking.collectAsState()

    val dateTag = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    val defaultFileName = "questphone_backup_$dateTag.json"

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importBackup(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Back up all your quests, profile, economy, and settings to a JSON file. Restore from any previous backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Export card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Export Backup", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)
                    Text("Saves quests + profile to a .json file in your chosen location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { exportLauncher.launch(defaultFileName) },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(painterResource(R.drawable.baseline_info_24), null,
                            Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export to File")
                    }
                }
            }

            // Import card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Restore Backup", fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall)
                    Text("Merges quests from backup. Existing quests with the same ID are overwritten. Your API key is preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(painterResource(R.drawable.baseline_info_24), null,
                            Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restore from File")
                    }
                }
            }

            if (isWorking) {
                Row(Modifier.fillMaxWidth(), Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Working…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            status?.let {
                val isError = it.startsWith("❌")
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it, Modifier.padding(14.dp),
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
