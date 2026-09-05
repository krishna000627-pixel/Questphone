package neth.iecal.questphone.core.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.backed.isOnline
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import neth.iecal.questphone.core.sync.SyncSanitizer
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "github_sync"
private const val KEY_TOKEN  = "gh_token"
private const val KEY_REPO   = "gh_repo"     // format: "owner/repo"
private const val KEY_LAST   = "gh_last_sync"
private const val KEY_PENDING= "gh_pending"
private const val TAG = "GitHubSync"
private const val SYNC_FILE  = "questphone_data.json"
private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

@Singleton
class GitHubSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _syncState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncState = _syncState.asStateFlow()

    private val _lastSync = MutableStateFlow(prefs.getLong(KEY_LAST, 0L))
    val lastSync = _lastSync.asStateFlow()

    fun getToken() = prefs.getString(KEY_TOKEN, "") ?: ""
    fun getRepo()  = prefs.getString(KEY_REPO, "") ?: ""
    fun isConfigured() = getToken().isNotBlank() && getRepo().isNotBlank()

    fun configure(token: String, repo: String) {
        prefs.edit {
            putString(KEY_TOKEN, token.trim())
            putString(KEY_REPO, repo.trim())
        }
    }

    /** Call this after any meaningful change (quest completed, task done, etc.) */
    fun scheduleSync() {
        prefs.edit { putBoolean(KEY_PENDING, true) }
        if (run { try { val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager; val n = cm.activeNetwork ?: return@run false; cm.getNetworkCapabilities(n)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true } catch (_: Exception) { false } }) {
            CoroutineScope(Dispatchers.IO).launch { push() }
        }
        // If offline, push() will be called next time flushPendingIfOnline() is called
    }

    /** Call on app foreground / network reconnect */
    fun flushPendingIfOnline() {
        if (prefs.getBoolean(KEY_PENDING, false) && context.isOnline()) {
            CoroutineScope(Dispatchers.IO).launch { push() }
        }
    }

    /** Push current UserInfo to GitHub */
    suspend fun push(): Result<Unit> {
        if (!isConfigured()) return Result.failure(Exception("Not configured"))
        _syncState.value = SyncStatus.Syncing("Pushing data…")
        return try {
            val userInfoJson = nethical.questphone.data.json.encodeToString(SyncSanitizer.sanitizeForSync(userRepository.userInfo))
            val encoded = Base64.encodeToString(userInfoJson.toByteArray(), Base64.NO_WRAP)
            val sha = getFileSha()

            val bodyObj = JSONObject().apply {
                put("message", "sync: ${System.currentTimeMillis()}")
                put("content", encoded)
                if (sha != null) put("sha", sha)
            }
            val req = Request.Builder()
                .url("https://api.github.com/repos/${getRepo()}/contents/$SYNC_FILE")
                .put(bodyObj.toString().toRequestBody("application/json".toMediaType()))
                .header("Authorization", "token ${getToken()}")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val resp = http.newCall(req).execute()
            if (resp.isSuccessful) {
                val now = System.currentTimeMillis()
                prefs.edit { putLong(KEY_LAST, now); putBoolean(KEY_PENDING, false) }
                _lastSync.value = now
                _syncState.value = SyncStatus.Success("Synced ✓")
                Log.d(TAG, "Push success")
                Result.success(Unit)
            } else {
                val err = "Push failed: ${resp.code}"
                _syncState.value = SyncStatus.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            _syncState.value = SyncStatus.Error(e.message ?: "Network error")
            prefs.edit { putBoolean(KEY_PENDING, true) }
            Result.failure(e)
        }
    }

    /** Pull from GitHub and restore UserInfo (respects 30-day limit) */
    suspend fun pull(): Result<Unit> {
        if (!isConfigured()) return Result.failure(Exception("Not configured"))
        _syncState.value = SyncStatus.Syncing("Pulling data…")
        return try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${getRepo()}/contents/$SYNC_FILE")
                .get()
                .header("Authorization", "token ${getToken()}")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) {
                val err = "Pull failed: ${resp.code}"
                _syncState.value = SyncStatus.Error(err)
                return Result.failure(Exception(err))
            }

            val body = resp.body?.string() ?: throw Exception("Empty response")
            val encoded = JSONObject(body).getString("content").replace("\\n","").trim()
            val decoded = Base64.decode(encoded, Base64.NO_WRAP).toString(Charsets.UTF_8)
            val restored = nethical.questphone.data.json.decodeFromString<nethical.questphone.data.UserInfo>(decoded)

            // 30-day check: only restore if last_updated is within 30 days
            val ageMs = System.currentTimeMillis() - restored.last_updated
            if (ageMs > THIRTY_DAYS_MS) {
                _syncState.value = SyncStatus.Error("Remote data is older than 30 days")
                return Result.failure(Exception("Data expired"))
            }

            // Validate and strip sensitive fields from pulled payload
            val safe = SyncSanitizer.validateIncoming(restored, userRepository.userInfo)
                ?: run {
                    _syncState.value = SyncStatus.Error("Remote data failed validation — not applied")
                    return Result.failure(Exception("Validation failed"))
                }
            val safeWithLocalFields = safe.copy(
                adminLockEnabled          = userRepository.userInfo.adminLockEnabled,
                notificationBlockEnabled  = userRepository.userInfo.notificationBlockEnabled,
                lockdownEscalationEnabled = userRepository.userInfo.lockdownEscalationEnabled,
                panicButtonCooldownMs     = userRepository.userInfo.panicButtonCooldownMs,
                missedQuestDays           = userRepository.userInfo.missedQuestDays,
                lastBossBattleWeek        = userRepository.userInfo.lastBossBattleWeek,
                bossDefeatedCount         = userRepository.userInfo.bossDefeatedCount,
                rivalStreak               = userRepository.userInfo.rivalStreak,
                rivalLevel                = userRepository.userInfo.rivalLevel,
                activeQuestChainIds       = userRepository.userInfo.activeQuestChainIds,
                appRenames                = userRepository.userInfo.appRenames
            )
            userRepository.restoreUserInfo(safeWithLocalFields)
            val now = System.currentTimeMillis()
            prefs.edit { putLong(KEY_LAST, now) }
            _lastSync.value = now
            _syncState.value = SyncStatus.Success("Restored from backup ✓")
            Log.d(TAG, "Pull success")
            Result.success(Unit)
        } catch (e: Exception) {
            _syncState.value = SyncStatus.Error(e.message ?: "Network error")
            Result.failure(e)
        }
    }

    private fun getFileSha(): String? {
        return try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${getRepo()}/contents/$SYNC_FILE")
                .get()
                .header("Authorization", "token ${getToken()}")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            val resp = http.newCall(req).execute()
            if (resp.isSuccessful) {
                JSONObject(resp.body?.string() ?: "{}").optString("sha", null.toString())
                    .takeIf { it != "null" }
            } else null
        } catch (_: Exception) { null }
    }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        data class Syncing(val msg: String) : SyncStatus()
        data class Success(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }
}


