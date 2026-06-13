package neth.iecal.questphone.backed.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val changelog: String
)

object AppUpdater {

    private const val PREFS = "app_updater"
    private const val KEY_SKIP = "skipped_version"

    // Called on launch — checks Render server for latest version
    suspend fun checkForUpdate(ctx: Context, serverUrl: String, token: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = serverUrl.trimEnd('/') + "/latest-version"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("x-sync-token", token)
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode != 200) return@withContext null
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                conn.disconnect()

                val json = JSONObject(body)
                val remoteCode = json.optInt("versionCode", 0)
                val skipped = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_SKIP, 0)

                val currentCode = ctx.packageManager
                    .getPackageInfo(ctx.packageName, 0).versionCode

                if (remoteCode > currentCode && remoteCode != skipped) {
                    UpdateInfo(
                        versionCode = remoteCode,
                        versionName = json.optString("versionName", ""),
                        downloadUrl = json.optString("downloadUrl", ""),
                        changelog = json.optString("changelog", "")
                    )
                } else null
            } catch (_: Exception) { null }
        }

    // Download APK into cache and trigger install
    suspend fun downloadAndInstall(
        ctx: Context,
        url: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val apkDir = File(ctx.cacheDir, "apks").also { it.mkdirs() }
        val apkFile = File(apkDir, "questphone_update.apk")

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 30000
        }
        val total = conn.contentLength.toLong()
        conn.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                var bytes: Int
                while (input.read(buf).also { bytes = it } != -1) {
                    output.write(buf, 0, bytes)
                    downloaded += bytes
                    if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                }
            }
        }
        conn.disconnect()

        // Trigger install via FileProvider
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun skipVersion(ctx: Context, versionCode: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SKIP, versionCode).apply()
    }
}
