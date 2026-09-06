package neth.iecal.questphone.core.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Pass the secret shown on the SERVER's screen when calling any method.
 * Never use a hardcoded secret.
 */
class WifiSyncClient {

    suspend fun getUserFrom(ip: String, secret: String): String? = withContext(Dispatchers.IO) {
        sendCommand(ip, "GET_USER placeholder $secret")
    }

    suspend fun getQuestsFrom(ip: String, secret: String): String? = withContext(Dispatchers.IO) {
        sendCommand(ip, "GET_QUESTS placeholder $secret")
    }

    suspend fun pushUserTo(ip: String, secret: String, userJson: String): Boolean = withContext(Dispatchers.IO) {
        val sanitized = userJson.replace("\n", "").replace("\r", "")
        val response = sendCommand(ip, "PUSH_USER $sanitized $secret")
        response == "OK"
    }

    suspend fun pushQuestsTo(ip: String, secret: String, questsJson: String): Boolean = withContext(Dispatchers.IO) {
        val sanitized = questsJson.replace("\n", "").replace("\r", "")
        val response = sendCommand(ip, "PUSH_QUESTS $sanitized $secret")
        response == "OK"
    }

    private fun sendCommand(ip: String, command: String): String? {
        return try {
            Socket(ip, SYNC_PORT).use { socket ->
                socket.soTimeout = 5000
                val writer = PrintWriter(socket.outputStream, true)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                writer.println(command)
                val status = reader.readLine()
                if (status == "OK") reader.readLine() else null
            }
        } catch (e: Exception) {
            Log.e("WifiSyncClient", "Failed to connect to $ip", e)
            null
        }
    }
}
