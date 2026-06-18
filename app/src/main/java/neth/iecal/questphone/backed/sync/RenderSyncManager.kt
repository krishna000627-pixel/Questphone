package neth.iecal.questphone.backed.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import neth.iecal.questphone.app.screens.account.QuestPhoneBackup
import neth.iecal.questphone.app.screens.mylife.MyLifeStorage
import neth.iecal.questphone.app.screens.people.PeopleDatabase
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.data.json
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RenderSyncPrefs {
    private const val PREFS = "render_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SYNC = "last_sync_at"
    private const val KEY_OWN_ID = "own_id"      // permanent device-generated ID
    private const val KEY_LINKED_ID = "linked_id" // set when linked to another device

    // Hardcoded server
    const val SERVER_URL = "https://questphone-sync.onrender.com"
    const val SYNC_TOKEN = "krishna_qp_2026"

    fun getServerUrl(ctx: Context) = SERVER_URL
    fun getSyncToken(ctx: Context) = SYNC_TOKEN

    // Permanent own ID — generated once, never changes
    fun getOwnId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_OWN_ID, null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
            prefs.edit().putString(KEY_OWN_ID, id).apply()
        }
        return id
    }

    // Active sync slot — own ID unless linked to another device
    fun getUserId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val linked = prefs.getString(KEY_LINKED_ID, null)
        return if (!linked.isNullOrBlank()) linked else getOwnId(ctx)
    }

    fun isLinked(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val linked = prefs.getString(KEY_LINKED_ID, null)
        return !linked.isNullOrBlank() && linked != getOwnId(ctx)
    }

    fun linkDevice(ctx: Context, code: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LINKED_ID, code.trim().uppercase()).apply()
    }

    fun unlink(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LINKED_ID).apply()
    }

    fun isEnabled(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getLastSyncAt(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncAt(ctx: Context, time: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SYNC, time).apply()
    }

    // Auto-init on first launch
    fun initIfNeeded(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ENABLED)) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        }
        getOwnId(ctx) // ensure ID is generated
    }

    // Legacy compat
    fun save(ctx: Context, url: String, token: String, enabled: Boolean) {
        setEnabled(ctx, enabled)
    }
}

object RenderSyncManager {

    // Fixed ID — all devices share the same backup slot on the server
    fun getDeviceId(ctx: Context): String = RenderSyncPrefs.getUserId(ctx)

    sealed class SyncResult {
        object Success : SyncResult()
        data class Error(val message: String) : SyncResult()
        object Disabled : SyncResult()
    }

    // Called after every data change — pushes current state to server
    suspend fun autoPush(
        ctx: Context,
        userRepository: UserRepository,
        questRepository: QuestRepository
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!RenderSyncPrefs.isEnabled(ctx)) return@withContext SyncResult.Disabled
        val url = RenderSyncPrefs.getServerUrl(ctx)
        val token = RenderSyncPrefs.getSyncToken(ctx)
        if (url.isBlank() || token.isBlank()) return@withContext SyncResult.Error("Sync not configured")

        try {
            val quests = questRepository.getAllQuestsAsList()
            val backup = QuestPhoneBackup(
                userInfo = userRepository.userInfo,
                quests = quests,
                myLife = MyLifeStorage.load(ctx),
                people = PeopleDatabase.load(ctx)
            )
            val body = json.encodeToString(backup).toByteArray(Charsets.UTF_8)

            val conn = (URL("$url/sync/push").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-sync-token", token)
                setRequestProperty("x-device-id", getDeviceId(ctx))
                connectTimeout = 15000
                readTimeout = 15000
            }
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            conn.disconnect()

            if (code == 200) {
                RenderSyncPrefs.setLastSyncAt(ctx, System.currentTimeMillis())
                SyncResult.Success
            } else {
                SyncResult.Error("Server returned $code")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    // Manual pull — restores data from server
    suspend fun pull(
        ctx: Context,
        userRepository: UserRepository,
        questRepository: QuestRepository
    ): SyncResult = withContext(Dispatchers.IO) {
        val url = RenderSyncPrefs.getServerUrl(ctx)
        val token = RenderSyncPrefs.getSyncToken(ctx)
        if (url.isBlank() || token.isBlank()) return@withContext SyncResult.Error("Sync not configured")

        try {
            val conn = (URL("$url/sync/pull").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("x-sync-token", token)
                setRequestProperty("x-device-id", getDeviceId(ctx))
                connectTimeout = 15000
                readTimeout = 15000
            }
            val code = conn.responseCode
            if (code == 404) return@withContext SyncResult.Error("No backup on server yet")
            if (code != 200) return@withContext SyncResult.Error("Server returned $code")

            val raw = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()

            val backup = json.decodeFromString<QuestPhoneBackup>(raw)
            questRepository.upsertAll(backup.quests)
            val restored = backup.userInfo.copy(
                needsSync = true,
                isAnonymous = userRepository.userInfo.isAnonymous
            )
            userRepository.userInfo = restored
            userRepository.saveUserInfo(isSetLastUpdated = false)
            MyLifeStorage.save(ctx, backup.myLife)
            PeopleDatabase.save(ctx, backup.people)
            RenderSyncPrefs.setLastSyncAt(ctx, System.currentTimeMillis())
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    // Test connection
    suspend fun testConnection(url: String, token: String): SyncResult = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("${url.trimEnd('/')}/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val code = conn.responseCode
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()
            if (code == 200 && body.contains("QuestPhone Sync")) SyncResult.Success
            else SyncResult.Error("Unexpected response ($code)")
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Cannot reach server")
        }
    }
}
