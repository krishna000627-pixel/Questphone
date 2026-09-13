package neth.iecal.questphone.backed.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import java.net.HttpURLConnection
import java.net.URL

/**
 * Habitica-style sync: no polling loop, no timer. We just hook the moments
 * a person actually "returns" to the launcher — screen unlock and screen-on —
 * and ask the server once "did anything change since my last sync". If yes,
 * pull. If no, nothing happens and no extra battery/network is spent.
 *
 * Cost: effectively zero while idle. The OS itself wakes this receiver on
 * SCREEN_ON / USER_PRESENT; there is no background loop holding the radio
 * or CPU awake in between.
 */
class SyncOnUnlockReceiver(
    private val userRepository: UserRepository,
    private val questRepository: QuestRepository
) : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Avoid hammering the server if SCREEN_ON and USER_PRESENT both fire
    // within the same unlock (very common back-to-back).
    @Volatile private var lastCheckAt = 0L
    private val minGapMs = 2000L

    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        if (now - lastCheckAt < minGapMs) return
        lastCheckAt = now

        val appCtx = context.applicationContext
        if (!RenderSyncPrefs.isEnabled(appCtx)) return

        scope.launch {
            checkAndPull(appCtx)
        }
    }

    private suspend fun checkAndPull(ctx: Context) {
        try {
            val url = RenderSyncPrefs.getServerUrl(ctx)
            val token = RenderSyncPrefs.getSyncToken(ctx)
            val lastSync = RenderSyncPrefs.getLastSyncAt(ctx)
            if (url.isBlank() || token.isBlank()) return

            val conn = (URL("$url/sync/check?clientUpdatedAt=$lastSync")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("x-sync-token", token)
                setRequestProperty("x-device-id", RenderSyncManager.getDeviceId(ctx))
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()

            if (code == 200) {
                val hasUpdate = org.json.JSONObject(body).optBoolean("hasUpdate", false)
                if (hasUpdate) {
                    RenderSyncManager.pull(ctx, userRepository, questRepository)
                }
            }
        } catch (_: Exception) {
            // Silent by design — same behavior as the existing onResume check.
        }
    }

    companion object {
        fun register(
            context: Context,
            userRepository: UserRepository,
            questRepository: QuestRepository
        ): SyncOnUnlockReceiver {
            val receiver = SyncOnUnlockReceiver(userRepository, questRepository)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT) // device unlocked
                addAction(Intent.ACTION_SCREEN_ON)     // screen woke (covers no-lockscreen setups)
            }
            context.applicationContext.registerReceiver(receiver, filter)
            return receiver
        }
    }
}
