package neth.iecal.questphone.core.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nethical.questphone.data.json
import neth.iecal.questphone.core.sync.SyncSanitizer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

const val SYNC_PORT = 45678

/**
 * Per-device session secret generated on server start.
 * Never hardcoded — user must manually share the secret shown in the WiFi Sync screen
 * with the connecting device. Displayed in the UI as a 6-digit code.
 */
object WifiSyncSecret {
    @Volatile private var _secret: String = ""

    fun generate(): String {
        _secret = (100000..999999).random().toString()
        return _secret
    }

    fun get(): String = _secret
    fun isValid(input: String): Boolean = input.isNotBlank() && input == _secret
}

/**
 * Lightweight HTTP-like sync server that runs on the phone over WiFi.
 * Another device on the same WiFi can push/pull quest & user data.
 *
 * Endpoints (plain TCP JSON protocol):
 *   GET_USER   → returns UserInfo JSON
 *   GET_QUESTS → returns list of quests JSON
 *   PUSH_USER  <json> → receives and saves UserInfo
 *   PUSH_QUESTS <json> → receives and saves quests
 */
class WifiSyncServer(
    private val context: Context,
    private val syncDataProvider: SyncDataProvider
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            try {
                WifiSyncSecret.generate()
                serverSocket = ServerSocket(SYNC_PORT)
                Log.d("WifiSync", "Server started on port $SYNC_PORT")
                while (true) {
                    val client = try { serverSocket?.accept() ?: break } catch (e: Exception) { break }
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                Log.e("WifiSync", "Server error — likely no network: ${e.message}")
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        serverSocket?.close()
        serverSocket = null
    }

    fun getDeviceIp(): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip == 0) "Not connected to WiFi"
            else "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        } catch (e: Exception) {
            "Unable to get IP"
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use {
                val reader = BufferedReader(InputStreamReader(it.inputStream))
                val writer = PrintWriter(it.outputStream, true)

                val firstLine = reader.readLine()?.trim() ?: return
                val parts = firstLine.split(" ", limit = 3)
                if (parts.size < 2) return

                // Auth check against session-generated secret (never hardcoded)
                val secret = parts.getOrNull(2) ?: ""
                if (!WifiSyncSecret.isValid(secret)) {
                    writer.println("ERROR:UNAUTHORIZED")
                    return
                }

                when (parts[0]) {
                    "GET_USER" -> {
                        // getUserJson() already calls SyncSanitizer.sanitizeForSync()
                        val userJson = syncDataProvider.getUserJson()
                        writer.println("OK")
                        writer.println(userJson)
                    }
                    "GET_QUESTS" -> {
                        val questsJson = syncDataProvider.getQuestsJson()
                        writer.println("OK")
                        writer.println(questsJson)
                    }
                    "PUSH_USER" -> {
                        // receiveUserJson() calls SyncSanitizer.validateIncoming() before saving
                        val json = parts.getOrNull(1) ?: return
                        syncDataProvider.receiveUserJson(json)
                        writer.println("OK")
                    }
                    "PUSH_QUESTS" -> {
                        val json = parts.getOrNull(1) ?: return
                        syncDataProvider.receiveQuestsJson(json)
                        writer.println("OK")
                    }
                    else -> writer.println("ERROR:UNKNOWN_COMMAND")
                }
            }
        } catch (e: Exception) {
            Log.e("WifiSync", "Client handling error", e)
        }
    }
}

interface SyncDataProvider {
    fun getUserJson(): String
    fun getQuestsJson(): String
    fun receiveUserJson(json: String)
    fun receiveQuestsJson(json: String)
}
