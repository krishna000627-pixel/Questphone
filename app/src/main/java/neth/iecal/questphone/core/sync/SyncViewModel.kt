package neth.iecal.questphone.core.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.data.CommonQuestInfo
import nethical.questphone.data.UserInfo
import nethical.questphone.data.json
import javax.inject.Inject

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application,
    private val userRepository: UserRepository,
    private val questRepository: QuestRepository
) : AndroidViewModel(application), SyncDataProvider {

    val server = WifiSyncServer(application, this)
    val client = WifiSyncClient()

    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _deviceIp = MutableStateFlow("---.---.---.---")
    val deviceIp: StateFlow<String> = _deviceIp

    // latest quests cache for the server thread (non-suspend context)
    private var cachedQuestsJson: String = "[]"

    fun startServer() {
        viewModelScope.launch {
            cachedQuestsJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(CommonQuestInfo.serializer()),
                questRepository.getAllQuestsAsList()
            )
            server.start()
            _serverRunning.value = true
            _deviceIp.value = server.getDeviceIp()
        }
    }

    fun stopServer() {
        server.stop()
        _serverRunning.value = false
    }

    fun syncFrom(targetIp: String) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val userJson = client.getUserFrom(targetIp)
                if (userJson != null) receiveUserJson(userJson)

                val questsJson = client.getQuestsFrom(targetIp)
                if (questsJson != null) receiveQuestsJson(questsJson)

                _syncState.value = SyncState.Success("Synced from $targetIp ✓")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Sync failed: ${e.message}")
            }
        }
    }

    fun pushTo(targetIp: String) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val questsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(CommonQuestInfo.serializer()),
                    questRepository.getAllQuestsAsList()
                )
                val userOk = client.pushUserTo(targetIp, getUserJson())
                val questsOk = client.pushQuestsTo(targetIp, questsJson)
                _syncState.value = if (userOk && questsOk)
                    SyncState.Success("Pushed to $targetIp ✓")
                else
                    SyncState.Error("Push partially failed — check connection")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Push failed: ${e.message}")
            }
        }
    }

    fun resetState() { _syncState.value = SyncState.Idle }

    // ── SyncDataProvider (called from server thread, must be non-suspend) ──
    override fun getUserJson(): String = json.encodeToString(userRepository.userInfo)
    override fun getQuestsJson(): String = cachedQuestsJson

    override fun receiveUserJson(jsonStr: String) {
        try {
            val incoming = json.decodeFromString<UserInfo>(jsonStr)
            if (incoming.last_updated >= userRepository.userInfo.last_updated) {
                userRepository.userInfo = incoming
                userRepository.saveUserInfo(false)
            }
        } catch (_: Exception) {}
    }

    override fun receiveQuestsJson(jsonStr: String) {
        viewModelScope.launch {
            try {
                val quests = json.decodeFromString<List<CommonQuestInfo>>(jsonStr)
                questRepository.upsertAll(quests)
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        server.stop()
        super.onCleared()
    }
}
