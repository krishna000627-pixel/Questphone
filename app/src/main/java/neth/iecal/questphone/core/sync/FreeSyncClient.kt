package neth.iecal.questphone.core.sync

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nethical.questphone.data.UserInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Syncs UserInfo to a free PHP host.
 * Host: set SERVER_URL to your deployed sync.php URL.
 * Auth: Gmail + password (SHA-256 on server).
 * Auto-deletes server data after 7 days.
 */
object FreeSyncClient {

    private const val PREF = "free_sync"
    private const val KEY_EMAIL    = "sync_email"
    private const val KEY_PASSWORD = "sync_password"
    private const val KEY_SERVER   = "sync_server"
    private const val KEY_ENABLED  = "sync_enabled"
    private const val KEY_LAST     = "sync_last_time"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // -- Config --------------------------------------------------------
    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun getSyncEmail(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_EMAIL, "") ?: ""

    fun getServer(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_SERVER, "") ?: ""

    fun saveConfig(ctx: Context, email: String, password: String, server: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit {
            putString(KEY_EMAIL, email.trim().lowercase())
            putString(KEY_PASSWORD, password)
            putString(KEY_SERVER, server.trimEnd('/'))
            putBoolean(KEY_ENABLED, email.isNotBlank() && password.isNotBlank() && server.isNotBlank())
        }
    }

    fun disableSync(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, false) }
    }

    fun getLastSyncTime(ctx: Context): Long =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    // -- Push ---------------------------------------------------------
    suspend fun push(ctx: Context, userInfo: UserInfo): SyncResult = withContext(Dispatchers.IO) {
        if (!isEnabled(ctx)) return@withContext SyncResult.Disabled
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val pass  = prefs.getString(KEY_PASSWORD, "") ?: ""
        val server = prefs.getString(KEY_SERVER, "") ?: ""
        if (email.isBlank() || pass.isBlank() || server.isBlank())
            return@withContext SyncResult.Error("Sync not configured")

        return@withContext try {
            val encoded = json.encodeToString(userInfo)
            val body = JSONObject().apply {
                put("email", email)
                put("password", pass)
                put("data", encoded)
                put("device", android.os.Build.MODEL)
            }.toString()

            val req = Request.Builder()
                .url("$server/sync.php?action=push")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val respJson = JSONObject(resp.body?.string() ?: "{}")

            if (respJson.optBoolean("ok")) {
                prefs.edit { putLong(KEY_LAST, System.currentTimeMillis()) }
                SyncResult.Success("Synced at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
            } else {
                SyncResult.Error(respJson.optString("message", "Push failed"))
            }
        } catch (e: Exception) {
            Log.e("FreeSync", "Push error", e)
            SyncResult.Error("Network error: ${e.message}")
        }
    }

    // -- Pull ---------------------------------------------------------
    suspend fun pull(ctx: Context): Pair<SyncResult, UserInfo?> = withContext(Dispatchers.IO) {
        if (!isEnabled(ctx)) return@withContext Pair(SyncResult.Disabled, null)
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val pass  = prefs.getString(KEY_PASSWORD, "") ?: ""
        val server = prefs.getString(KEY_SERVER, "") ?: ""

        return@withContext try {
            val body = JSONObject().apply {
                put("email", email); put("password", pass)
            }.toString()
            val req = Request.Builder()
                .url("$server/sync.php?action=pull")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            val respJson = JSONObject(resp.body?.string() ?: "{}")

            if (respJson.optBoolean("ok")) {
                val data = respJson.optString("data")
                val parsed = json.decodeFromString<UserInfo>(data)
                prefs.edit { putLong(KEY_LAST, System.currentTimeMillis()) }
                Pair(SyncResult.Success("Pulled successfully"), parsed)
            } else {
                Pair(SyncResult.Error(respJson.optString("message", "Pull failed")), null)
            }
        } catch (e: Exception) {
            Log.e("FreeSync", "Pull error", e)
            Pair(SyncResult.Error("Network error: ${e.message}"), null)
        }
    }

    // -- Delete --------------------------------------------------------
    suspend fun deleteRemote(ctx: Context): SyncResult = withContext(Dispatchers.IO) {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val pass  = prefs.getString(KEY_PASSWORD, "") ?: ""
        val server = prefs.getString(KEY_SERVER, "") ?: ""
        try {
            val body = JSONObject().apply { put("email", email); put("password", pass) }.toString()
            val req = Request.Builder().url("$server/sync.php?action=delete")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val resp = client.newCall(req).execute()
            val r = JSONObject(resp.body?.string() ?: "{}")
            if (r.optBoolean("ok")) SyncResult.Success("Account data deleted from server")
            else SyncResult.Error(r.optString("message"))
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Error")
        }
    }
}

sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
    object Disabled : SyncResult()
    object Syncing : SyncResult()
}
